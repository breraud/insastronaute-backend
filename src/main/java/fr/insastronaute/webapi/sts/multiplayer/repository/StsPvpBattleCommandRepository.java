package fr.insastronaute.webapi.sts.multiplayer.repository;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StsPvpBattleCommandRepository extends JpaRepository<StsPvpBattleCommand, UUID> {

    Optional<StsPvpBattleCommand> findByBattleIdAndActingUserIdAndActionId(
            UUID battleId, UUID actingUserId, String actionId);
}
