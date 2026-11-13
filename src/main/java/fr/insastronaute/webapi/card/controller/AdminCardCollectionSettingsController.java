package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.dto.CardCollectionSettingsDto;
import fr.insastronaute.webapi.card.dto.CardCollectionSettingsRequest;
import fr.insastronaute.webapi.card.service.CardCollectionSettingsService;
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
@RequestMapping("/api/admin/card-collection-settings")
@Tag(name = "Admin Cards")
public class AdminCardCollectionSettingsController {

    private final CardCollectionSettingsService settingsService;

    public AdminCardCollectionSettingsController(CardCollectionSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @Operation(
            summary = "Read collection point settings",
            description = "Returns the physical multiplier, whether physical duplicates score, and how many tokens one collection point pays."
    )
    public CardCollectionSettingsDto get() {
        return settingsService.get();
    }

    @PutMapping
    @Operation(summary = "Update collection point settings")
    public CardCollectionSettingsDto update(
            @Valid @RequestBody CardCollectionSettingsRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return settingsService.update(request, oidcUser.getSubject());
    }
}
