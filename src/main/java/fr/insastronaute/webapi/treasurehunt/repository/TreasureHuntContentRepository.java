package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TreasureHuntContentRepository extends JpaRepository<TreasureHuntContent, UUID> {
    List<TreasureHuntContent> findByCheckpointIdOrderByPositionAsc(UUID checkpointId);
    List<TreasureHuntContent> findByVaultIdOrderByPositionAsc(Boolean vaultId);
    long countByCheckpointId(UUID checkpointId);
    long countByVaultId(Boolean vaultId);
}
