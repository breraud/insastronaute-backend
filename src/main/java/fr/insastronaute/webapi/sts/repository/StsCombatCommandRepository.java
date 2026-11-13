package fr.insastronaute.webapi.sts.repository;

import fr.insastronaute.webapi.sts.domain.StsCombatCommand;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StsCombatCommandRepository extends JpaRepository<StsCombatCommand, UUID> {

    Optional<StsCombatCommand> findByRunIdAndActionId(UUID runId, String actionId);
}
