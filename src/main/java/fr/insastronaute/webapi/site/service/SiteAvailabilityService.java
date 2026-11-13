package fr.insastronaute.webapi.site.service;

import fr.insastronaute.webapi.site.domain.SiteAvailability;
import fr.insastronaute.webapi.site.dto.SiteAvailabilityDto;
import fr.insastronaute.webapi.site.dto.SiteAvailabilityUpdateRequest;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Lecture cachee de la disponibilite du site.
 *
 * <p>Le filtre HTTP interroge ce service a chaque requete : une lecture base par
 * requete est exclue. L'entree memoire est rafraichie au-dela de {@link #CACHE_TTL_NANOS}
 * et remplacee immediatement a l'ecriture, pour que l'administrateur qui bascule
 * l'interrupteur voie l'effet sans attendre.</p>
 *
 * <p>La base qui tombe ne doit pas fermer le site par effet de bord : en cas d'echec
 * de lecture, la derniere valeur connue est conservee, et a defaut le site est ouvert.</p>
 */
@Service
public class SiteAvailabilityService {

    static final long CACHE_TTL_NANOS = 5_000_000_000L;

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteAvailabilityService.class);

    public record SiteStatus(boolean playerAccessEnabled, String message) {}

    private record CachedStatus(SiteStatus status, long readAtNanos) {}

    private final SiteAvailabilityStore store;
    private final LongSupplier nanoTime;
    private final AtomicReference<CachedStatus> cache = new AtomicReference<>();

    // Deux constructeurs : sans cette annotation, Spring ne sait pas lequel choisir.
    // Meme raison que le double constructeur de PlaceConfigService.
    @Autowired
    public SiteAvailabilityService(SiteAvailabilityStore store) {
        this(store, System::nanoTime);
    }

    SiteAvailabilityService(SiteAvailabilityStore store, LongSupplier nanoTime) {
        this.store = store;
        this.nanoTime = nanoTime;
    }

    /**
     * Lire, verifier, rafraichir - sans atomicite, et c'est voulu.
     *
     * <p>A l'expiration, plusieurs requetes concurrentes peuvent recharger toutes
     * ensemble : elles lisent la meme ligne et ecrivent la meme valeur, donc le
     * pire cas est quelques lectures de plus une fois toutes les cinq secondes.
     * Serialiser cet acces mettrait un verrou sur le chemin le plus chaud de
     * l'application - /api/session le traverse a chaque appel - pour empecher une
     * duplication sans consequence.</p>
     */
    public SiteStatus currentStatus() {
        CachedStatus cached = cache.get();
        long now = nanoTime.getAsLong();

        if (cached != null && now - cached.readAtNanos() < CACHE_TTL_NANOS) {
            return cached.status();
        }

        try {
            SiteAvailability entity = store.getOrCreate();
            SiteStatus fresh = new SiteStatus(entity.isPlayerAccessEnabled(), entity.getMessage());
            cache.set(new CachedStatus(fresh, now));
            return fresh;
        } catch (RuntimeException exception) {
            LOGGER.warn("Site availability could not be read, keeping the last known value", exception);
            return cached != null ? cached.status() : new SiteStatus(true, null);
        }
    }

    void cacheStatus(SiteStatus status) {
        cache.set(new CachedStatus(status, nanoTime.getAsLong()));
    }

    public SiteAvailabilityDto get() {
        return SiteAvailabilityDto.from(store.getOrCreate());
    }

    public SiteAvailabilityDto update(SiteAvailabilityUpdateRequest request, AppUser actor) {
        String message = request.message() == null || request.message().isBlank()
                ? null
                : request.message().trim();

        SiteAvailability entity = store.save(request.playerAccessEnabled(), message, actor);
        cacheStatus(new SiteStatus(entity.isPlayerAccessEnabled(), entity.getMessage()));

        return SiteAvailabilityDto.from(entity);
    }
}
