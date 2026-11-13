package fr.insastronaute.webapi.leaderboard.repository;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaderboardEntryRepository extends JpaRepository<LeaderboardEntry, UUID> {
    List<LeaderboardEntry> findBySnapshotOrderByRankAsc(LeaderboardSnapshot snapshot);
    Optional<LeaderboardEntry> findBySnapshotAndSubjectId(LeaderboardSnapshot snapshot, UUID subjectId);
    long countBySnapshot(LeaderboardSnapshot snapshot);

    // ---- Achievement key phrases -------------------------------------------

    // Best rank ever reached on a released snapshot of one metric. Achievements
    // read history, not just the current standing: a podium you held last week
    // still counts.
    @Query("""
            select coalesce(min(entry.rank), 0)
            from LeaderboardEntry entry
            where entry.subjectId = :userId
              and entry.snapshot.config.scope = fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope.USER
              and entry.snapshot.config.metric = :metric
            """)
    int findBestRankEver(
            @Param("userId") UUID userId,
            @Param("metric") fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric metric
    );

    @Query("""
            select distinct entry.subjectId
            from LeaderboardEntry entry
            where entry.rank <= :maxRank
              and entry.snapshot.config.scope = fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope.USER
              and entry.snapshot.config.metric = :metric
              and (:afterUserId is null or entry.subjectId > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = entry.subjectId
                      and awarded.status = 'VALIDATED'
                )
            order by entry.subjectId asc
            """)
    List<UUID> findUsersEverRankedWithin(
            @Param("metric") fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric metric,
            @Param("maxRank") int maxRank,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );
}
