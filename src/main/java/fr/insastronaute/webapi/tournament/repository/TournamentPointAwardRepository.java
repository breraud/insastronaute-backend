package fr.insastronaute.webapi.tournament.repository;

import fr.insastronaute.webapi.tournament.domain.TournamentPointAward;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TournamentPointAwardRepository extends JpaRepository<TournamentPointAward, UUID> {

    @EntityGraph(attributePaths = "user")
    List<TournamentPointAward> findByEventId(UUID eventId);

    void deleteByEventId(UUID eventId);

    @Query("""
            select award.event.id, coalesce(sum(award.totalPoints), 0)
            from TournamentPointAward award
            where award.event.id in :eventIds
            group by award.event.id
            """)
    List<Object[]> sumTotalPointsByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    // ---- Achievement key phrases -------------------------------------------

    @Query("""
            select count(award)
            from TournamentPointAward award
            where award.user.id = :userId
              and award.tierLabel is not null
            """)
    long countPodiumsForUser(@Param("userId") UUID userId);

    @Query("""
            select award.user.id
            from TournamentPointAward award
            where award.tierLabel is not null
              and (:afterUserId is null or award.user.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = award.user.id
                      and awarded.status = 'VALIDATED'
                )
            group by award.user.id
            having count(award) >= :threshold
            order by award.user.id asc
            """)
    List<UUID> findUsersWithPodiums(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );
}
