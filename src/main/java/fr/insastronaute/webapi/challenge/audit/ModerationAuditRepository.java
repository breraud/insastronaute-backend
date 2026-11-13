package fr.insastronaute.webapi.challenge.audit;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ModerationAuditRepository extends JpaRepository<ModerationAuditEvent, UUID> {
    List<ModerationAuditEvent> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    /**
     * Marquer ces soumissions comme supprimees, avant qu'elles le soient.
     *
     * <p>La base cascade sans rien estampiller, donc l'estampille se pose ici, juste
     * avant. Les lignes deja marquees sont laissees telles quelles : la premiere date
     * est la bonne.</p>
     *
     * @return le nombre de lignes d'audit conservees
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update ModerationAuditEvent event
               set event.submissionDeletedAt = :deletedAt
             where event.submissionId in :submissionIds
               and event.submissionDeletedAt is null
            """)
    int markSubmissionsDeleted(
            @Param("submissionIds") Collection<UUID> submissionIds,
            @Param("deletedAt") Instant deletedAt);
}
