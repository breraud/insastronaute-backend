package fr.insastronaute.webapi.sts.combat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatSnapshotDto;
import fr.insastronaute.webapi.sts.service.StsRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/sts/combats")
public class CombatSnapshotController {

    private final StsRunService runService;
    private final ObjectMapper objectMapper;

    public CombatSnapshotController(StsRunService runService, ObjectMapper objectMapper) {
        this.runService = runService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{combatId}")
    public CombatSnapshotDto getSnapshot(@PathVariable String combatId, Principal principal) {
        UUID runId = UUID.fromString(combatId);
        var combat = runService.authoritativeCombatSnapshot(principal.getName(), runId);
        var revision = combat.get("revision");
        if (revision == null || !revision.canConvertToLong() || revision.longValue() < 0) {
            throw new IllegalStateException("Invalid persisted combat revision");
        }
        return new CombatSnapshotDto(
                1,
                combatId,
                revision.asText(),
                "PVE",
                objectMapper.convertValue(combat, java.util.Map.class));
    }
}
