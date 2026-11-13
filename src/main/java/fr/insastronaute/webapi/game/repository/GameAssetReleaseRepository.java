package fr.insastronaute.webapi.game.repository;

import fr.insastronaute.webapi.game.domain.GameAssetRelease;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameAssetReleaseRepository extends JpaRepository<GameAssetRelease, Long> {
}
