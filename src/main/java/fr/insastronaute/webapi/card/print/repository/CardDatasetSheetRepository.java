package fr.insastronaute.webapi.card.print.repository;

import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardDatasetSheetRepository extends JpaRepository<CardDatasetSheet, UUID> {

    boolean existsBySheetId(String sheetId);

    Optional<CardDatasetSheet> findBySheetId(String sheetId);

    @Query("""
            select sheet.id as id,
                   sheet.sheetId as sheetId,
                   run.id as batchId,
                   sheet.createdAt as createdAt,
                   count(crop.id) as scanCount,
                   case when sheet.pdfObjectKey is null then false else true end as downloadable
            from CardDatasetSheet sheet
            left join sheet.printRun run
            left join CardDatasetCrop crop on crop.sheet = sheet
            group by sheet.id, sheet.sheetId, run.id, sheet.createdAt, sheet.pdfObjectKey
            order by sheet.createdAt desc, sheet.id desc
            """)
    List<CardDatasetSheetSummaryProjection> findAllSummaries(Pageable pageable);

    @EntityGraph(attributePaths = {"codes"})
    @Query("""
            select sheet
            from CardDatasetSheet sheet
            where sheet.sheetId = :sheetId
            """)
    Optional<CardDatasetSheet> findWithCodesBySheetId(@Param("sheetId") String sheetId);

    interface CardDatasetSheetSummaryProjection {
        UUID getId();
        String getSheetId();
        UUID getBatchId();
        Instant getCreatedAt();
        long getScanCount();
        boolean getDownloadable();
    }
}
