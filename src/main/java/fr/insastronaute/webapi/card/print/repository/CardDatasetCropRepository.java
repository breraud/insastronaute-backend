package fr.insastronaute.webapi.card.print.repository;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardDatasetCropRepository extends JpaRepository<CardDatasetCrop, UUID> {

    @EntityGraph(attributePaths = {"sheet", "strip"})
    List<CardDatasetCrop> findBySheetOrderByCreatedAtAsc(CardDatasetSheet sheet);

    @EntityGraph(attributePaths = {"sheet", "strip"})
    @Query("""
            select crop
            from CardDatasetCrop crop
            where crop.id = :id
            """)
    Optional<CardDatasetCrop> findWithSheetById(@Param("id") UUID id);

    /**
     * Toutes les decoupes, dans un ordre stable, pour l'export.
     *
     * <p>L'ordre ne decide de rien -- la separation entrainement/validation se deduit de
     * l'identite de la bande, pas du rang -- mais il rend deux exports du meme etat
     * identiques octet pour octet, donc comparables.
     */
    @EntityGraph(attributePaths = {"sheet", "strip"})
    @Query("""
            select crop
            from CardDatasetCrop crop
            order by crop.sheet.sheetId asc, crop.strip.position asc, crop.createdAt asc, crop.id asc
            """)
    List<CardDatasetCrop> findAllForExport();
}
