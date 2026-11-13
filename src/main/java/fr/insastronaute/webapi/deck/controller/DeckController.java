package fr.insastronaute.webapi.deck.controller;

import fr.insastronaute.webapi.deck.dto.DeckDetailDto;
import fr.insastronaute.webapi.deck.dto.DeckSummaryDto;
import fr.insastronaute.webapi.deck.dto.PullResultDto;
import fr.insastronaute.webapi.deck.service.DeckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/decks")
@Tag(name = "Decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    @Operation(summary = "List decks", description = "Returns all purchasable card decks.")
    public List<DeckSummaryDto> getAll() {
        return deckService.findAll();
    }

    @GetMapping("/{deckId}")
    @Operation(summary = "Get deck details", description = "Returns a deck with its dynamic loot table.")
    public DeckDetailDto getById(@PathVariable UUID deckId) {
        return deckService.findById(deckId);
    }

    @PostMapping("/{deckId}/pull")
    @Operation(summary = "Pull a card", description = "Purchases one deck pull and returns the dropped card.")
    public PullResultDto pull(
            @PathVariable UUID deckId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return deckService.pull(deckId, oidcUser.getSubject());
    }
}
