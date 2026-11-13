package fr.insastronaute.webapi.sts.combat.api;

import fr.insastronaute.webapi.sts.combat.api.dto.CombatCommandEnvelope;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandRequest;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandResponse;
import fr.insastronaute.webapi.sts.domain.StsCombatCommand;
import fr.insastronaute.webapi.sts.repository.StsCombatCommandRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.sts.service.StsAuthoritativeCombatService;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Controller
public class CombatCommandController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatCommandController.class);

    private final StsRunRepository runRepository;
    private final StsCombatCommandRepository commandRepository;
    private final StsAuthoritativeCombatService combatService;
    private final CombatEventPublisher eventPublisher;
    private final CombatCommandMetrics metrics;

    public CombatCommandController(
            StsRunRepository runRepository,
            StsCombatCommandRepository commandRepository,
            StsAuthoritativeCombatService combatService,
            CombatEventPublisher eventPublisher,
            CombatCommandMetrics metrics
    ) {
        this.runRepository = runRepository;
        this.commandRepository = commandRepository;
        this.combatService = combatService;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Receives one command from a player.
     *
     * <p>Every path out of here has to say something back. A command that failed used to fail in
     * silence: the client waited for an answer that was never coming, and a player with no energy
     * left had no other move to make, so the fight stopped for good. The failure is answered here
     * and then rethrown, because the client needs to hear about it and the transaction needs to
     * roll back, and only rethrowing gets both.
     */
    @MessageMapping("/sts/combats/{combatId}/commands")
    @Transactional
    public void receiveCommand(@DestinationVariable String combatId, @Payload CombatCommandEnvelope command, Principal principal) {
        metrics.record("total", () -> receiveMeasuredCommand(combatId, command, principal));
    }

    private void receiveMeasuredCommand(String combatId, CombatCommandEnvelope command, Principal principal) {
        try {
            handleCommand(combatId, command, principal);
        } catch (RuntimeException failure) {
            LOGGER.error("STS combat command failed (combat={}, action={})",
                    combatId, command == null ? null : command.actionId(), failure);
            answerFailure(combatId, command, principal, failure);
            throw failure;
        }
    }

    private void handleCommand(String combatId, CombatCommandEnvelope command, Principal principal) {
        if (command.protocolVersion() != 1) {
            throw new BadRequestException("Unsupported combat protocol version.");
        }
        if (!combatId.equals(command.combatId())) {
            throw new BadRequestException("Combat command destination mismatch.");
        }
        if (command.actionId() == null || command.actionId().isBlank()) {
            throw new BadRequestException("Combat command action identifier is required.");
        }
        long expectedRevision = expectedRevision(command);
        var run = metrics.record("load_run", () -> runRepository
                .findOwnedWithLock(UUID.fromString(combatId), principal.getName())
                .orElseThrow(() -> new NotFoundException("Run not found: " + combatId)));

        // The same frame can be delivered twice. Answering the second delivery with what the
        // first produced is what keeps a card from being played twice on a flaky connection.
        var alreadyAnswered = metrics.record("deduplicate", () ->
                commandRepository.findByRunIdAndActionId(run.getId(), command.actionId()));
        if (alreadyAnswered.isPresent()) {
            metrics.record("publish", () ->
                    republish(principal.getName(), combatId, command, alreadyAnswered.get(), run));
            return;
        }
        
        // Extract cardInstanceId and targets from payload
        String cardInstanceId = null;
        List<String> targetIds = List.of();
        List<String> selectedCardInstanceIds = List.of();
        if (command.payload() instanceof Map<?,?> map) {
            if (map.get("cardInstanceId") instanceof String cId) cardInstanceId = cId;
            if (map.get("targetIds") instanceof List<?> tList) {
                targetIds = tList.stream().map(Object::toString).toList();
            }
            if (map.get("selectedCardInstanceIds") instanceof List<?> selectedList) {
                selectedCardInstanceIds = selectedList.stream().map(Object::toString).toList();
            }
        }
        
        StsCombatCommandRequest request = new StsCombatCommandRequest(
            command.type(), expectedRevision, cardInstanceId, targetIds,
            selectedCardInstanceIds
        );
        
        StsCombatCommandResponse response = metrics.record("process", () ->
                combatService.submitCommand(run, request));
        
        if (!response.accepted()) {
            metrics.record("store", () -> commandRepository.save(StsCombatCommand.rejected(
                    run.getId(), command.actionId(), response.rejectionCode(), response.rejectionMessage())));
            metrics.record("publish", () -> eventPublisher.publishEvent(
                    principal.getName(), combatId, new CombatMessageEnvelope(
                            1, combatId, command.expectedRevision(), "COMMAND_REJECTED",
                            command.actionId(), Map.of("code", response.rejectionCode(),
                            "message", response.rejectionMessage()))));
            return;
        }

        String resultingRevision = resultingRevision(response.combat());
        metrics.record("store", () -> commandRepository.save(StsCombatCommand.accepted(
                run.getId(), command.actionId(), Long.parseLong(resultingRevision))));

        metrics.record("publish", () -> {
            // Publish individual animation events first so Unity can play them before the state update.
            if (response.events() != null) {
                for (Object event : response.events()) {
                    String eventType = null;
                    Object eventPayload = event;
                    if (event instanceof Map<?, ?> eventMap) {
                        Object type = eventMap.get("eventType");
                        if (type instanceof String s) eventType = s;
                    }
                    if (eventType != null) {
                        eventPublisher.publishEvent(principal.getName(), combatId, new CombatMessageEnvelope(
                                1, combatId, resultingRevision, "COMBAT_EVENT", null, eventPayload));
                    }
                }
            }
            eventPublisher.publishEvent(principal.getName(), combatId, new CombatMessageEnvelope(
                    1, combatId, resultingRevision, "STATE_UPDATED", command.actionId(), response.combat()));
        });
    }

    /**
     * Tells the player that their command died, without telling them how.
     *
     * <p>A code and a flat sentence are all that leaves the server: a stack trace or a raw
     * exception message is operator material, and it already went to the log. Publishing is
     * shielded because a broker that is down must not swallow the original failure on its way out.
     */
    private void answerFailure(
            String combatId,
            CombatCommandEnvelope command,
            Principal principal,
            RuntimeException failure
    ) {
        // Without an action identifier there is nothing for the client to match the answer
        // against, and a command it never tracked is a command it is not waiting on.
        if (command == null || command.actionId() == null || command.actionId().isBlank()
                || principal == null || principal.getName() == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(principal.getName(), combatId, new CombatMessageEnvelope(
                    1, combatId, command.expectedRevision(), "COMMAND_FAILED", command.actionId(),
                    failurePayload(failure)));
        } catch (RuntimeException notDelivered) {
            LOGGER.warn("Could not tell {} that combat command {} failed",
                    principal.getName(), command.actionId(), notDelivered);
        }
    }

    private static Map<String, String> failurePayload(RuntimeException failure) {
        if (failure instanceof BadRequestException) {
            return Map.of("code", "INVALID_COMMAND",
                    "message", "This command was not valid.");
        }
        if (failure instanceof NotFoundException) {
            return Map.of("code", "COMBAT_NOT_FOUND",
                    "message", "This combat is no longer available.");
        }
        return Map.of("code", "INTERNAL_ERROR",
                "message", "The server could not process this command.");
    }

    /**
     * Answers a command that has already been answered.
     *
     * <p>The stored outcome says what happened; the state sent back is the current one, because
     * the authoritative state is always the one that counts and a client that missed the first
     * answer needs the state as it stands, not as it stood.
     */
    private void republish(
            String user,
            String combatId,
            CombatCommandEnvelope command,
            StsCombatCommand answered,
            fr.insastronaute.webapi.sts.domain.StsRun run
    ) {
        if (!answered.isAccepted()) {
            eventPublisher.publishEvent(user, combatId, new CombatMessageEnvelope(
                    1, combatId, command.expectedRevision(), "COMMAND_REJECTED", command.actionId(),
                    Map.of("code", answered.getRejectionCode(),
                            "message", answered.getRejectionMessage())));
            return;
        }
        eventPublisher.publishEvent(user, combatId, new CombatMessageEnvelope(
                1, combatId, Long.toString(answered.getResultingRevision()), "STATE_UPDATED",
                command.actionId(), combatService.combatState(run).combat()));
    }

    private static long expectedRevision(CombatCommandEnvelope command) {
        try {
            return Long.parseLong(command.expectedRevision());
        } catch (NumberFormatException notANumber) {
            throw new BadRequestException("Combat command expected revision must be a number.");
        }
    }

    private static String resultingRevision(Object combat) {
        if (combat instanceof Map<?, ?> map) {
            Object revision = map.get("revision");
            if (revision instanceof Number number && number.longValue() >= 0) {
                return Long.toString(number.longValue());
            }
            if (revision instanceof String value && value.matches("0|[1-9]\\d*")) {
                return value;
            }
        }
        throw new IllegalStateException("Invalid authoritative combat revision");
    }
}
