package fr.insastronaute.webapi.leaderboard.repository;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardConfig;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeaderboardSnapshotRepository extends JpaRepository<LeaderboardSnapshot, UUID> {
    Optional<LeaderboardSnapshot> findFirstByConfigOrderByReleasedAtDesc(LeaderboardConfig config);
}
