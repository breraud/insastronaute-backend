package fr.insastronaute.webapi.sts.multiplayer.controller;

import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpAdminDtos;
import fr.insastronaute.webapi.sts.multiplayer.service.StsPvpAdminService;
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
 * Le multijoueur vu de l'administration : de quoi repérer ce qui demande un équilibrage.
 *
 * <p>Réservé aux administrateurs, sans règle dédiée : {@code /api/admin/**} leur revient par
 * défaut dans la configuration de sécurité. C'est le bon public — la fiche d'un joueur expose son
 * adresse et ses decks, que ses adversaires n'ont pas à connaître.
 */
@RestController
@RequestMapping("/api/admin/sts/pvp")
@Tag(name = "Admin STS PVP")
public class StsPvpAdminController {

    private final StsPvpAdminService adminService;

    public StsPvpAdminController(StsPvpAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/characters/usage")
    @Operation(summary = "PvP character usage",
            description = "Picks, share and win rate of each character over finished battles. Raids are "
                    + "excluded unless mode=RAID is requested; days restricts to a recent period.")
    public StsPvpAdminDtos.CharacterUsage characterUsage(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "true") boolean includeFriendly
    ) {
        return adminService.characterUsage(days, mode, includeFriendly);
    }

    @GetMapping("/players")
    @Operation(summary = "Top rated PvP players",
            description = "Players who played at least one ranked duel, best rating first, with their "
                    + "rank on the public Elo leaderboard when they appear on it.")
    public List<StsPvpAdminDtos.PlayerSummary> players(@RequestParam(required = false) Integer limit) {
        return adminService.topPlayers(limit);
    }

    @GetMapping("/players/{userId}")
    @Operation(summary = "PvP player detail",
            description = "A player's rating, record, selected character and every saved deck, with the "
                    + "deck a duel would actually use today.")
    public StsPvpAdminDtos.PlayerDetail player(@PathVariable UUID userId) {
        return adminService.player(userId);
    }
}
