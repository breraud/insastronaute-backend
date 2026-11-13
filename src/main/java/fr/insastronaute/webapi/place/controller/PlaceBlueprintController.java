package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.place.dto.PlaceBlueprintAnchorDto;
import fr.insastronaute.webapi.place.dto.PlaceBlueprintDto;
import fr.insastronaute.webapi.place.dto.PlaceBlueprintSaveDto;
import fr.insastronaute.webapi.place.dto.PlaceBlueprintShareDto;
import fr.insastronaute.webapi.place.service.PlaceBlueprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/place/blueprints")
@Tag(name = "Place")
public class PlaceBlueprintController {

    private final PlaceBlueprintService blueprintService;

    public PlaceBlueprintController(PlaceBlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @GetMapping
    @Operation(
            summary = "List the blueprints a player can use",
            description = "Returns the player's own blueprints first, then the ones friends shared with them.")
    public List<PlaceBlueprintDto> list(@AuthenticationPrincipal OidcUser oidcUser) {
        return blueprintService.listVisible(oidcUser.getSubject());
    }

    @PostMapping
    @Operation(summary = "Create a blueprint")
    public PlaceBlueprintDto create(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody PlaceBlueprintSaveDto dto
    ) {
        return blueprintService.create(oidcUser.getSubject(), dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a blueprint the player owns")
    public PlaceBlueprintDto update(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID id,
            @Valid @RequestBody PlaceBlueprintSaveDto dto
    ) {
        return blueprintService.update(oidcUser.getSubject(), id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a blueprint, or drop a shared one",
            description = "The owner deletes it for everyone; a recipient only removes their own access.")
    public void delete(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID id) {
        blueprintService.delete(oidcUser.getSubject(), id);
    }

    @PutMapping("/{id}/anchor")
    @Operation(
            summary = "Set where a blueprint sits on the canvas",
            description = "Owner only. The position travels with the blueprint to every friend it is shared with.")
    public PlaceBlueprintDto setAnchor(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID id,
            @Valid @RequestBody PlaceBlueprintAnchorDto dto
    ) {
        return blueprintService.setAnchor(oidcUser.getSubject(), id, dto.x(), dto.y());
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Share a blueprint with friends")
    public PlaceBlueprintDto share(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID id,
            @Valid @RequestBody PlaceBlueprintShareDto dto
    ) {
        return blueprintService.share(oidcUser.getSubject(), id, dto.userIds());
    }
}
