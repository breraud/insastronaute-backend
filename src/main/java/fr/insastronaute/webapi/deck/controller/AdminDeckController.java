package fr.insastronaute.webapi.deck.controller;

import fr.insastronaute.webapi.deck.dto.DeckDetailDto;
import fr.insastronaute.webapi.deck.dto.DropTierOrderUpdateRequest;
import fr.insastronaute.webapi.deck.service.DeckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;


@RestController
@RequestMapping("/api/admin/decks")
@Tag(name = "Admin Decks")
public class AdminDeckController {

    private final DeckService deckService;

    public AdminDeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a deck", description = "Creates a purchasable card deck with dynamic drop tiers.")
    public DeckDetailDto create(
            @RequestPart("image") MultipartFile image,
            @RequestPart("deckData") String deckData
    ) {
        return deckService.create(deckData, image);
    }

    @PatchMapping(path = "/{deckId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update a deck", description = "Updates a purchasable card deck and optionally replaces its image.")
    public DeckDetailDto update(
            @PathVariable UUID deckId,
            @RequestPart("deckData") String deckData,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return deckService.update(deckId, deckData, image);
    }

    @DeleteMapping("/{deckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a deck",
            description = "Removes a purchasable deck and its drop tiers. Cards already pulled from it are untouched: "
                    + "a collection keeps no trace of the deck a card came from."
    )
    public void delete(@PathVariable UUID deckId) {
        deckService.delete(deckId);
    }

    @PutMapping("/{deckId}/drop-tiers/order")
    @Operation(summary = "Reorder deck drop tiers", description = "Replaces the visual display order of every drop tier in one deck.")
    public DeckDetailDto reorderDropTiers(
            @PathVariable UUID deckId,
            @Valid @RequestBody DropTierOrderUpdateRequest request
    ) {
        return deckService.reorderDropTiers(deckId, request);
    }
}
