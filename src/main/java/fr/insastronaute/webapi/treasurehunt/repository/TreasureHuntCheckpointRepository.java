package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreasureHuntCheckpointRepository extends JpaRepository<TreasureHuntCheckpoint, UUID> {
    Optional<TreasureHuntCheckpoint> findByCodeHash(byte[] codeHash);
    int countByPathId(UUID pathId);
    List<TreasureHuntCheckpoint> findByPathIdOrderByStepNumberAsc(UUID pathId);
}
