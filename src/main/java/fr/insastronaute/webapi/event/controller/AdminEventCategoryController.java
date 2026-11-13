package fr.insastronaute.webapi.event.controller;

import fr.insastronaute.webapi.event.dto.EventCategoryDto;
import fr.insastronaute.webapi.event.dto.EventCategoryRequest;
import fr.insastronaute.webapi.event.service.EventService;
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

@RestController
@RequestMapping("/api/admin/event-categories")
@Tag(name = "Admin Event Categories")
public class AdminEventCategoryController {

    private final EventService eventService;

    public AdminEventCategoryController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @Operation(summary = "List event categories", description = "Returns all event categories.")
    public List<EventCategoryDto> listCategories() {
        return eventService.listCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an event category", description = "Creates a reusable event category.")
    public EventCategoryDto createCategory(@Valid @RequestBody EventCategoryRequest request) {
        return eventService.createCategory(request);
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update an event category", description = "Renames an event category and updates its slug.")
    public EventCategoryDto updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody EventCategoryRequest request
    ) {
        return eventService.updateCategory(categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an event category", description = "Deletes an event category.")
    public void deleteCategory(@PathVariable UUID categoryId) {
        eventService.deleteCategory(categoryId);
    }
}
