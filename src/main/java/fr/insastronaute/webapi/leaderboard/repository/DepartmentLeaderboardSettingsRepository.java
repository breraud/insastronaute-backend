package fr.insastronaute.webapi.leaderboard.repository;

import fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentLeaderboardSettingsRepository extends JpaRepository<DepartmentLeaderboardSettings, UUID> {
}
