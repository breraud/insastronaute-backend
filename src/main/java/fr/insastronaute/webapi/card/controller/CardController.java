package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardGrouping;
import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.dto.CardInstanceClaimRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceDto;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.common.security.roles.StudentLimitedAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import fr.insastronaute.webapi.card.dto.CardInstanceReleaseRequest;
import fr.insastronaute.webapi.card.dto.OwnedCardInstanceDto;
import fr.insastronaute.webapi.card.dto.ReleasedCardInstanceDto;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards")
public class CardController {

    private final CardService cardService;
    private final CardInstanceService cardInstanceService;

    public CardController(CardService cardService, CardInstanceService cardInstanceService) {
        this.cardService = cardService;
        this.cardInstanceService = cardInstanceService;
    }

    @GetMapping("/deck")
    @Operation(summary = "Get my card deck", description = "Returns the authenticated player's collectible card deck.")
    public CardDeckDto getMyDeck(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(required = false, defaultValue = "VIRTUAL") CardCollectionType collectionType,
            @RequestParam(required = false, defaultValue = "rank") String groupBy,
            @RequestParam(required = false, defaultValue = "PERSONAL") CardCollectionScope scope
    ) {
        if (collectionType == CardCollectionType.PHYSICAL || scope == CardCollectionScope.TEAM) {
            StudentLimitedAccess.requireAllowed(oidcUser.getAuthorities());
        }
        return cardService.getDeck(oidcUser.getSubject(), collectionType, CardGrouping.from(groupBy), scope);
    }

    @GetMapping("/cover")
    @Operation(summary = "Get card cover", description = "Returns the back cover image used for locked collectible cards.")
    public CardDto getCover() {
        return cardService.getCover();
    }

    @PostMapping("/instances/claim")
    @Operation(summary = "Claim a physical card", description = "Adds a claimable physical card instance to the authenticated player's collection.")
    public CardInstanceDto claimPhysicalCard(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody CardInstanceClaimRequest request
    ) {
        StudentLimitedAccess.requireAllowed(oidcUser.getAuthorities());
        return cardInstanceService.claim(oidcUser.getSubject(), request.publicId());
    }

    @GetMapping("/{cardId}/instances/mine")
    @Operation(summary = "List my physical copies of a card", description = "Returns the printed codes of the authenticated player's physical copies of a card, so they can pick the one to release.")
    public List<OwnedCardInstanceDto> listMyPhysicalCopies(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID cardId
    ) {
        StudentLimitedAccess.requireAllowed(oidcUser.getAuthorities());
        return cardInstanceService.findMine(oidcUser.getSubject(), cardId);
    }

    @PostMapping("/instances/release")
    @Operation(summary = "Release a physical duplicate", description = "Gives up the authenticated player's ownership of one of their duplicate physical copies; whoever scans its code next claims it. The last copy of a card cannot be released.")
    public ReleasedCardInstanceDto releasePhysicalDuplicate(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody CardInstanceReleaseRequest request
    ) {
        StudentLimitedAccess.requireAllowed(oidcUser.getAuthorities());
        return cardInstanceService.releaseMine(oidcUser.getSubject(), request.publicId());
    }
}
