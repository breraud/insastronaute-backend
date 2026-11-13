package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.AdminCardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardCollectionGrantRequest;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.dto.CardInstanceAssignRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceBatchCreateRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceCreateRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardInstanceUpdateRequest;
import fr.insastronaute.webapi.card.dto.CardOrderUpdateRequest;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/cards")
@Tag(name = "Admin Cards")
public class AdminCardController {

    private final CardService cardService;
    private final CardInstanceService cardInstanceService;

    public AdminCardController(CardService cardService, CardInstanceService cardInstanceService) {
        this.cardService = cardService;
        this.cardInstanceService = cardInstanceService;
    }

    @GetMapping
    @Operation(summary = "List cards", description = "Returns every collectible card configured for the application.")
    public List<CardDto> getAll() {
        return cardService.findAll();
    }

    @GetMapping("/instances")
    @Operation(summary = "Search physical card instances", description = "Returns physical cards with their type, number, public code, owner, and print batch metadata.")
    public Page<AdminCardInstanceDto> searchInstances(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID cardId,
            @RequestParam(required = false) String publicId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer number,
            @RequestParam(required = false) String assignmentStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return cardInstanceService.searchAdmin(search, cardId, publicId, ownerUserId, type, number, assignmentStatus, pageable);
    }

    @GetMapping("/instances/{instanceId}")
    @Operation(summary = "Get physical card instance", description = "Returns one physical card with owner and print batch metadata.")
    public AdminCardInstanceDto getInstance(@PathVariable UUID instanceId) {
        return cardInstanceService.getAdmin(instanceId);
    }

