package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerVaultCodeRepository extends JpaRepository<PlayerVaultCode, UUID> {
    @Modifying
    @Query(value = """
            insert into player_vault_codes (id, user_id, path_id, code, issued_at)
            values (:id, :userId, :pathId, :code, :issuedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int tryCreateVaultCode(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("pathId") UUID pathId, @Param("code") String code,
            @Param("issuedAt") Instant issuedAt);

    Optional<PlayerVaultCode> findByUserIdAndPathId(UUID userId, UUID pathId);
    /** L'ordre du coffre : reserve a la comparaison, jamais rendu au joueur. */
    List<PlayerVaultCode> findByUserIdOrderByPathPositionAsc(UUID userId);

    /** Les memes codes, dans un ordre qui ne trahit pas la combinaison. */
    List<PlayerVaultCode> findByUserIdOrderByPathDepartmentShortNameAsc(UUID userId);

    @Query("""
            select new fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultCodeIssue(
                code.user.id, code.path.id, code.issuedAt)
            from PlayerVaultCode code
            """)
    List<TreasureHuntVaultCodeIssue> findAllIssues();

    @Query("""
            select new fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultCodeIssue(
                code.user.id, code.path.id, code.issuedAt)
            from PlayerVaultCode code
            where code.user.id = :userId
            """)
    List<TreasureHuntVaultCodeIssue> findIssuesByUserId(@Param("userId") UUID userId);
}
