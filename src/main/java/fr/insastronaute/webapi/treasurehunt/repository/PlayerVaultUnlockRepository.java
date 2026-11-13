package fr.insastronaute.webapi.treasurehunt.repository;

import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PlayerVaultUnlockRepository extends JpaRepository<PlayerVaultUnlock, UUID> {
    Optional<PlayerVaultUnlock> findByUserId(UUID userId);

    @Query("select unlock.unlockedAt from PlayerVaultUnlock unlock where unlock.user.id = :userId")
    Optional<Instant> findUnlockedAtByUserId(@Param("userId") UUID userId);

    @Query("select unlock.user.id from PlayerVaultUnlock unlock")
    java.util.List<UUID> findAllUserIds();
}
