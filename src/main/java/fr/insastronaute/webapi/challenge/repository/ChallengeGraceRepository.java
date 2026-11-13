package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.ChallengeGrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChallengeGraceRepository extends JpaRepository<ChallengeGrace, UUID> {

    /**
     * Les graces encore valables d'un joueur, la plus lointaine d'abord.
     *
     * <p>Une seule lecture pour toute la page des defis : demander defi par defi
     * si le joueur a une grace couterait une requete par ligne, sur la page la
     * plus consultee.</p>
     */
    @Query("""
            select grace
            from ChallengeGrace grace
            where grace.user.id = :userId
              and grace.expiresAt > :now
            order by grace.expiresAt desc
            """)
    List<ChallengeGrace> findActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select grace
            from ChallengeGrace grace
            where grace.user.id in :userIds
              and grace.challenge.id = :challengeId
              and grace.expiresAt > :now
            """)
    List<ChallengeGrace> findActiveForUsersAndChallenge(
            @Param("userIds") List<UUID> userIds,
            @Param("challengeId") UUID challengeId,
            @Param("now") Instant now
    );
}
