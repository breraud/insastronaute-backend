package fr.insastronaute.webapi.friend.repository;

import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
            select case
                when friendship.requester.id = :userId then friendship.addressee.id
                else friendship.requester.id
            end
            from Friendship friendship
            where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.ACCEPTED
            and (friendship.requester.id = :userId or friendship.addressee.id = :userId)
            """)
    List<UUID> findAcceptedFriendIds(@Param("userId") UUID userId);

    @Query("""
            select friendship from Friendship friendship
            join fetch friendship.requester
            join fetch friendship.addressee
            where friendship.id = :id
            """)
    Optional<Friendship> findDetailedById(@Param("id") UUID id);

    @Query("""
            select friendship from Friendship friendship
            where (friendship.requester.id = :firstUserId and friendship.addressee.id = :secondUserId)
            or (friendship.requester.id = :secondUserId and friendship.addressee.id = :firstUserId)
            """)
    Optional<Friendship> findBetween(@Param("firstUserId") UUID firstUserId, @Param("secondUserId") UUID secondUserId);

    @Query("""
            select friendship from Friendship friendship
            join fetch friendship.requester
            join fetch friendship.addressee
            where friendship.status = :status
            and (friendship.requester.id = :userId or friendship.addressee.id = :userId)
            order by friendship.respondedAt desc nulls last, friendship.createdAt desc
            """)
    List<Friendship> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    @Query("""
            select friendship from Friendship friendship
            join fetch friendship.requester
            join fetch friendship.addressee
            where friendship.requester.id = :userId or friendship.addressee.id = :userId
            order by friendship.respondedAt desc nulls last, friendship.createdAt desc
            """)
    List<Friendship> findByUserIdForAdmin(@Param("userId") UUID userId);

    @Query(
            value = """
                    select friendship from Friendship friendship
                    join fetch friendship.requester
                    join fetch friendship.addressee
                    where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.PENDING
                    and friendship.addressee.id = :userId
                    """,
            countQuery = """
                    select count(friendship) from Friendship friendship
                    where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.PENDING
                    and friendship.addressee.id = :userId
                    """
    )
    Page<Friendship> findIncomingRequests(@Param("userId") UUID userId, Pageable pageable);

    @Query(
            value = """
                    select friendship from Friendship friendship
                    join fetch friendship.requester
                    join fetch friendship.addressee
                    where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.PENDING
                    and friendship.requester.id = :userId
                    """,
            countQuery = """
                    select count(friendship) from Friendship friendship
                    where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.PENDING
                    and friendship.requester.id = :userId
                    """
    )
    Page<Friendship> findOutgoingRequests(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select friendship from Friendship friendship
            join fetch friendship.requester
            join fetch friendship.addressee
            where (friendship.requester.id = :userId and friendship.addressee.id in :candidateIds)
            or (friendship.addressee.id = :userId and friendship.requester.id in :candidateIds)
            """)
    List<Friendship> findRelationsWithCandidates(
            @Param("userId") UUID userId,
            @Param("candidateIds") Collection<UUID> candidateIds
    );

    // ---- Achievement key phrases -------------------------------------------

    @Query("""
            select count(friendship)
            from Friendship friendship
            where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.ACCEPTED
              and (friendship.requester.id = :userId or friendship.addressee.id = :userId)
            """)
    long countAcceptedFriends(@Param("userId") UUID userId);

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
                    select count(friendship)
                    from Friendship friendship
                    where friendship.status = fr.insastronaute.webapi.friend.domain.FriendshipStatus.ACCEPTED
                      and (friendship.requester.id = candidate.id or friendship.addressee.id = candidate.id)
                  ) >= :threshold
            order by candidate.id asc
            """)
    List<UUID> findUsersWithEnoughFriends(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Toutes les amities ou ce joueur figure, dans un sens ou dans l'autre, quel que soit
     * leur statut.
     *
     * <p>Les demandes en attente comptent autant que les amities etablies : laissees en
     * place, elles proposeraient a d'autres joueurs de repondre a quelqu'un qui n'est
     * plus la.</p>
     */
    @Query("""
            select friendship from Friendship friendship
            where friendship.requester.id = :userId or friendship.addressee.id = :userId
            """)
    List<Friendship> findAllInvolving(@Param("userId") UUID userId);
}
