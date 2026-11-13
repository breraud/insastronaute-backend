package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaLibrarySelectionRepository extends JpaRepository<MediaLibrarySelection, MediaSelectionKey> {

    @Query("select sel from MediaLibrarySelection sel join fetch sel.selectedBy where sel.id.submissionId in :submissionIds")
    List<MediaLibrarySelection> findBySubmissionIds(@Param("submissionIds") Collection<UUID> submissionIds);

    @Query("select sel from MediaLibrarySelection sel join fetch sel.selectedBy")
    List<MediaLibrarySelection> findAllWithSelector();

    boolean existsBySelectedBy_Id(UUID userId);

    /**
     * Retenir sans jamais ecraser : la premiere personne qui a retenu le media reste
     * celle que l'ecran nomme, et deux insertions simultanees ne levent rien.
     */
    @Modifying
    @Query(value = """
            insert into media_library_selection (submission_id, media_index, selected_by, selected_at)
            values (:submissionId, :mediaIndex, :selectedBy, :selectedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("submissionId") UUID submissionId,
                       @Param("mediaIndex") int mediaIndex,
                       @Param("selectedBy") UUID selectedBy,
                       @Param("selectedAt") Instant selectedAt);

    @Modifying
    @Query("delete from MediaLibrarySelection sel where sel.id.submissionId = :submissionId and sel.id.mediaIndex = :mediaIndex")
    int deleteOne(@Param("submissionId") UUID submissionId, @Param("mediaIndex") int mediaIndex);
}
