package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface ChallengeSubmissionRepository extends JpaRepository<ChallengeSubmission, UUID> {
    interface ParticipantConfirmationProjection {
        UUID getUserId();

        String getDisplayName();

        String getEmail();

        String getConfirmationStatus();

        Instant getInvitedAt();

        Instant getRespondedAt();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChallengeSubmission s where s.id = :id")
    Optional<ChallengeSubmission> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select distinct s
            from ChallengeSubmission s
            left join s.participants participant
            where s.challenge.id = :challengeId
              and (s.user.id = :userId or participant.id = :userId)
            """)
    List<ChallengeSubmission> findByUserIdOrParticipantUserIdAndChallengeId(
            @Param("userId") UUID userId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            select distinct s
            from ChallengeSubmission s
            join fetch s.challenge
            left join s.participants participant
            where s.user.id = :userId or participant.id = :userId
            order by s.createdAt desc
            """)
    List<ChallengeSubmission> findByUserIdOrParticipantUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("""
            select distinct s
            from ChallengeSubmission s
            join fetch s.challenge
            left join s.participants participant
            where (s.user.id = :userId or participant.id = :userId)
              and s.status = :status
            order by s.createdAt desc
            """)
    List<ChallengeSubmission> findByUserIdOrParticipantUserIdAndStatusOrderByCreatedAtDesc(
            @Param("userId") UUID userId,
            @Param("status") String status
    );

    @Query("""
            select distinct s
            from ChallengeSubmission s
            join fetch s.challenge
            join fetch s.user
            left join fetch s.participants participant
            where s.user.id = :userId or participant.id = :userId
            order by s.createdAt desc
            """)
    List<ChallengeSubmission> findDetailedByUserIdOrParticipantUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("""
            select distinct s
            from ChallengeSubmission s
            left join fetch s.participants participant
            where s.challenge.id = :challengeId
              and s.status in ('WAITING_CONFIRMATION', 'PENDING', 'VALIDATED')
              and (s.user.id in :userIds or exists (
                  select matchingParticipant.id
                  from ChallengeSubmission matchingSubmission
                  join matchingSubmission.participants matchingParticipant
                  where matchingSubmission = s and matchingParticipant.id in :userIds
              ))
            """)
    List<ChallengeSubmission> findBlockingSubmissionsForUsersAndChallenge(
            @Param("userIds") List<UUID> userIds,
            @Param("challengeId") UUID challengeId
    );

    /**
     * Les defis qu'un joueur a deja fait valider, en une requete.
     *
     * <p>La page des defis appelait {@code existsValidatedSubmissionForUserAndChallenge}
     * une fois par prerequis de chaque defi. Les prerequis d'une page se resolvent en une
     * seule lecture : on demande l'ensemble une fois, on l'interroge en memoire.</p>
     */
    @Query("""
            select distinct s.challenge.id
            from ChallengeSubmission s
            left join s.participants participant
            where s.status = 'VALIDATED'
              and (s.user.id = :userId or participant.id = :userId)
            """)
    List<UUID> validatedChallengeIdsForUser(@Param("userId") UUID userId);

    /**
     * Toutes les soumissions d'un joueur, en une requete, pour une page entiere.
     *
     * <p>Meme motif : {@code findByUserIdOrParticipantUserIdAndChallengeId} etait
     * appelee defi par defi. Le {@code distinct} reste necessaire, la jointure sur les
     * participants dupliquant les lignes.</p>
     */
    @Query("""
            select distinct s
            from ChallengeSubmission s
            left join s.participants participant
            where s.user.id = :userId or participant.id = :userId
            """)
    List<ChallengeSubmission> findByUserIdOrParticipantUserId(@Param("userId") UUID userId);

    @Query("""
            select count(s) > 0
            from ChallengeSubmission s
            left join s.participants participant
            where s.challenge.id = :challengeId
              and s.status = 'VALIDATED'
              and (s.user.id = :userId or participant.id = :userId)
            """)
    boolean existsValidatedSubmissionForUserAndChallenge(
            @Param("userId") UUID userId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            select distinct submission.challenge.id
            from ChallengeSubmission submission
            left join submission.participants participant
            where submission.status = 'VALIDATED'
              and (submission.user.id = :userId or participant.id = :userId)
            """)
    List<UUID> findValidatedChallengeIdsByUserIdOrParticipantUserId(@Param("userId") UUID userId);

    @Modifying
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            update challenge_submission_participants
            set confirmation_status = :status,
                responded_at = CURRENT_TIMESTAMP
            where submission_id = :submissionId
              and user_id = :userId
            """, nativeQuery = true)
    int updateParticipantConfirmation(
            @Param("submissionId") UUID submissionId,
            @Param("userId") UUID userId,
            @Param("status") String status
    );

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select count(*)
            from challenge_submission_participants
            where submission_id = :submissionId
              and confirmation_status <> 'ACCEPTED'
            """, nativeQuery = true)
    long countParticipantsNotAccepted(@Param("submissionId") UUID submissionId);

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select u.id as "userId",
                   u.display_name as "displayName",
                   u.email as email,
                   p.confirmation_status as "confirmationStatus",
                   p.invited_at as "invitedAt",
                   p.responded_at as "respondedAt"
            from challenge_submission_participants p
            join users u on u.id = p.user_id
            where p.submission_id = :submissionId
            order by lower(coalesce(u.display_name, u.email)), u.id
            """, nativeQuery = true)
    List<ParticipantConfirmationProjection> findParticipantConfirmations(@Param("submissionId") UUID submissionId);

    @Modifying
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            update challenge_submission_participants
            set invited_at = CURRENT_TIMESTAMP
            where submission_id = :submissionId
              and confirmation_status = 'PENDING'
            """, nativeQuery = true)
    void touchPendingParticipantInvites(@Param("submissionId") UUID submissionId);

    List<ChallengeSubmission> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Les preuves deja tranchees d'un defi.
     *
     * <p>Les preuves en attente en sont exclues, et c'est le point : sans sa photo,
     * une soumission devient injugeable et bloque la file de moderation.</p>
     */
    List<ChallengeSubmission> findByChallengeIdAndStatusIn(UUID challengeId, Collection<String> statuses);

    @Query("""
            select distinct s
            from ChallengeSubmission s
            left join fetch s.participants
            join fetch s.challenge
            join fetch s.user
            where s.status = 'VALIDATED'
              and s.challenge.deletedAt is null
            order by s.challenge.id, s.validatedAt asc nulls last, s.createdAt asc, s.id asc
            """)
    List<ChallengeSubmission> findValidatedSubmissionsForPointRecalculation();

    @Query("""
            select distinct s
            from ChallengeSubmission s
            join fetch s.challenge
            join fetch s.user
            left join fetch s.participants
            where s.challenge.id = :challengeId
              and s.status = 'VALIDATED'
            order by s.validatedAt asc nulls last, s.createdAt asc, s.id asc
            """)
    List<ChallengeSubmission> findValidatedSubmissionsForChallenge(@Param("challengeId") UUID challengeId);

    // ---- Achievement key phrases -------------------------------------------

    @Query("""
            select count(distinct submission.challenge.id)
            from ChallengeSubmission submission
            left join submission.participants participant
            where submission.status = 'VALIDATED'
              and (submission.user.id = :userId or participant.id = :userId)
            """)
    long countValidatedChallengesForUser(@Param("userId") UUID userId);

    /**
     * Les défis que les amis d'un joueur ont validés, du plus partagé au moins
     * partagé.
     *
     * <p>Sert aux recommandations : « trois de tes amis l'ont fait » est un
     * argument que ni la date ni la catégorie ne remplacent. Le classement se
     * fait en SQL parce que seul le haut du tas est affiché.
     */
    @Query("""
            select submission.challenge.id
            from ChallengeSubmission submission
            where submission.status = 'VALIDATED'
              and submission.user.id in :friendIds
              and submission.challenge.id not in :excludedChallengeIds
            group by submission.challenge.id
            order by count(submission) desc, submission.challenge.id asc
            """)
    List<UUID> findChallengeIdsValidatedByFriends(
            @Param("friendIds") Collection<UUID> friendIds,
            @Param("excludedChallengeIds") Collection<UUID> excludedChallengeIds,
            Pageable pageable
    );


    /**
     * Combien de joueurs sont credites par les soumissions validees de ce
     * defi.
     *
     * <p>C'est la portee d'un changement de points : recalculateChallengePoints
     * recredite exactement ces joueurs. Sous-requete correlee sur AppUser
     * plutot que jointure, pour la meme raison que findUsersWithValidatedChallenges
     * plus bas : la regle "auteur ou participant" ne se groupe pas proprement,
     * et il faut un seul compte de joueurs distincts a travers les deux.</p>
     *
     * <p>Le quota global d'un defi n'entre pas dans ce compte : il limite le
     * nombre de soumissions recompensees, ce qui ne s'exprime pas en une seule
     * requete. Sur un defi a quota atteint, le compte est donc celui des
     * joueurs valides, majorant de ceux que le recalcul recredite.</p>
     */
    @Query("""
            select count(player)
            from AppUser player
            where exists (
                  select 1
                  from ChallengeSubmission submission
                  left join submission.participants participant
                  where submission.challenge.id = :challengeId
                    and submission.status = 'VALIDATED'
                    and (submission.user.id = player.id or participant.id = player.id)
              )
            """)
    long countPlayersCreditedByChallenge(@Param("challengeId") UUID challengeId);

    /**
     * Les soumissions d'un defi, par identifiant seul.
     *
     * <p>Sert a estampiller leur audit avant que la cascade les emporte : charger les
     * entites entieres pour n'en lire que la cle serait du gachis sur un defi qui en
     * compte des centaines.</p>
     */
    @Query("select submission.id from ChallengeSubmission submission where submission.challenge.id = :challengeId")
    List<UUID> findIdsByChallengeId(@Param("challengeId") UUID challengeId);

    /**
     * Les soumissions qu'un joueur a lancees.
     *
     * <p>Pas celles ou il n'est que participant : supprimer sa ligne n'emporte que
     * les siennes, les autres survivent sans lui.</p>
     */
    @Query("select submission.id from ChallengeSubmission submission where submission.user.id = :userId")
    List<UUID> findIdsByUserId(@Param("userId") UUID userId);

    /** Combien de preuves ce defi porte, validees ou non. */
    long countByChallengeId(UUID challengeId);

    /** Lesquelles de ces soumissions existent encore, pour toute une page d'un coup. */
    @Query("select submission.id from ChallengeSubmission submission where submission.id in :ids")
    List<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    // Correlated subquery rather than an entity join with a disjunctive ON: the
    // "owner or participant" rule does not group cleanly, and this scan is an
    // hourly safety net over a cohort-sized user table, not a hot path.
    @Query("""
            select candidate.id
            from AppUser candidate
            where (:afterUserId is null or candidate.id > :afterUserId)
            and not exists (
                  select 1 from ChallengeSubmission awarded
                  where awarded.challenge.id = :challengeId
                    and awarded.user.id = candidate.id
                    and awarded.status = 'VALIDATED'
              )
              and (
                    select count(distinct submission.challenge.id)
                    from ChallengeSubmission submission
                    left join submission.participants participant
                    where submission.status = 'VALIDATED'
                      and (submission.user.id = candidate.id or participant.id = candidate.id)
                  ) >= :threshold
            order by candidate.id asc
            """)
    List<UUID> findUsersWithValidatedChallenges(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("""
            select submission.validatedAt
            from ChallengeSubmission submission
            left join submission.participants participant
            where submission.status = 'VALIDATED'
              and submission.validatedAt is not null
              and (submission.user.id = :userId or participant.id = :userId)
            """)
    List<java.time.Instant> findValidationInstantsForUser(@Param("userId") UUID userId);

    /**
     * Les défis du jour distincts qu'un joueur a fait valider, seul ou en groupe.
     *
     * <p>DAILY compte en catégorie principale comme en secondaire, comme partout
     * ailleurs. Un défi supprimé sort du compte : ses points tombent à la
     * suppression, sa validation aussi.</p>
     */
    @Query("""
            select count(distinct submission.challenge.id)
            from ChallengeSubmission submission
            left join submission.participants participant
            where submission.status = 'VALIDATED'
              and (submission.user.id = :userId or participant.id = :userId)
              and submission.challenge.deletedAt is null
              and (submission.challenge.category = 'DAILY'
                   or 'DAILY' member of submission.challenge.extraCategories)
            """)
    long countValidatedDailyChallengesForUser(@Param("userId") UUID userId);

    /** Le balayage de {@link #countValidatedDailyChallengesForUser}, sur la forme de findUsersWithValidatedChallenges. */
    @Query("""
            select candidate.id
            from AppUser candidate
            where (:afterUserId is null or candidate.id > :afterUserId)
            and not exists (
                  select 1 from ChallengeSubmission awarded
                  where awarded.challenge.id = :challengeId
                    and awarded.user.id = candidate.id
                    and awarded.status = 'VALIDATED'
              )
              and (
                    select count(distinct submission.challenge.id)
                    from ChallengeSubmission submission
                    left join submission.participants participant
                    where submission.status = 'VALIDATED'
                      and (submission.user.id = candidate.id or participant.id = candidate.id)
                      and submission.challenge.deletedAt is null
                      and (submission.challenge.category = 'DAILY'
                           or 'DAILY' member of submission.challenge.extraCategories)
                  ) >= :threshold
            order by candidate.id asc
            """)
    List<UUID> findUsersWithValidatedDailyChallenges(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Ce que la promo a deja sur ce defi : une preuve en cours, ou une validee.
     *
     * <p>Le departement du <em>porteur</em> de la preuve fait foi, pas celui de ses
     * coequipiers : un groupe peut melanger les promos, et il faut bien qu'une
     * seule soit creditee.</p>
     */
    @Query("""
            select submission
            from ChallengeSubmission submission
            where submission.challenge.id = :challengeId
              and submission.user.departmentId = :departmentId
              and submission.status in ('PENDING', 'WAITING_CONFIRMATION', 'VALIDATED')
            """)
    List<ChallengeSubmission> findPromoBlockingSubmissions(
            @Param("challengeId") UUID challengeId,
            @Param("departmentId") UUID departmentId
    );

    /** Le meme etat, pour tous les defis de promo d'un coup : la liste des defis le lit. */
    @Query("""
            select submission
            from ChallengeSubmission submission
            where submission.challenge.promo = true
              and submission.user.departmentId = :departmentId
              and submission.status in ('PENDING', 'WAITING_CONFIRMATION', 'VALIDATED')
            """)
    List<ChallengeSubmission> findPromoSubmissionsForDepartment(@Param("departmentId") UUID departmentId);

    /**
     * Les defis de promo remportes, par departement.
     *
     * <p>La soumission validee fait foi : ses points suivent d'eux-memes un
     * ajustement de bonus, une revocation ou un refus, sans registre parallele a
     * defaire.</p>
     */
    @Query("""
            select submission.challenge.id, submission.user.departmentId,
                   submission.challenge.pointsReward, submission.bonusPoints
            from ChallengeSubmission submission
            where submission.challenge.promo = true
              and submission.status = 'VALIDATED'
              and submission.user.departmentId is not null
            """)
    List<Object[]> findValidatedPromoAwards();
}