    @PostMapping("/instances")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a physical card instance", description = "Creates one physical card instance from a JSON body, optionally with a public code and owner assignment.")
    public AdminCardInstanceDto createInstance(
            @Valid @RequestBody CardInstanceCreateRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.createAdmin(request, oidcUser.getSubject());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a card", description = "Creates a collectible card and uploads its source image.")
    public CardDto create(
            @RequestParam String name,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean defaultCard,
            @RequestParam(required = false) Integer displayOrder,
            @RequestParam(required = false) Integer firstAcquisitionPoints,
            @RequestParam(required = false) Integer duplicatePoints,
            @RequestParam MultipartFile image,
            @RequestParam(required = false) MultipartFile textlessImage
    ) {
        return cardService.create(
                name, categoryId, category, typeId, enabled, defaultCard, displayOrder,
                firstAcquisitionPoints, duplicatePoints, image, textlessImage
        );
    }

    @PatchMapping(path = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update card cover", description = "Creates or replaces the back cover image used for locked cards.")
    public CardDto updateCover(@RequestParam MultipartFile image) {
        return cardService.updateCover(image);
    }

    @DeleteMapping("/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete card cover", description = "Deletes the back cover image used for locked cards.")
    public void deleteCover() {
        cardService.deleteCover();
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update a card", description = "Updates card metadata and optionally replaces the card image.")
    public CardDto update(
            @PathVariable UUID id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false, defaultValue = "false") boolean clearType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean defaultCard,
            @RequestParam(required = false) Integer displayOrder,
            @RequestParam(required = false) Integer firstAcquisitionPoints,
            @RequestParam(required = false) Integer duplicatePoints,
            @RequestParam(required = false, defaultValue = "false") boolean clearFirstAcquisitionPoints,
            @RequestParam(required = false, defaultValue = "false") boolean clearDuplicatePoints,
            @RequestParam(required = false) MultipartFile image
    ) {
        return cardService.update(
                id, name, categoryId, category, typeId, clearType, enabled, defaultCard, displayOrder,
                firstAcquisitionPoints, duplicatePoints, clearFirstAcquisitionPoints, clearDuplicatePoints, image
        );
    }

    @PatchMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update card image", description = "Replaces only the selected card image.")
    public CardDto updateImage(
            @PathVariable UUID id,
            @RequestParam MultipartFile image
    ) {
        return cardService.updateImage(id, image);
    }

    @PatchMapping(path = "/{id}/image/textless", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update card textless image", description = "Replaces only the selected card textless image.")
    public CardDto updateTextlessImage(
            @PathVariable UUID id,
            @RequestParam MultipartFile image
    ) {
        return cardService.updateTextlessImage(id, image);
    }

    @DeleteMapping("/{id}/image/textless")
    @Operation(summary = "Delete card textless image", description = "Deletes the selected card textless image.")
    public CardDto deleteTextlessImage(@PathVariable UUID id) {
        return cardService.deleteTextlessImage(id);
    }

    @PutMapping("/categories/{categoryId}/order")
    @Operation(summary = "Reorder cards", description = "Replaces card display order inside one category.")
    public List<CardDto> reorder(
            @PathVariable UUID categoryId,
            @Valid @RequestBody CardOrderUpdateRequest request
    ) {
        return cardService.reorder(categoryId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a card", description = "Deletes a collectible card by id.")
    public void delete(@PathVariable UUID id) {
        cardService.delete(id);
    }

    @PostMapping("/{id}/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Grant a card", description = "Adds the selected card to one or more player decks.")
    public CardDeckCardDto grant(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "VIRTUAL") CardCollectionType collectionType,
            @Valid @RequestBody CardCollectionGrantRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardService.grant(id, collectionType, request, oidcUser.getSubject());
    }

    @GetMapping("/{id}/instances")
    @Operation(summary = "List physical card instances", description = "Returns physical instances created for the selected card.")
    public List<CardInstanceDto> getInstances(@PathVariable UUID id) {
        return cardInstanceService.findByCard(id);
    }

    @PostMapping("/{id}/instances")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a physical card instance", description = "Creates one claimable physical instance for the selected card.")
    public CardInstanceDto createInstance(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.create(id, oidcUser.getSubject());
    }

    @PostMapping("/{id}/instances/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create physical card instances", description = "Creates a batch of claimable physical instances for the selected card.")
    public List<CardInstanceDto> createInstances(
            @PathVariable UUID id,
            @Valid @RequestBody CardInstanceBatchCreateRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.createBatch(id, request.quantity(), oidcUser.getSubject());
    }

    @PostMapping("/instances/{instanceId}/assignment")
    @Operation(summary = "Assign a physical card instance", description = "Assigns one physical card instance to a player.")
    public CardInstanceDto assignInstance(
            @PathVariable UUID instanceId,
            @Valid @RequestBody CardInstanceAssignRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.assign(instanceId, request.userId(), oidcUser.getSubject());
    }

    @DeleteMapping("/instances/{instanceId}/assignment")
    @Operation(summary = "Release a physical card instance", description = "Removes the player assignment from one physical card instance.")
    public CardInstanceDto releaseInstance(
            @PathVariable UUID instanceId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.release(instanceId, oidcUser.getSubject());
    }

    @PatchMapping("/instances/{instanceId}")
    @Operation(summary = "Update a physical card instance", description = "Updates card type/number by changing its card definition, public code, or owner assignment.")
    public AdminCardInstanceDto updateInstance(
            @PathVariable UUID instanceId,
            @Valid @RequestBody CardInstanceUpdateRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.updateAdmin(instanceId, request, oidcUser.getSubject());
    }

    @PutMapping("/instances/{instanceId}")
    @Operation(summary = "Replace physical card instance fields", description = "Updates card type/number by changing its card definition, public code, or owner assignment.")
    public AdminCardInstanceDto replaceInstance(
            @PathVariable UUID instanceId,
            @Valid @RequestBody CardInstanceUpdateRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return cardInstanceService.updateAdmin(instanceId, request, oidcUser.getSubject());
    }

    @DeleteMapping("/instances/{instanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a physical card instance", description = "Deletes one physical card instance and removes its collection attribution if needed.")
    public void deleteInstance(
            @PathVariable UUID instanceId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        cardInstanceService.delete(instanceId, oidcUser.getSubject());
    }
}
