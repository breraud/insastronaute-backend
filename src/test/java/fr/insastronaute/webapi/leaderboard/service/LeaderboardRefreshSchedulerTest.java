package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.common.web.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardRefreshSchedulerTest {

    @Mock
    private LeaderboardService leaderboardService;

    @InjectMocks
    private LeaderboardRefreshScheduler scheduler;

    @Test
    void aFailingLeaderboardDoesNotFreezeTheOthers() {
        UUID department = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        when(leaderboardService.dueLeaderboardIds()).thenReturn(List.of(department, game));
        when(leaderboardService.releaseIfDue(department))
                .thenThrow(new ConflictException("Un effectif theorique manque pour un departement participant."));
        when(leaderboardService.describeLeaderboard(department)).thenReturn("DEPARTMENT/CHALLENGE_POINTS");

        assertThatCode(() -> scheduler.releaseDueLeaderboards()).doesNotThrowAnyException();

        verify(leaderboardService).releaseIfDue(game);
    }
}
