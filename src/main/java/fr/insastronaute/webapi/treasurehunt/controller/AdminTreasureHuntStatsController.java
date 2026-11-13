package fr.insastronaute.webapi.treasurehunt.controller;

import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayersResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerStatsResponse;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntStatsService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Les statistiques de la chasse, en lecture seule.
 *
 * Sous /api/admin/treasure-hunt, donc reservees au role ADMIN comme le reste de la
 * chasse : un moderateur qui ouvre la fiche d'un joueur n'y voit pas sa section.
 */
@RestController
@RequestMapping("/api/admin/treasure-hunt/stats")
public class AdminTreasureHuntStatsController {
    private final TreasureHuntStatsService treasureHuntStatsService;

    public AdminTreasureHuntStatsController(TreasureHuntStatsService treasureHuntStatsService) {
        this.treasureHuntStatsService = treasureHuntStatsService;
    }

    @GetMapping("/checkpoints")
    @Operation(summary = "Treasure hunt stats per step", description = "Validations, first and last validation, and median delay since the previous step, for every step of every path. Only players who count in the public rankings are counted: staff are left out.")
    public TreasureHuntCheckpointStatsResponse checkpointStats() {
        return treasureHuntStatsService.checkpointStats();
    }

    @GetMapping("/checkpoints/{checkpointId}/players")
    @Operation(summary = "Players who validated a treasure hunt step", description = "The players who validated a step, in order of arrival, with the delay since their own validation of the previous step. Staff are left out, as in the counts.")
    public TreasureHuntCheckpointPlayersResponse checkpointPlayers(@PathVariable UUID checkpointId) {
        return treasureHuntStatsService.checkpointPlayers(checkpointId);
    }

    @GetMapping("/players/{userId}")
    @Operation(summary = "A player's treasure hunt", description = "Validated steps per path, vault code issue dates and vault opening, without the vault codes themselves.")
    public TreasureHuntPlayerStatsResponse playerStats(@PathVariable UUID userId) {
        return treasureHuntStatsService.playerStats(userId);
    }
}
