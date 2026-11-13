package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.auth.dto.OnboardingSessionDto;
import fr.insastronaute.webapi.auth.dto.SessionResponse;
import fr.insastronaute.webapi.auth.dto.SessionUserResponse;
import fr.insastronaute.webapi.auth.dto.SiteStatusDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryAccess;
import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/session")
@Tag(name = "Authentication")
public class SessionController {

    private final UserSynchronizationService userSyncService;
    private final SiteAvailabilityService siteAvailabilityService;
    private final MediaLibraryAccess mediaLibraryAccess;

    public SessionController(
            UserSynchronizationService userSyncService,
            SiteAvailabilityService siteAvailabilityService,
            MediaLibraryAccess mediaLibraryAccess
    ) {
        this.userSyncService = userSyncService;
        this.siteAvailabilityService = siteAvailabilityService;
        this.mediaLibraryAccess = mediaLibraryAccess;
    }

    @GetMapping
    @Operation(summary = "Get session status", description = "Returns whether the browser session is authenticated and, when logged in, the synchronized user profile and roles.")
    public SessionResponse getSession(@AuthenticationPrincipal OidcUser oidcUser) {
        SiteStatusDto siteStatus = SiteStatusDto.from(siteAvailabilityService.currentStatus());

        if (oidcUser == null) {
            return new SessionResponse(false, null, siteStatus, null);
        }

        AppUser appUser = userSyncService.syncWithKeycloak(oidcUser);

        List<String> roles = oidcUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        SessionUserResponse userResponse = new SessionUserResponse(
                appUser.getId(),
                appUser.getKeycloakSub(),
                appUser.getEmail(),
                appUser.getDisplayName(),
                roles,
                appUser.getFirstScan(),
                mediaLibraryAccess.canUse(appUser.getKeycloakSub(), roles)
        );

        return new SessionResponse(true, userResponse, siteStatus, OnboardingSessionDto.from(appUser));
    }
}
