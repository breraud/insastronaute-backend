package fr.insastronaute.webapi.sts.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.sts.dto.StsClaimRewardRequest;
import fr.insastronaute.webapi.sts.dto.StsClaimRewardResponse;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionRequest;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionResponse;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandRequest;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandResponse;
import fr.insastronaute.webapi.sts.dto.StsCombatStateResponse;
import fr.insastronaute.webapi.sts.dto.StsCompleteNodeRequest;
import fr.insastronaute.webapi.sts.dto.StsRestActionRequest;
import fr.insastronaute.webapi.sts.dto.StsRestActionResponse;
import fr.insastronaute.webapi.sts.dto.StsCompleteNodeResponse;
import fr.insastronaute.webapi.sts.dto.StsContinueAfterRetreatRequest;
import fr.insastronaute.webapi.sts.dto.StsCurrentRunResponse;
import fr.insastronaute.webapi.sts.dto.StsEnterNodeRequest;
import fr.insastronaute.webapi.sts.dto.StsEnterNodeResponse;
import fr.insastronaute.webapi.sts.dto.StsRunStateDto;
import fr.insastronaute.webapi.sts.dto.StsRetireRunResponse;
import fr.insastronaute.webapi.sts.dto.StsRetreatPreviewResponse;
import fr.insastronaute.webapi.sts.dto.StsStartRunRequest;
import fr.insastronaute.webapi.sts.service.StsRunService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sts/runs")
@Tag(name = "STS Runs")
public class StsRunController {

    private final UserSynchronizationService synchronizationService;
    private final StsRunService stsRunService;

    public StsRunController(UserSynchronizationService synchronizationService, StsRunService stsRunService) {
        this.synchronizationService = synchronizationService;
        this.stsRunService = stsRunService;
    }

    @PostMapping
        @Operation(summary = "Start or resume an STS run", description = "Returns the active run or creates a backend-authoritative Stage1 run for the authenticated user.")
    public StsRunStateDto startRun(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody StsStartRunRequest request
    ) {
        return stsRunService.startRun(currentUser(oidcUser), request);
    }

    @GetMapping("/current")
    @Operation(summary = "Get current STS run", description = "Returns the active STS run for the authenticated user, if any.")
    public StsCurrentRunResponse currentRun(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsRunService.currentRun(currentUser(oidcUser));
    }

    @PostMapping("/{runId}/nodes/{nodeId}/enter")
    @Operation(summary = "Enter an STS map node", description = "Marks a reachable node as in progress and creates an active encounter when needed.")
    public StsEnterNodeResponse enterNode(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @PathVariable int nodeId,
            @RequestBody(required = false) StsEnterNodeRequest request
    ) {
        return stsRunService.enterNode(currentUser(oidcUser), runId, nodeId, request);
    }

    @PostMapping("/{runId}/nodes/{nodeId}/complete")
    @Operation(summary = "Complete an STS map node", description = "Validates the active node result, advances the run, and creates server-side rewards.")
    public StsCompleteNodeResponse completeNode(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @PathVariable int nodeId,
            @RequestBody(required = false) StsCompleteNodeRequest request
    ) {
        return stsRunService.completeNode(currentUser(oidcUser), runId, nodeId, request);
    }

    @PostMapping("/{runId}/rewards/{rewardId}/claim")
    @Operation(summary = "Claim an STS reward", description = "Applies a pending reward to the run inventory.")
    public StsClaimRewardResponse claimReward(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @PathVariable String rewardId,
            @RequestBody(required = false) StsClaimRewardRequest request
    ) {
        return stsRunService.claimReward(currentUser(oidcUser), runId, rewardId, request);
    }

    @GetMapping("/{runId}/combat")
    @Operation(summary = "Get STS combat state", description = "Returns the backend-authoritative STS combat state for the active encounter, when available.")
    public StsCombatStateResponse combatState(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId
    ) {
        return stsRunService.combatState(currentUser(oidcUser), runId);
    }

    @PostMapping("/{runId}/combat/commands")
    @Operation(summary = "Submit STS combat command", description = "Validates and applies one backend-authoritative STS combat command, then advances AI turns.")
    public StsCombatCommandResponse submitCombatCommand(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @RequestBody StsCombatCommandRequest request
    ) {
        return stsRunService.submitCombatCommand(currentUser(oidcUser), runId, request);
    }
    

    @PostMapping("/{runId}/events/{eventInstanceId}/choose")
    @Operation(summary = "Choose an STS event option", description = "Applies a backend-authoritative event option and completes the active event node.")
    public StsChooseEventOptionResponse chooseEventOption(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @PathVariable String eventInstanceId,
            @RequestBody(required = false) StsChooseEventOptionRequest request
    ) {
        return stsRunService.chooseEventOption(currentUser(oidcUser), runId, eventInstanceId, request);
    }

    @PostMapping("/{runId}/nodes/{nodeId}/rest")
    @Operation(summary = "Act on an STS rest site", description = "Applies a backend-authoritative heal or enchantment at a campfire.")
    public StsRestActionResponse rest(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @PathVariable int nodeId,
            @RequestBody(required = false) StsRestActionRequest request
    ) {
        return stsRunService.rest(currentUser(oidcUser), runId, nodeId, request);
    }

    @PostMapping("/{runId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset an STS run", description = "Stops the active owned run and marks it as abandoned.")
    public void resetRun(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId
    ) {
        stsRunService.resetRun(currentUser(oidcUser), runId);
    }

    @PostMapping("/{runId}/retire")
    @Operation(summary = "Retire an STS run", description = "Ends an owned run after a completed boss and grants the server-calculated token reward.")
    public StsRetireRunResponse retireRun(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId
    ) {
        return stsRunService.retireRun(currentUser(oidcUser), runId);
    }

    @GetMapping("/{runId}/retreat-preview")
    @Operation(summary = "Preview STS retreat score", description = "Computes the current server-side score and token preview without retiring the run.")
    public StsRetreatPreviewResponse retreatPreview(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId
    ) {
        return stsRunService.retreatPreview(currentUser(oidcUser), runId);
    }

    @PostMapping("/{runId}/retreat/continue")
    @Operation(summary = "Continue an STS run after retreat", description = "Advances the act, regenerates the authoritative map, and returns the refreshed run state.")
    public StsRunStateDto continueAfterRetreat(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID runId,
            @Valid @RequestBody StsContinueAfterRetreatRequest request
    ) {
        return stsRunService.continueAfterRetreat(currentUser(oidcUser), runId, request.expectedAct());
    }

    private AppUser currentUser(OidcUser oidcUser) {
        return synchronizationService.syncWithKeycloak(oidcUser);
    }
}
