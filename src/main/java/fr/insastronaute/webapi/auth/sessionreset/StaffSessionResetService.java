package fr.insastronaute.webapi.auth.sessionreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Le compteur qui decide quelles sessions de staff ont fait leur temps.
 *
 * <p>Un role est lu une seule fois, a la connexion, et fige dans la session pour huit
 * heures. Reattribuer les roles dans Keycloak n'a donc aucun effet sur les gens deja
 * connectes : ils gardent ceux qu'ils avaient en arrivant.</p>
 *
 * <p>Le depot sait maintenant retrouver les sessions d'un utilisateur precis, mais une
 * reattribution globale du staff ne doit pas enumerer chaque compte et chaque session.
 * Le sens reste donc inverse pour ce cas collectif : un compteur commun est incremente,
 * chaque session porte le numero qu'elle a vu, et celle qui presente un numero perime se
 * retire d'elle-meme a sa requete suivante.</p>
 *
 * <p>Le compteur est relu au plus une fois par seconde et demie : sans ce cache, chaque
 * requete authentifiee couterait un aller-retour Redis supplementaire, sur le chemin le
 * plus frequente de l'application.</p>
 */
@Service
public class StaffSessionResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffSessionResetService.class);
    private static final String KEY = "insastronaute:security:staff-session-epoch";
    private static final long CACHE_TTL_NANOS = Duration.ofMillis(1500).toNanos();

    private final StringRedisTemplate redisTemplate;

    private volatile long cachedEpoch;
    private volatile long cachedAtNanos = Long.MIN_VALUE;

    public StaffSessionResetService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Le numero courant. Une lecture ratee renvoie la derniere valeur connue plutot que
     * zero : repondre zero ferait passer toutes les sessions pour perimees et deconnecterait
     * le staff a chaque hoquet de Redis.
     */
    public long currentEpoch() {
        long now = System.nanoTime();
        if (cachedAtNanos != Long.MIN_VALUE && now - cachedAtNanos < CACHE_TTL_NANOS) {
            return cachedEpoch;
        }
        try {
            String value = redisTemplate.opsForValue().get(KEY);
            cachedEpoch = value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException exception) {
            LOGGER.warn("Lecture du compteur de reinitialisation impossible, on garde {} : {}",
                    cachedEpoch, exception.getMessage());
        }
        cachedAtNanos = now;
        return cachedEpoch;
    }

    /**
     * Perime toutes les sessions de staff ouvertes. Rien n'est ferme a cet instant : chacune
     * s'en apercevra a sa prochaine requete.
     */
    public long reset() {
        Long value = redisTemplate.opsForValue().increment(KEY);
        long epoch = value == null ? 0L : value;
        cachedEpoch = epoch;
        cachedAtNanos = System.nanoTime();
        LOGGER.info("Sessions de staff perimees, compteur porte a {}", epoch);
        return epoch;
    }
}
