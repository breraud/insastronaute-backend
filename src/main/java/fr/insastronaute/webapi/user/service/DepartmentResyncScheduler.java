package fr.insastronaute.webapi.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relit les departements de tout le monde, une fois par nuit.
 *
 * <p>Le filet, pas le moyen principal. Un departement change se corrige a la main depuis
 * la fiche du joueur, tout de suite ; ce balayage rattrape ce que personne n'a remarque —
 * et il tourne a une heure ou un appel Keycloak par compte ne gene personne.</p>
 */
@Component
@ConditionalOnProperty(value = "app.departments.resync.enabled", havingValue = "true", matchIfMissing = true)
public class DepartmentResyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepartmentResyncScheduler.class);

    private final DepartmentResyncService resyncService;

    public DepartmentResyncScheduler(DepartmentResyncService resyncService) {
        this.resyncService = resyncService;
    }

    @Scheduled(cron = "${app.departments.resync-cron}")
    public void sweep() {
        try {
            DepartmentResyncService.SweepReport report = resyncService.resyncAll();
            LOGGER.info("Balayage nocturne des departements : {} lus, {} corriges, {} illisibles.",
                    report.examined(), report.changed(), report.unreadable());
        } catch (RuntimeException exception) {
            // Un balayage qui echoue ne doit pas emporter le planificateur : le suivant
            // repassera demain, et la correction a la main reste disponible entre-temps.
            LOGGER.error("Balayage nocturne des departements interrompu.", exception);
        }
    }
}
