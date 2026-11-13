package fr.insastronaute.webapi.sts.controller;

import fr.insastronaute.webapi.sts.dto.StsRunAdminDtos;
import fr.insastronaute.webapi.sts.service.StsRunAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Les runs solo STS vues de l'administration : quels personnages se jouent et comment leurs
 * runs se terminent.
 *
 * <p>Réservé aux administrateurs par défaut, comme {@code /api/admin/**}.
 */
@RestController
@RequestMapping("/api/admin/sts/runs")
@Tag(name = "Admin STS Runs")
public class StsRunAdminController {

    private final StsRunAdminService adminService;

    public StsRunAdminController(StsRunAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/characters/usage")
    @Operation(summary = "STS solo character usage",
            description = "How many runs each character started, and how those runs ended (retired, "
                    + "game over, abandoned, still in progress); days restricts to a recent period.")
    public StsRunAdminDtos.CharacterUsage characterUsage(@RequestParam(required = false) Integer days) {
        return adminService.characterUsage(days);
    }

    @GetMapping("/players/{userId}/characters")
    @Operation(summary = "A player's STS character usage",
            description = "How many runs this player has started with each character.")
    public List<StsRunAdminDtos.PlayerCharacterUsageRow> playerCharacterUsage(@PathVariable UUID userId) {
        return adminService.playerCharacterUsage(userId);
    }
}
