package fr.insastronaute.webapi.sts.repository;

import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StsRunRepository extends JpaRepository<StsRun, UUID> {

    Optional<StsRun> findFirstByOwner_KeycloakSubAndStatusOrderByUpdatedAtDesc(String keycloakSub, StsRunStatus status);

    List<StsRun> findByOwner_KeycloakSubAndStatus(String keycloakSub, StsRunStatus status);

    @Query("select run from StsRun run where run.id = :id and run.owner.keycloakSub = :keycloakSub")
    Optional<StsRun> findOwned(@Param("id") UUID id, @Param("keycloakSub") String keycloakSub);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from StsRun run where run.id = :id and run.owner.keycloakSub = :keycloakSub")
    Optional<StsRun> findOwnedWithLock(@Param("id") UUID id, @Param("keycloakSub") String keycloakSub);

    // ---- Achievement key phrases -------------------------------------------
    //
    // A finished run is one the player RETIRED: that is the ending that banks a
    // score and pays tokens. StsRunStatus.Completed is never assigned anywhere
    // in the codebase, so counting it would count nothing.

    @Query("""
            select count(run) from StsRun run
            where run.owner.id = :userId
              and run.status = fr.insastronaute.webapi.sts.domain.StsRunStatus.Retired
            """)
    long countFinishedRuns(@Param("userId") UUID userId);

    @Query("""
            select run.owner.id from StsRun run
            where run.status = fr.insastronaute.webapi.sts.domain.StsRunStatus.Retired
              and (:afterUserId is null or run.owner.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = run.owner.id
                      and awarded.status = 'VALIDATED'
                )
            group by run.owner.id
            having count(run) >= :threshold
            order by run.owner.id asc
            """)
    List<UUID> findUsersWithFinishedRuns(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    // Deepest floor over every run, finished or not: reaching floor 30 and dying
    // there is still reaching floor 30.
    @Query("select coalesce(max(run.currentFloor), 0) from StsRun run where run.owner.id = :userId")
    int findDeepestFloor(@Param("userId") UUID userId);

    @Query("""
            select run.owner.id from StsRun run
            where run.currentFloor >= :threshold
              and (:afterUserId is null or run.owner.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = run.owner.id
                      and awarded.status = 'VALIDATED'
                )
            group by run.owner.id
            order by run.owner.id asc
            """)
    List<UUID> findUsersReachingFloor(
            @Param("threshold") int threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("select coalesce(max(run.retirementScore), 0) from StsRun run where run.owner.id = :userId")
    long findBestRetirementScore(@Param("userId") UUID userId);

    /**
     * Ce que le jeu a rapporte a chacun : la somme des jetons verses en fin de partie.
     *
     * <p>Les jetons, et non le score. Le score est un compteur interne a la partie,
     * converti en jetons par les regles de la version de jeu : deux parties de meme
     * score ne versent pas forcement autant, et c'est le versement qui est le gain.
     * C'est aussi lui que le joueur voit arriver sur son solde.</p>
     *
     * <p>Une seule requete pour tout le classement. Interroger joueur par joueur
     * ferait une requete par ligne classee, et il y a autant de lignes que
     * d'inscrits.</p>
     *
     * <p>Les parties en cours n'ont rien verse et sont ecartees : une partie compte
     * quand elle est finie, pas pendant qu'on la joue.</p>
     */
    @Query("""
            select run.owner.id, coalesce(sum(run.retirementTokens), 0)
            from StsRun run
            where run.retirementTokens is not null
            group by run.owner.id
            """)
    List<Object[]> sumRetirementTokensByOwner();

    @Query("""
            select run.owner.id from StsRun run
            where run.retirementScore >= :threshold
              and (:afterUserId is null or run.owner.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = run.owner.id
                      and awarded.status = 'VALIDATED'
                )
            group by run.owner.id
            order by run.owner.id asc
            """)
    List<UUID> findUsersWithRetirementScore(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Ce que chaque partie terminee a rapporte : montant, date, joueur.
     *
     * <p>Une projection plutot que les runs : l'onglet stats les lit toutes, et une
     * run porte un etat de partie complet dont il n'a pas besoin.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
            select run.retirementTokens, run.updatedAt, run.owner.id
            from StsRun run
            where run.retirementTokens is not null and run.retirementTokens > 0
            """)
    java.util.List<Object[]> findRetirementPayouts();

    /** Combien de runs chaque personnage a lancees, sans filtre de période. */
    @Query("""
            select run.selectedCharacter, run.status, count(run)
            from StsRun run
            group by run.selectedCharacter, run.status
            """)
    List<Object[]> countByCharacterAndStatus();

    /** Combien de runs chaque personnage a lancees depuis une date donnée. */
    @Query("""
            select run.selectedCharacter, run.status, count(run)
            from StsRun run
            where run.createdAt >= :since
            group by run.selectedCharacter, run.status
            """)
    List<Object[]> countByCharacterAndStatusSince(@Param("since") java.time.Instant since);

    /** Combien de runs un joueur a lancees avec chaque personnage. */
    @Query("""
            select run.selectedCharacter, count(run)
            from StsRun run
            where run.owner.id = :userId
            group by run.selectedCharacter
            order by count(run) desc
            """)
    List<Object[]> countByCharacterForOwner(@Param("userId") UUID userId);
}
