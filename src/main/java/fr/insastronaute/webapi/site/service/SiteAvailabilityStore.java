package fr.insastronaute.webapi.site.service;

import fr.insastronaute.webapi.site.domain.SiteAvailability;
import fr.insastronaute.webapi.site.repository.SiteAvailabilityRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le seul point d'acces base pour la disponibilite du site.
 *
 * <p>Il est separe du service porteur du cache parce qu'une methode
 * {@code @Transactional} appelee depuis le meme bean contourne le proxy Spring :
 * la creation de la ligne singleton tournerait alors hors transaction.</p>
 */
@Service
public class SiteAvailabilityStore {

    private final SiteAvailabilityRepository repository;

    public SiteAvailabilityStore(SiteAvailabilityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SiteAvailability getOrCreate() {
        return repository.findById(SiteAvailability.SINGLETON_ID)
                .orElseGet(() -> repository.save(new SiteAvailability()));
    }

    @Transactional
    public SiteAvailability save(boolean playerAccessEnabled, String message, AppUser actor) {
        SiteAvailability entity = getOrCreate();
        entity.setPlayerAccessEnabled(playerAccessEnabled);
        entity.setMessage(message);
        entity.setUpdatedBy(actor);
        return repository.save(entity);
    }
}
