package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.dto.CardCategoryDto;
import fr.insastronaute.webapi.card.dto.CardCategoryRequest;
import fr.insastronaute.webapi.card.service.CardCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/card-categories")
@Tag(name = "Admin Card Categories")
public class AdminCardCategoryController {

    private final CardCategoryService categoryService;

    public AdminCardCategoryController(CardCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List card categories", description = "Returns every category available for card classification.")
    public List<CardCategoryDto> getAll() {
        return categoryService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a card category", description = "Creates a card category.")
    public CardCategoryDto create(@Valid @RequestBody CardCategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a card category", description = "Updates a card category by id.")
    public CardCategoryDto update(
            @PathVariable UUID id,
            @Valid @RequestBody CardCategoryRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return categoryService.update(id, request, oidcUser.getSubject());
    }

    @PatchMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update a card category image", description = "Creates or replaces the public image for a card category.")
    public CardCategoryDto updateImage(
            @PathVariable UUID id,
            @RequestParam MultipartFile image
    ) {
        return categoryService.updateImage(id, image);
    }

    @DeleteMapping("/{id}/image")
    @Operation(summary = "Delete a card category image", description = "Removes the public image for a card category.")
    public CardCategoryDto deleteImage(@PathVariable UUID id) {
        return categoryService.deleteImage(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a card category", description = "Deletes a card category by id.")
    public void delete(@PathVariable UUID id) {
        categoryService.delete(id);
    }
}
