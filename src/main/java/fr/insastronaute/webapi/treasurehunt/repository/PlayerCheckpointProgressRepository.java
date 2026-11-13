package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.PlayerCheckpointProgress;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PlayerCheckpointProgressRepository extends JpaRepository<PlayerCheckpointProgress, UUID> {
    boolean existsByUserIdAndCheckpointId(UUID userId, UUID checkpointId);
    boolean existsByUserIdAndCheckpointPathIdAndCheckpointStepNumber(UUID userId, UUID pathId, Integer stepNumber);
    long countByUserIdAndCheckpointPathId(UUID userId, UUID pathId);

    /**
     * Les checkpoints que ce joueur a deja valides, dans l'ordre du carnet.
     *
     * Le contenu d'une etape n'etait rendu que par la validation du code, et le code
     * est sur une balise posee dans l'INSA : passe la premiere lecture, le joueur ne
     * pouvait plus revenir dessus. Cette liste rouvre ce qu'il a gagne, sans jamais
     * rendre un code -- ni le sien, ni celui d'une etape qu'il n'a pas atteinte.
     */
    @Query("""
            select checkpoint
            from PlayerCheckpointProgress progress
            join progress.checkpoint checkpoint
            join checkpoint.path path
            join path.department department
            where progress.user.id = :userId
            order by department.shortName asc, checkpoint.stepNumber asc
            """)
    List<TreasureHuntCheckpoint> findValidatedCheckpoints(@Param("userId") UUID userId);

    // ---- Achievement key phrases -------------------------------------------

    @Query("select count(progress) from PlayerCheckpointProgress progress where progress.user.id = :userId")
    long countValidatedCheckpoints(@Param("userId") UUID userId);

    @Query("""
            select progress.user.id
            from PlayerCheckpointProgress progress
            where (:afterUserId is null or progress.user.id > :afterUserId)
            and not exists (
                  select 1 from ChallengeSubmission awarded
                  where awarded.challenge.id = :challengeId
                    and awarded.user.id = progress.user.id
                    and awarded.status = 'VALIDATED'
              )
            group by progress.user.id
            having count(progress) >= :threshold
            order by progress.user.id asc
            """)
    List<UUID> findUsersWithValidatedCheckpoints(
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    @Query("""
            select new fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntValidation(
                progress.user.id, checkpoint.path.id, checkpoint.id, checkpoint.stepNumber, progress.validatedAt)
            from PlayerCheckpointProgress progress
            join progress.checkpoint checkpoint
            """)
    List<TreasureHuntValidation> findAllValidations();

    @Query("""
            select new fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntValidation(
                progress.user.id, checkpoint.path.id, checkpoint.id, checkpoint.stepNumber, progress.validatedAt)
            from PlayerCheckpointProgress progress
            join progress.checkpoint checkpoint
            where progress.user.id = :userId
            """)
    List<TreasureHuntValidation> findValidationsByUserId(@Param("userId") UUID userId);

    @Query("""
            select new fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntValidation(
                progress.user.id, checkpoint.path.id, checkpoint.id, checkpoint.stepNumber, progress.validatedAt)
            from PlayerCheckpointProgress progress
            join progress.checkpoint checkpoint
            where checkpoint.id in :checkpointIds
            """)
    List<TreasureHuntValidation> findValidationsByCheckpointIds(@Param("checkpointIds") java.util.Collection<UUID> checkpointIds);
}
