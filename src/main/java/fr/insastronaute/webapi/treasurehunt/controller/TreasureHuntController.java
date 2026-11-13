package fr.insastronaute.webapi.treasurehunt.controller;

import fr.insastronaute.webapi.treasurehunt.dto.CheckpointValidationRequest;
import fr.insastronaute.webapi.treasurehunt.dto.CheckpointValidationResponse;
import fr.insastronaute.webapi.treasurehunt.dto.PlayerTreasureHuntProgressResponse;
import fr.insastronaute.webapi.treasurehunt.dto.VaultCodesResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntVisibilityResponse;
import fr.insastronaute.webapi.treasurehunt.dto.VaultUnlockRequest;
import fr.insastronaute.webapi.treasurehunt.dto.ValidatedCheckpointsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.VaultUnlockResponse;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/treasure-hunt")
@Tag(name = "Chasse au tresor")
public class TreasureHuntController {
    private final TreasureHuntService treasureHuntService;

    public TreasureHuntController(TreasureHuntService treasureHuntService) {
        this.treasureHuntService = treasureHuntService;
    }

    @GetMapping("/visibility")
    @Operation(
            summary = "Is the treasure hunt shown to players",
            description = "Tells the player front whether the treasure hunt exists for it at all. A hidden hunt is also closed to play."
    )
    public TreasureHuntVisibilityResponse getVisibility() {
        return new TreasureHuntVisibilityResponse(treasureHuntService.isVisible());
    }

    @PostMapping("/checkpoints/validate")
    @Operation(summary = "Validate a treasure hunt checkpoint")
    // Pas de @Valid ici, a la difference des autres corps : celui-ci ne porte volontairement
    // aucune contrainte, pour qu'un corps vide ou incomplet suive le chemin d'un secret faux.
    // L'annotation ne contraignait donc rien et suggerait le contraire de cette intention.
    public CheckpointValidationResponse validateCheckpoint(
            @RequestBody CheckpointValidationRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return treasureHuntService.validateCheckpoint(oidcUser.getSubject(), request.tool(), request.effectiveSecret(), request.pathId());
    }

    @GetMapping("/vault/codes")
    @Operation(summary = "List the authenticated player's vault codes")
    public VaultCodesResponse getVaultCodes(@AuthenticationPrincipal OidcUser oidcUser) {
        return treasureHuntService.getVaultCodes(oidcUser.getSubject());
    }

    @GetMapping("/checkpoints/validated")
    @Operation(
            summary = "List the checkpoints the authenticated player has already validated",
            description = "Lets a player reread what a step gave him: the code stays on the marker, the reward should not."
    )
    public ValidatedCheckpointsResponse getValidatedCheckpoints(@AuthenticationPrincipal OidcUser oidcUser) {
        return treasureHuntService.getValidatedCheckpoints(oidcUser.getSubject());
    }

    @GetMapping("/progress")
    @Operation(summary = "Get the authenticated player's treasure hunt progress")
    public PlayerTreasureHuntProgressResponse getProgress(@AuthenticationPrincipal OidcUser oidcUser) {
        return treasureHuntService.getProgress(oidcUser.getSubject());
    }

    @PostMapping("/vault/unlock")
    @Operation(summary = "Unlock the final treasure hunt vault")
    public VaultUnlockResponse unlockVault(
            @Valid @RequestBody VaultUnlockRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return treasureHuntService.unlockVault(oidcUser.getSubject(), request.codes());
    }
}
