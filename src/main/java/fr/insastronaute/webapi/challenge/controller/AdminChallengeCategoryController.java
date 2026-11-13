package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ChallengeCategoryDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeCategoryRequest;
import fr.insastronaute.webapi.challenge.service.ChallengeCategoryService;
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

@RestController
@RequestMapping("/api/admin/challenge-categories")
@Tag(name = "Admin Challenge Categories")
public class AdminChallengeCategoryController {

    private final ChallengeCategoryService categoryService;

    public AdminChallengeCategoryController(ChallengeCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List challenge categories", description = "Returns every category available for challenge classification.")
    public List<ChallengeCategoryDto> getAll() {
        return categoryService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a challenge category", description = "Creates a challenge category with its display metadata.")
    public ChallengeCategoryDto create(@Valid @RequestBody ChallengeCategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{name}")
    @Operation(summary = "Update a challenge category", description = "Updates a category selected by its current name.")
    public ChallengeCategoryDto update(
            @PathVariable String name,
            @Valid @RequestBody ChallengeCategoryRequest request
    ) {
        return categoryService.update(name, request);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a challenge category", description = "Deletes a category selected by name.")
    public void delete(@PathVariable String name) {
        categoryService.delete(name);
    }
}
