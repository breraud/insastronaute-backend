package fr.insastronaute.webapi.site.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.site.dto.SiteAvailabilityDto;
import fr.insastronaute.webapi.site.dto.SiteAvailabilityUpdateRequest;
import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/site-availability")
@Tag(name = "Admin Site")
public class AdminSiteAvailabilityController {

    private final SiteAvailabilityService availabilityService;
    private final UserSynchronizationService synchronizationService;

    public AdminSiteAvailabilityController(
            SiteAvailabilityService availabilityService,
            UserSynchronizationService synchronizationService
    ) {
        this.availabilityService = availabilityService;
        this.synchronizationService = synchronizationService;
    }

    @GetMapping
    @Operation(summary = "Get site availability", description = "Returns whether the player front is open and the message shown when it is closed.")
    public SiteAvailabilityDto get() {
        return availabilityService.get();
    }

    @PutMapping
    @Operation(summary = "Update site availability", description = "Opens or closes the player front and updates the message shown to players.")
    public SiteAvailabilityDto update(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody SiteAvailabilityUpdateRequest request
    ) {
        return availabilityService.update(request, synchronizationService.syncWithKeycloak(oidcUser));
    }
}
