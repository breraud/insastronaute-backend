package fr.insastronaute.webapi.onboarding.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.onboarding.dto.OnboardingRejectRequest;
import fr.insastronaute.webapi.onboarding.dto.PendingOnboardingDto;
import fr.insastronaute.webapi.onboarding.service.AdminOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/onboarding")
@Tag(name = "Admin Onboarding")
public class AdminOnboardingController {

    private final AdminOnboardingService service;
    private final UserSynchronizationService synchronizationService;

    public AdminOnboardingController(AdminOnboardingService service, UserSynchronizationService synchronizationService) {
        this.service = service;
        this.synchronizationService = synchronizationService;
    }

    @GetMapping("/pending")
    @Operation(summary = "List the validation queue", description = "Accounts waiting for a decision, oldest submission first.")
    public List<PendingOnboardingDto> pending() {
        return service.pending();
    }

    @GetMapping("/rejected")
    @Operation(summary = "List refused accounts", description = "Accounts refused so far, most recently decided first.")
    public List<PendingOnboardingDto> rejected() {
        return service.rejected();
    }

    @PostMapping("/{userId}/validate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Validate an account", description = "Grants the STUDENT realm role, then records the decision.")
    public void validate(@PathVariable UUID userId, @AuthenticationPrincipal OidcUser oidcUser) {
        service.validate(userId, synchronizationService.syncWithKeycloak(oidcUser));
    }

    @PostMapping("/{userId}/validate-limited")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Validate an account as STUDENT_LIMITED", description = "Grants STUDENT_LIMITED, removes STUDENT, marks hidden from leaderboards, and records decision.")
    public void validateLimited(@PathVariable UUID userId, @AuthenticationPrincipal OidcUser oidcUser) {
        service.validateLimited(userId, synchronizationService.syncWithKeycloak(oidcUser));
    }

    @PostMapping("/{userId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject an account", description = "Removes the STUDENT realm role, if any, then records the motive.")
    public void reject(
            @PathVariable UUID userId,
            @Valid @RequestBody OnboardingRejectRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        service.reject(userId, synchronizationService.syncWithKeycloak(oidcUser), request.reason());
    }

    @PostMapping("/{userId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a validated account", description = "Revokes STUDENT, closes application sessions, and records the refusal motive.")
    public void cancel(
            @PathVariable UUID userId,
            @Valid @RequestBody OnboardingRejectRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        service.cancel(userId, synchronizationService.syncWithKeycloak(oidcUser), request.reason());
    }

    @PostMapping("/backfill")
    @Operation(summary = "Deployment catch-up", description = "Grants STUDENT to every account not yet validated. Idempotent.")
    public int backfill() {
        return service.backfill();
    }
}
