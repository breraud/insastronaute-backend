package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardType;
import fr.insastronaute.webapi.card.dto.CardTypeDto;
import fr.insastronaute.webapi.card.dto.CardTypeRequest;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CardTypeService {
    private final CardTypeRepository typeRepository;
    private final CardRepository cardRepository;

    public CardTypeService(CardTypeRepository typeRepository, CardRepository cardRepository) {
        this.typeRepository = typeRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public List<CardTypeDto> findAll() {
        return typeRepository.findAll().stream()
                .sorted(Comparator.comparing(CardType::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto).toList();
    }

    @Transactional
    public CardTypeDto create(CardTypeRequest request) {
        String name = normalizeName(request.name());
        if (typeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ce type de carte existe déjà.");
        }
        CardType type = new CardType();
        type.setName(name);
        return toDto(typeRepository.save(type));
    }

    @Transactional
    public CardTypeDto update(UUID id, CardTypeRequest request) {
        CardType type = find(id);
        String name = normalizeName(request.name());
        if (typeRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id)).isPresent()) {
            throw new ConflictException("Ce type de carte existe déjà.");
        }
        type.setName(name);
        return toDto(typeRepository.save(type));
    }

    @Transactional
    public void delete(UUID id) {
        CardType type = find(id);
        List<Card> cards = cardRepository.findByTypeRef_Id(id);
        cards.forEach(card -> card.setTypeRef(null));
        cardRepository.saveAll(cards);
        typeRepository.delete(type);
    }

    private CardType find(UUID id) {
        return typeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Type de carte introuvable."));
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) throw new BadRequestException("Le nom du type est requis.");
        String normalized = name.trim();
        if (normalized.length() > 50) throw new BadRequestException("Le nom du type doit contenir au plus 50 caractères.");
        return normalized;
    }

    private CardTypeDto toDto(CardType type) {
        return new CardTypeDto(type.getId(), type.getName(), type.getCreatedAt(), type.getUpdatedAt());
    }
}
