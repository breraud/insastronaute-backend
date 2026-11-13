package fr.insastronaute.webapi.user.repository;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID>, UserRepositoryCustom {

    @Query("""
            select user.id from AppUser user
            where user.scanned = true and (:afterUserId is null or user.id > :afterUserId)
            order by user.id
            """)
    List<UUID> findScannedUserIdsAfter(@Param("afterUserId") UUID afterUserId, Pageable pageable);

    Optional<AppUser> findByKeycloakSub(String keycloakSub);

    // La file de validation d'accueil. Une requete derivee suffit : la file est
    // petite et l'index partiel ix_users_onboarding_pending (V109) la sert.
    List<AppUser> findByOnboardingStatusOrderByOnboardingSubmittedAtAsc(OnboardingStatus status);

    // Les comptes refuses, du plus recemment tranche au plus ancien : c'est la
    // decision, pas la soumission, qui interesse le respo qui cherche ici le
    // recours humain promis par l'ecran de refus (spec 2.4).
    List<AppUser> findByOnboardingStatusOrderByOnboardingDecidedAtDesc(OnboardingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.keycloakSub = :keycloakSub")
    Optional<AppUser> findWithLockByKeycloakSub(@Param("keycloakSub") String keycloakSub);

    @Query("""
            select user from AppUser user
            where user.id <> :currentUserId
            and (
                :query is null
                or lower(coalesce(user.displayName, '')) like lower(concat('%', :query, '%'))
                or lower(user.email) like lower(concat('%', :query, '%'))
            )
            """)
    Page<AppUser> searchFriendCandidates(
            @Param("currentUserId") UUID currentUserId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select distinct user from AppUser user
            where user.id <> :currentUserId
            and (
                lower(coalesce(user.displayName, '')) like lower(concat('%', :query, '%'))
                or lower(user.email) like lower(concat('%', :query, '%'))
            )
            and (
                (:departmentId is not null and user.departmentId = :departmentId)
                or exists (
                    select friendship.id from Friendship friendship
                    where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.ACCEPTED
                    and (
                        (friendship.requester.id = :currentUserId and friendship.addressee = user)
                        or (friendship.addressee.id = :currentUserId and friendship.requester = user)
                    )
                )
            )
            """)
    Page<AppUser> searchDepartmentPeopleAndFriends(
            @Param("currentUserId") UUID currentUserId,
            @Param("departmentId") UUID departmentId,
            @Param("query") String query,
            Pageable pageable
    );

    // ---- Achievement key phrases -------------------------------------------

    @Query("""
            select candidate.id from AppUser candidate
            where candidate.tokens >= :threshold
              and (:afterUserId is null or candidate.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = candidate.id
                      and awarded.status = 'VALIDATED'
                )
            order by candidate.id asc
            """)
    List<UUID> findUsersWithTokenBalance(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("""
            select candidate.id from AppUser candidate
            where candidate.placeBonusPixelsUsed >= :threshold
              and (:afterUserId is null or candidate.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = candidate.id
                      and awarded.status = 'VALIDATED'
                )
            order by candidate.id asc
            """)
    List<UUID> findUsersWithBonusPixelsUsed(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("""
            select candidate.id from AppUser candidate
            where candidate.scanned = true
              and (:afterUserId is null or candidate.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = candidate.id
                      and awarded.status = 'VALIDATED'
                )
            order by candidate.id asc
            """)
    List<UUID> findUsersWhoScanned(
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );
}
