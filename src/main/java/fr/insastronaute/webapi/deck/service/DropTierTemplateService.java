package fr.insastronaute.webapi.deck.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.deck.domain.DropTierTemplate;
import fr.insastronaute.webapi.deck.domain.DropTierTemplateTier;
import fr.insastronaute.webapi.deck.dto.DropTierCreateRequest;
import fr.insastronaute.webapi.deck.dto.DropTierDto;
import fr.insastronaute.webapi.deck.dto.DropTierTemplateDto;
import fr.insastronaute.webapi.deck.dto.DropTierTemplateRequest;
import fr.insastronaute.webapi.deck.repository.DropTierTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saved tier arrangements, reusable across packs.
 *
 * Applying a template is deliberately not a write: the admin loads it into the
 * pack form and stays free to adjust before saving. A template therefore never
 * points at a pack, and a pack never remembers the template it came from —
 * editing one has no effect on the other, which is what makes them safe to reuse.
 */
@Service
public class DropTierTemplateService {

    private final DropTierTemplateRepository templateRepository;
    private final CardRepository cardRepository;

    public DropTierTemplateService(DropTierTemplateRepository templateRepository, CardRepository cardRepository) {
        this.templateRepository = templateRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public List<DropTierTemplateDto> findAll() {
        return templateRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DropTierTemplateDto create(DropTierTemplateRequest request) {
        String name = request.name().trim();
        if (templateRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Un modèle porte déjà ce nom.");
        }
        DropTierTemplate template = new DropTierTemplate();
        template.setId(UUID.randomUUID());
        apply(template, name, request);
        return toDto(templateRepository.save(template));
    }

    @Transactional
    public DropTierTemplateDto update(UUID templateId, DropTierTemplateRequest request) {
        DropTierTemplate template = templateRepository.findWithTiersById(templateId)
                .orElseThrow(() -> new NotFoundException("Modèle de paliers introuvable."));
        String name = request.name().trim();
        templateRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(templateId))
                .ifPresent(existing -> {
                    throw new ConflictException("Un modèle porte déjà ce nom.");
                });
        apply(template, name, request);
        return toDto(templateRepository.save(template));
    }

    @Transactional
    public void delete(UUID templateId) {
        DropTierTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Modèle de paliers introuvable."));
        templateRepository.delete(template);
    }

    private void apply(DropTierTemplate template, String name, DropTierTemplateRequest request) {
        int cardsPerPull = DropTierRules.resolveCardsPerPull(request.cardsPerPull());
        DropTierRules.validateTiers(cardsPerPull, request.dropTiers());
        Map<UUID, Card> cardsById = DropTierRules.loadCards(cardRepository, request.dropTiers());

        template.setName(name);
        template.setCardsPerPull(cardsPerPull);

        List<DropTierTemplateTier> tiers = new ArrayList<>();
        for (int index = 0; index < request.dropTiers().size(); index++) {
            DropTierCreateRequest tierRequest = request.dropTiers().get(index);
            DropTierTemplateTier tier = new DropTierTemplateTier();
            tier.setLabel(DropTierRules.normalizeLabel(tierRequest.label()));
            tier.setDropChancePercent(tierRequest.dropChancePercent());
            tier.setMaxCardsPerPull(tierRequest.maxCardsPerPull());
            tier.setMinCardsPerPull(tierRequest.minCardsPerPull());
            tier.setDisplayOrder(index);
            tier.setCards(tierRequest.cardIds().stream().map(cardsById::get).toList());
            tiers.add(tier);
        }
        template.setTiers(tiers);
    }

    private DropTierTemplateDto toDto(DropTierTemplate template) {
        return new DropTierTemplateDto(
                template.getId(),
                template.getName(),
                template.getCardsPerPull(),
                template.getTiers().stream()
                        .sorted(Comparator.comparing(DropTierTemplateTier::getDisplayOrder))
                        // Un modèle porte son propre nombre de cartes par tirage :
                        // sans lui, la rareté annoncée ne voudrait rien dire.
                        .map(tier -> toTierDto(tier, templateFreeSlots(template)))
                        .toList(),
                template.getUpdatedAt()
        );
    }

    private DropTierDto toTierDto(DropTierTemplateTier tier, int freeSlots) {
        double probability = DropTierRules.pullProbability(
                tier.getDropChancePercent() == null ? 0 : tier.getDropChancePercent().doubleValue(),
                freeSlots,
                tier.getMinCardsPerPull()
        );
        double expectedPulls = DropTierRules.expectedPullsForOne(probability);
        return new DropTierDto(
                tier.getId(),
                tier.getLabel(),
                tier.getDropChancePercent(),
                tier.getMaxCardsPerPull(),
                tier.getMinCardsPerPull(),
                tier.getCards().stream().map(this::toCardDto).toList(),
                Math.round(probability * 10_000.0) / 100.0,
                Double.isInfinite(expectedPulls) ? null : Math.round(expectedPulls * 100.0) / 100.0,
                DropTierRules.rarityOf(expectedPulls)
        );
    }

    private static int templateFreeSlots(DropTierTemplate template) {
        int guaranteed = template.getTiers().stream()
                .map(DropTierTemplateTier::getMinCardsPerPull)
                .mapToInt(DropTierRules::resolveMinCardsPerPull)
                .sum();
        return DropTierRules.freeSlots(
                template.getCardsPerPull() == null ? 1 : template.getCardsPerPull(), guaranteed);
    }

    private CardDto toCardDto(Card card) {
        return new CardDto(
                card.getId(),
                card.getName(),
                card.getCategoryId(),
                card.getCategory(),
                card.isEnabled(),
                card.isDefaultCard(),
                card.getDisplayOrder(),
                card.getImageUrl(),
                card.getTextlessImageUrl(),
                card.getThumbnailUrl(),
                card.getContentType(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
