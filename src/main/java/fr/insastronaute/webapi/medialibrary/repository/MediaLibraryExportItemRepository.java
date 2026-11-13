package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.medialibrary.domain.MediaExportItemKey;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MediaLibraryExportItemRepository extends JpaRepository<MediaLibraryExportItem, MediaExportItemKey> {

    @Query("""
            select i from MediaLibraryExportItem i join fetch i.selectedBy
            where i.id.exportId = :exportId and i.partNumber = :partNumber
            order by i.id.itemPosition
            """)
    List<MediaLibraryExportItem> findPart(@Param("exportId") UUID exportId, @Param("partNumber") int partNumber);

    @Query("""
            select new fr.insastronaute.webapi.medialibrary.repository.MediaExportPartSummary(
                i.partNumber, count(i), sum(i.sizeBytes))
            from MediaLibraryExportItem i
            where i.id.exportId = :exportId
            group by i.partNumber
            order by i.partNumber
            """)
    List<MediaExportPartSummary> summarizeParts(@Param("exportId") UUID exportId);

    boolean existsBySelectedBy_Id(UUID userId);

    boolean existsByIdExportIdAndPartNumber(UUID exportId, int partNumber);
}
