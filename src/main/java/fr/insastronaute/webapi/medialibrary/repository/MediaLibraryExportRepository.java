package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MediaLibraryExportRepository extends JpaRepository<MediaLibraryExport, UUID> {

    @Query("select e from MediaLibraryExport e join fetch e.createdBy order by e.createdAt desc")
    List<MediaLibraryExport> findAllNewestFirst();

    boolean existsByCreatedBy_Id(UUID userId);
}
