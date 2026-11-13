package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les soumissions vues par la Mediatheque. Toutes passent par
 * {@link MediaLibraryEligibility#JPQL}.
 */
public interface MediaLibrarySubmissionRepository extends Repository<ChallengeSubmission, UUID> {

    String FROM = " from ChallengeSubmission s join s.challenge c join s.user u where ";

    // Le type se lit sur l'extension des cles : c'est aussi ce que fait
    // ProofStorageService.contentTypeForProofKey. Une cle est proofs/<uuid>/<uuid>/<uuid>.<ext> :
    // aucun autre point ne peut y faire croire a une extension.
    String FILTERS = """
             and s.status in :statuses
             and (:type is null
                  or (:type = 'video' and (lower(s.proofData) like '%.mp4%'
                       or lower(s.proofData) like '%.webm%' or lower(s.proofData) like '%.mov%'))
                  or (:type = 'photo' and (lower(s.proofData) like '%.jpg%'
                       or lower(s.proofData) like '%.jpeg%' or lower(s.proofData) like '%.png%'
                       or lower(s.proofData) like '%.webp%')))
            """;

    String SCOPE = """
             and (:challengeId is null or c.id = :challengeId)
             and (:userId is null or u.id = :userId or exists (
                    select 1 from ChallengeSubmission sp join sp.participants pp
                    where sp = s and pp.id = :userId))
             and (:selectedOnly = false or exists (
                    select 1 from MediaLibrarySelection sel where sel.id.submissionId = s.id))
            """;

    @Query(value = "select s" + FROM + MediaLibraryEligibility.JPQL + FILTERS + SCOPE,
            countQuery = "select count(s)" + FROM + MediaLibraryEligibility.JPQL + FILTERS + SCOPE)
    Page<ChallengeSubmission> search(@Param("statuses") Collection<String> statuses,
                                     @Param("type") String type,
                                     @Param("challengeId") UUID challengeId,
                                     @Param("userId") UUID userId,
                                     @Param("selectedOnly") boolean selectedOnly,
                                     Pageable pageable);

    @Query("select new fr.insastronaute.webapi.medialibrary.repository.ChallengeCountRow(c.id, c.title, c.category, count(s))"
            + FROM + MediaLibraryEligibility.JPQL + FILTERS
            + " group by c.id, c.title, c.category order by c.title")
    List<ChallengeCountRow> countByChallenge(@Param("statuses") Collection<String> statuses, @Param("type") String type);

    @Query("select new fr.insastronaute.webapi.medialibrary.repository.PersonCountRow(u.id, count(s))"
            + FROM + MediaLibraryEligibility.JPQL + FILTERS + " group by u.id")
    List<PersonCountRow> countByAuthor(@Param("statuses") Collection<String> statuses, @Param("type") String type);

    @Query("select new fr.insastronaute.webapi.medialibrary.repository.PersonCountRow(p.id, count(s))"
            + " from ChallengeSubmission s join s.challenge c join s.user u join s.participants p where "
            + MediaLibraryEligibility.JPQL + FILTERS + " group by p.id")
    List<PersonCountRow> countByParticipant(@Param("statuses") Collection<String> statuses, @Param("type") String type);

    @Query("select s" + FROM + "s.id = :id and " + MediaLibraryEligibility.JPQL)
    Optional<ChallengeSubmission> findEligible(@Param("id") UUID id);

    @Query("select distinct s from ChallengeSubmission s join fetch s.challenge c join fetch s.user u"
            + " left join fetch s.participants where s.id in :ids and " + MediaLibraryEligibility.JPQL)
    List<ChallengeSubmission> findEligibleWithMembers(@Param("ids") Collection<UUID> ids);
}
