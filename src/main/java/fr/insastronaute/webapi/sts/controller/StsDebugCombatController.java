package fr.insastronaute.webapi.sts.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.sts.dto.StsDebugCombatRequest;
import fr.insastronaute.webapi.sts.dto.StsDebugCombatResponse;
import fr.insastronaute.webapi.sts.service.StsRunService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Hand-picked combats, for testing the engine only. Rewrites the caller's own run, so it stays
 * off unless {@code app.sts.debug.combat.enabled} is turned on.
 */
@RestController
@RequestMapping("/api/sts/runs/{runId}/debug/combat")
@ConditionalOnProperty(name = "app.sts.debug.combat.enabled", havingValue = "true")
@Tag(name = "STS Debug")
public class StsDebugCombatController {

    private final UserSynchronizationService synchronizationService;
    private final StsRunService stsRunService;

    public StsDebugCombatController(UserSynchronizationService synchronizationService, StsRunService stsRunService) {
        this.synchronizationService = synchronizationService;
        this.stsRunService = stsRunService;
    }

    @PostMapping
    @Operation(summary = "Start a hand-picked STS combat",
            description = "Replaces the run's player, deck, relics and encounter with the requested ones and builds the authoritative combat.")
    public StsDebugCombatResponse startDebugCombat(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @RequestBody StsDebugCombatRequest request
    ) {
        return stsRunService.startDebugCombat(currentUser(oidcUser), runId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear a hand-picked STS combat", description = "Drops the debug encounter and combat from the run.")
    public void clearDebugCombat(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId
    ) {
        stsRunService.clearDebugCombat(currentUser(oidcUser), runId);
    }

    private AppUser currentUser(OidcUser oidcUser) {
        return synchronizationService.syncWithKeycloak(oidcUser);
    }
}
