package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.dto.CardTypeDto;
import fr.insastronaute.webapi.card.dto.CardTypeRequest;
import fr.insastronaute.webapi.card.service.CardTypeService;
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
@RequestMapping("/api/admin/card-types")
public class AdminCardTypeController {
    private final CardTypeService typeService;

    public AdminCardTypeController(CardTypeService typeService) { this.typeService = typeService; }

    @GetMapping
    public List<CardTypeDto> getAll() { return typeService.findAll(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardTypeDto create(@Valid @RequestBody CardTypeRequest request) { return typeService.create(request); }

    @PutMapping("/{id}")
    public CardTypeDto update(@PathVariable UUID id, @Valid @RequestBody CardTypeRequest request) {
        return typeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { typeService.delete(id); }
}
