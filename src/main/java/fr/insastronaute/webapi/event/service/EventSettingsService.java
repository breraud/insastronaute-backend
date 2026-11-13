package fr.insastronaute.webapi.event.service;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.event.domain.EventSettings;
import fr.insastronaute.webapi.event.repository.EventSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventSettingsService {

    private final EventSettingsRepository repository;

    public EventSettingsService(EventSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * <p>Absent, le reglage vaut « ouvert ». Une table vide doit laisser les inscriptions
     * fonctionner : la migration en pose la ligne, mais un environnement monte autrement
     * ne doit pas fermer les evenements a tout le monde par accident.</p>
     */
    @Transactional(readOnly = true)
    public boolean staffRegistrationBlocked() {
        return repository.findById(true)
                .map(EventSettings::isStaffRegistrationBlocked)
                .orElse(false);
    }

    @Transactional
    public boolean setStaffRegistrationBlocked(boolean blocked) {
        EventSettings settings = repository.findById(true).orElseGet(EventSettings::new);
        settings.setStaffRegistrationBlocked(blocked);
        settings.setUpdatedAt(TimeTravelContext.instantNow());
        return repository.save(settings).isStaffRegistrationBlocked();
    }
}
