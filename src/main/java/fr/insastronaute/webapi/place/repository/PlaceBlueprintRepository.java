package fr.insastronaute.webapi.place.repository;

import fr.insastronaute.webapi.place.domain.PlaceBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlaceBlueprintRepository extends JpaRepository<PlaceBlueprint, UUID> {

    List<PlaceBlueprint> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    /**
     * Les plans qu'on a recus, le plus recemment partage d'abord.
     *
     * <p>{@code join fetch} sur le proprietaire : la liste affiche « partage par »
     * pour chacun, et sans lui chaque ligne coute sa requete.</p>
     */
    @Query("""
            select blueprint
            from PlaceBlueprintShare share
            join share.blueprint blueprint
            join fetch blueprint.owner
            where share.user.id = :userId
            order by share.createdAt desc
            """)
    List<PlaceBlueprint> findSharedWith(@Param("userId") UUID userId);
}
