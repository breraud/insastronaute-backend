package fr.insastronaute.webapi.leaderboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "app.leaderboards.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LeaderboardRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRefreshScheduler.class);

    private final LeaderboardService leaderboardService;

    public LeaderboardRefreshScheduler(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * Publie les classements dus, un par un.
     *
     * <p>Chaque publication passe par le proxy du service, donc dans sa propre transaction : un
     * classement qui echoue est journalise et retente au passage suivant, sans empecher les
     * autres de se publier. Avant, tous partageaient une transaction, et un seul echec les
     * figeait tous.</p>
     */
    @Scheduled(
            initialDelayString = "${app.leaderboards.refresh-initial-delay-ms:60000}",
            fixedDelayString = "${app.leaderboards.refresh-check-delay-ms:60000}"
    )
    public void releaseDueLeaderboards() {
        for (UUID configId : leaderboardService.dueLeaderboardIds()) {
            try {
                leaderboardService.releaseIfDue(configId);
            } catch (RuntimeException exception) {
                log.error("Leaderboard {} could not be released: {}",
                        safeDescription(configId), exception.getMessage(), exception);
            }
        }
    }

    private String safeDescription(UUID configId) {
        try {
            return leaderboardService.describeLeaderboard(configId);
        } catch (RuntimeException ignored) {
            return String.valueOf(configId);
        }
    }
}
