package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.dto.MediaLibraryGrantDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/media-library/grants")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Media Library")
public class AdminMediaLibraryGrantController {

    private final MediaLibraryGrantService grantService;

    public AdminMediaLibraryGrantController(MediaLibraryGrantService grantService) {
        this.grantService = grantService;
    }

    @GetMapping
    @Operation(summary = "List media library grants", description = "Staff members an administrator opened the media library to.")
    public List<MediaLibraryGrantDto> list() {
        return grantService.list();
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Grant media library access", description = "Idempotent. Only staff members can be designated.")
    public void grant(@PathVariable UUID userId, @AuthenticationPrincipal OidcUser oidcUser) {
        grantService.grant(userId, oidcUser.getSubject());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke media library access", description = "Idempotent. Takes effect on the next request.")
    public void revoke(@PathVariable UUID userId, @AuthenticationPrincipal OidcUser oidcUser) {
        grantService.revoke(userId, oidcUser.getSubject());
    }
}
