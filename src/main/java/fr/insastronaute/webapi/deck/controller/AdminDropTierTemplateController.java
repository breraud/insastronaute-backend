package fr.insastronaute.webapi.deck.controller;

import fr.insastronaute.webapi.deck.dto.DropTierTemplateDto;
import fr.insastronaute.webapi.deck.dto.DropTierTemplateRequest;
import fr.insastronaute.webapi.deck.service.DropTierTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/**
 * Kept under the deck path so it answers to the same card-admin roles as the
 * packs it feeds: whoever may build a pack's tiers may save and reuse them.
 */
@RestController
@RequestMapping("/api/admin/decks/tier-templates")
@Tag(name = "Admin Decks")
public class AdminDropTierTemplateController {

    private final DropTierTemplateService templateService;

    public AdminDropTierTemplateController(DropTierTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @Operation(
            summary = "List drop tier templates",
            description = "Returns every saved tier arrangement, ready to be poured into a pack."
    )
    public List<DropTierTemplateDto> getAll() {
        return templateService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Save a drop tier template",
            description = "Stores a tier arrangement under a name so another pack can start from it."
    )
    public DropTierTemplateDto create(@Valid @RequestBody DropTierTemplateRequest request) {
        return templateService.create(request);
    }

    @PutMapping("/{templateId}")
    @Operation(
            summary = "Replace a drop tier template",
            description = "Renames a template and replaces its tiers. Packs already created from it are untouched."
    )
    public DropTierTemplateDto update(
            @PathVariable UUID templateId,
            @Valid @RequestBody DropTierTemplateRequest request
    ) {
        return templateService.update(templateId, request);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a drop tier template",
            description = "Removes a saved tier arrangement. Packs created from it keep their tiers."
    )
    public void delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
    }
}
