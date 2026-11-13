package fr.insastronaute.webapi.sts.combat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.sts.service.StsRunService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatSnapshotControllerTest {

    private final StsRunService runService = mock(StsRunService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CombatSnapshotController controller = new CombatSnapshotController(runService, objectMapper);
    private final Principal principal = () -> "owner-sub";

    @Test
    void returnsPersistedCombatWithItsAuthoritativeRevision() {
        UUID runId = UUID.randomUUID();
        var combat = objectMapper.createObjectNode()
                .put("combatId", "engine-combat")
                .put("revision", 42)
                .put("status", "ACTIVE");
        when(runService.authoritativeCombatSnapshot("owner-sub", runId)).thenReturn(combat);

        var snapshot = controller.getSnapshot(runId.toString(), principal);

        assertThat(snapshot.protocolVersion()).isEqualTo(1);
        assertThat(snapshot.combatId()).isEqualTo(runId.toString());
        assertThat(snapshot.revision()).isEqualTo("42");
        assertThat(snapshot.mode()).isEqualTo("PVE");
        assertThat(snapshot.payload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) snapshot.payload()).get("combatId")).isEqualTo("engine-combat");
        verify(runService).authoritativeCombatSnapshot("owner-sub", runId);
    }

    @Test
    void hidesRunsNotOwnedByAuthenticatedUser() {
        UUID runId = UUID.randomUUID();
        when(runService.authoritativeCombatSnapshot("owner-sub", runId))
                .thenThrow(new NotFoundException("Run STS introuvable."));

        assertThatThrownBy(() -> controller.getSnapshot(runId.toString(), principal))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsRunsWithoutAnActiveCombat() {
        UUID runId = UUID.randomUUID();
        when(runService.authoritativeCombatSnapshot("owner-sub", runId))
                .thenThrow(new ConflictException("Aucun combat STS authoritative actif."));

        assertThatThrownBy(() -> controller.getSnapshot(runId.toString(), principal))
                .isInstanceOf(ConflictException.class);
    }

}
