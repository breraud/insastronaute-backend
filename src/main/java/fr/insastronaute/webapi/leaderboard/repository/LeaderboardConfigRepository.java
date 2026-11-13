package fr.insastronaute.webapi.leaderboard.repository;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardConfig;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeaderboardConfigRepository extends JpaRepository<LeaderboardConfig, UUID> {
    Optional<LeaderboardConfig> findByScopeAndMetric(LeaderboardScope scope, LeaderboardMetric metric);
}
