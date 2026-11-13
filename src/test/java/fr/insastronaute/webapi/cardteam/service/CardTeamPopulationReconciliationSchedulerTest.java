package fr.insastronaute.webapi.cardteam.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CardTeamPopulationReconciliationSchedulerTest {

    @Test
    void startupAndPeriodicTicksBothAttemptReconciliation() {
        CardTeamPopulationReconciler reconciler = mock(CardTeamPopulationReconciler.class);
        CardTeamPopulationReconciliationScheduler scheduler =
                new CardTeamPopulationReconciliationScheduler(reconciler);

        scheduler.afterStartup();
        scheduler.periodically();

        verify(reconciler, times(2)).reconcile();
    }
}
