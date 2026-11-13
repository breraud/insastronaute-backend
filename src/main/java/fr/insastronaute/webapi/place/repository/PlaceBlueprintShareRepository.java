package fr.insastronaute.webapi.place.repository;

import fr.insastronaute.webapi.place.domain.PlaceBlueprintShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceBlueprintShareRepository
        extends JpaRepository<PlaceBlueprintShare, PlaceBlueprintShare.Key> {

    Optional<PlaceBlueprintShare> findByBlueprintIdAndUserId(UUID blueprintId, UUID userId);

    List<PlaceBlueprintShare> findByBlueprintId(UUID blueprintId);

    boolean existsByBlueprintIdAndUserId(UUID blueprintId, UUID userId);

    void deleteByBlueprintIdAndUserId(UUID blueprintId, UUID userId);
}
