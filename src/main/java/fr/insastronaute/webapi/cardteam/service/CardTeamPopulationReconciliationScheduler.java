package fr.insastronaute.webapi.cardteam.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CardTeamPopulationReconciliationScheduler {

    private final CardTeamPopulationReconciler reconciler;

    public CardTeamPopulationReconciliationScheduler(CardTeamPopulationReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void afterStartup() {
        reconciler.reconcile();
    }

    @Scheduled(fixedDelayString = "${app.card-teams.population-reconciliation-delay-ms:300000}")
    public void periodically() {
        reconciler.reconcile();
    }
}
