package fr.insastronaute.webapi.sts.combat.api;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatCommandEnvelope;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandResponse;
import fr.insastronaute.webapi.sts.domain.StsCombatCommand;
import fr.insastronaute.webapi.sts.repository.StsCombatCommandRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.sts.service.StsAuthoritativeCombatService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CombatCommandControllerTest {

    private final StsRunRepository repository = mock(StsRunRepository.class);
    private final StsAuthoritativeCombatService service = mock(StsAuthoritativeCombatService.class);
    private final CombatEventPublisher publisher = mock(CombatEventPublisher.class);
    private final StsCombatCommandRepository commandRepository = mock(StsCombatCommandRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CombatCommandController controller =
            new CombatCommandController(repository, commandRepository, service, publisher,
                    new CombatCommandMetrics(meterRegistry));
    private final Principal principal = () -> "owner-sub";

    @Test
    void measuresTheCommandAroundItsDatabaseProcessingAndPublicationBoundaries() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        Map<String, Object> state = Map.of("revision", 43, "status", "ACTIVE");
        var command = new CombatCommandEnvelope(
                1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(run.getId(), "action-1"))
                .thenReturn(Optional.empty());
        when(service.submitCommand(eq(run), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new StsCombatCommandResponse(true, runId, state, List.of(), null, null));

        controller.receiveCommand(runId.toString(), command, principal);

        assertThat(recordedPhases()).containsExactlyInAnyOrder(
                "load_run", "deduplicate", "process", "store", "publish", "total");
    }

    private List<String> recordedPhases() {
        return meterRegistry.find("sts.combat.command.duration").timers().stream()
                .filter(timer -> timer.count() > 0)
                .map(timer -> timer.getId().getTag("phase"))
                .toList();
    }

    @Test
    void answersASecondDeliveryOfTheSameCommandWithoutResolvingItAgain() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        Map<String, Object> state = Map.of("revision", 43, "status", "ACTIVE");
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(run.getId(), "action-1"))
                .thenReturn(Optional.of(StsCombatCommand.accepted(run.getId(), "action-1", 43)));
        when(service.combatState(run))
                .thenReturn(new fr.insastronaute.webapi.sts.dto.StsCombatStateResponse(
                        true, runId, state));

        controller.receiveCommand(runId.toString(), command, principal);

        verify(service, never()).submitCommand(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(publisher).publishEvent(
                "owner-sub",
                runId.toString(),
                new CombatMessageEnvelope(1, runId.toString(), "43", "STATE_UPDATED", "action-1", state));
    }

    @Test
    void answersASecondDeliveryOfARejectedCommandWithTheSameRejection() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(run.getId(), "action-1"))
                .thenReturn(Optional.of(StsCombatCommand.rejected(
                        run.getId(), "action-1", "STALE_REVISION", "too late")));

        controller.receiveCommand(runId.toString(), command, principal);

        verify(service, never()).submitCommand(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(publisher).publishEvent(
                "owner-sub",
                runId.toString(),
                new CombatMessageEnvelope(1, runId.toString(), "42", "COMMAND_REJECTED", "action-1",
                        Map.of("code", "STALE_REVISION", "message", "too late")));
    }

    @Test
    void remembersTheOutcomeSoTheNextDeliveryCanBeAnswered() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        Map<String, Object> state = Map.of("revision", 43, "status", "ACTIVE");
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(run.getId(), "action-1"))
                .thenReturn(Optional.empty());
        when(service.submitCommand(org.mockito.ArgumentMatchers.eq(run), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new StsCombatCommandResponse(true, runId, state, List.of(), null, null));

        controller.receiveCommand(runId.toString(), command, principal);

        verify(commandRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.isAccepted() && saved.getResultingRevision() == 43L
                        && "action-1".equals(saved.getActionId())));
    }

    @Test
    void refusesACommandThatCarriesNoActionIdentifier() {
        UUID runId = UUID.randomUUID();
        var command = new CombatCommandEnvelope(1, " ", runId.toString(), "42", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .isInstanceOf(BadRequestException.class);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void refusesARevisionThatIsNotANumber() {
        UUID runId = UUID.randomUUID();
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "later", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .describedAs("a malformed revision is a bad request, not a server error")
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void publishesCombatEventsBeforeOneAuthoritativeStateUpdateAtTheResultingRevision() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        Map<String, Object> state = Map.of("revision", 43, "status", "ACTIVE");
        Map<String, Object> event = Map.of("eventType", "TurnEnded");
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(service.submitCommand(org.mockito.ArgumentMatchers.eq(run), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new StsCombatCommandResponse(true, runId, state, List.of(event), null, null));

        controller.receiveCommand(runId.toString(), command, principal);

        InOrder ordered = inOrder(publisher);
        ordered.verify(publisher).publishEvent(
                "owner-sub",
                runId.toString(),
                new CombatMessageEnvelope(1, runId.toString(), "43", "COMBAT_EVENT", null, event));
        ordered.verify(publisher).publishEvent(
                "owner-sub",
                runId.toString(),
                new CombatMessageEnvelope(1, runId.toString(), "43", "STATE_UPDATED", "action-1", state));
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void refusesCommandsForRunsNotOwnedByThePrincipal() {
        UUID runId = UUID.randomUUID();
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "0", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .isInstanceOf(NotFoundException.class);
        verify(service, never()).submitCommand(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnEnvelopeWhoseCombatDoesNotMatchTheDestination() {
        UUID runId = UUID.randomUUID();
        var command = new CombatCommandEnvelope(1, "action-1", UUID.randomUUID().toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).findOwnedWithLock(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnUnsupportedProtocolVersion() {
        UUID runId = UUID.randomUUID();
        var command = new CombatCommandEnvelope(2, "action-1", runId.toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void tellsTheClientWhichCommandFailedWhenHandlingBlowsUp() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(service.submitCommand(eq(run), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("hand holds a card that is not in the deck"));

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .describedAs("the failure still has to leave the method, or the transaction never rolls back")
                .isInstanceOf(IllegalStateException.class);

        verify(publisher).publishEvent(
                "owner-sub",
                runId.toString(),
                new CombatMessageEnvelope(1, runId.toString(), "42", "COMMAND_FAILED", "action-1",
                        Map.of("code", "INTERNAL_ERROR",
                                "message", "The server could not process this command.")));
    }

    @Test
    void keepsTheInternalFailureDetailOutOfWhatTheClientSees() {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.of(run));
        when(commandRepository.findByRunIdAndActionId(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(service.submitCommand(eq(run), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("jdbc error on relation sts_run_card"));

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .isInstanceOf(IllegalStateException.class);

        var published = org.mockito.ArgumentCaptor.forClass(CombatMessageEnvelope.class);
        verify(publisher).publishEvent(eq("owner-sub"), eq(runId.toString()), published.capture());
        assertThat(String.valueOf(published.getValue().payload()))
                .describedAs("a player is not an operator; the stack trace belongs in the server log")
                .doesNotContain("jdbc")
                .doesNotContain("sts_run_card");
    }

    @Test
    void tellsTheClientWhenTheCombatItNamedCannotBeFound() {
        UUID runId = UUID.randomUUID();
        var command = new CombatCommandEnvelope(1, "action-1", runId.toString(), "42", "END_TURN", Map.of());
        when(repository.findOwnedWithLock(runId, "owner-sub")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.receiveCommand(runId.toString(), command, principal))
                .isInstanceOf(NotFoundException.class);

        verify(publisher).publishEvent(
                "owner-sub",
                runId.toString(),
                new CombatMessageEnvelope(1, runId.toString(), "42", "COMMAND_FAILED", "action-1",
                        Map.of("code", "COMBAT_NOT_FOUND",
                                "message", "This combat is no longer available.")));
    }
}
