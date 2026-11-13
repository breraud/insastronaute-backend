package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionPatchDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionSummaryDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibrarySelectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media-library/selection")
@Tag(name = "Media Library")
public class MediaLibrarySelectionController {

    private final MediaLibrarySelectionService selectionService;

    public MediaLibrarySelectionController(MediaLibrarySelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @PatchMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the shared selection", description = "Removes, then adds. Idempotent. All or nothing.")
    public void patch(@Valid @RequestBody MediaSelectionPatchDto patch, @AuthenticationPrincipal OidcUser oidcUser) {
        selectionService.patch(oidcUser.getSubject(), Authorities.of(oidcUser), patch);
    }

    @GetMapping("/summary")
    @Operation(summary = "Selection summary", description = "Available retained media, their weight, and how many became unavailable.")
    public MediaSelectionSummaryDto summary(@AuthenticationPrincipal OidcUser oidcUser) {
        return selectionService.summary(oidcUser.getSubject(), Authorities.of(oidcUser));
    }
}
