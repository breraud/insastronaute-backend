package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.place.dto.PlaceCensorRequest;
import fr.insastronaute.webapi.place.dto.PlaceCensorResultDto;
import fr.insastronaute.webapi.place.dto.PlacePixelAuthorDto;
import fr.insastronaute.webapi.place.service.PlaceModerationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/place")
@Tag(name = "Admin Place")
public class AdminPlaceModerationController {

    private final PlaceModerationService moderationService;

    public AdminPlaceModerationController(PlaceModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/pixels")
    @Operation(
            summary = "Read who painted a pixel",
            description = "Returns the placement history of one cell, newest first."
    )
    public List<PlacePixelAuthorDto> history(@RequestParam int x, @RequestParam int y) {
        return moderationService.history(x, y);
    }

    @PostMapping("/censor")
    @Operation(
            summary = "Clear an area of the canvas",
            description = "Repaints a rectangle and returns who had painted it, so the sanction does not have to be reconstructed by hand."
    )
    public PlaceCensorResultDto censor(
            @Valid @RequestBody PlaceCensorRequest request,
            @AuthenticationPrincipal OidcUser moderator
    ) {
        return moderationService.censor(request, moderator.getSubject());
    }

    @PostMapping("/bans/{userId}")
    @Operation(summary = "Close the canvas to a player", description = "The rest of the site stays open to them.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable UUID userId, @AuthenticationPrincipal OidcUser moderator) {
        moderationService.setBanned(userId, true, moderator.getSubject());
    }

    @PostMapping("/bans/{userId}/lift")
    @Operation(summary = "Reopen the canvas to a player")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@PathVariable UUID userId, @AuthenticationPrincipal OidcUser moderator) {
        moderationService.setBanned(userId, false, moderator.getSubject());
    }
}
