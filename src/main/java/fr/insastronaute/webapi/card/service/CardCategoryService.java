package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.dto.CardCategoryDto;
import fr.insastronaute.webapi.card.dto.CardCategoryRequest;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CardCategoryService {

    private final CardCategoryRepository categoryRepository;
    private final CardRepository cardRepository;
    private final CardCollectionRepository collectionRepository;
    private final CardImageService imageService;
    private final AdminActionJournal journal;

    public CardCategoryService(
            CardCategoryRepository categoryRepository,
            CardRepository cardRepository,
            CardCollectionRepository collectionRepository,
            CardImageService imageService,
            AdminActionJournal journal
    ) {
        this.categoryRepository = categoryRepository;
        this.cardRepository = cardRepository;
        this.collectionRepository = collectionRepository;
        this.imageService = imageService;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<CardCategoryDto> findAll() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(CardCategory::getDisplayOrder)
                        .thenComparing(CardCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    /**
     * Creer une categorie n'ecrit rien au registre : une categorie neuve ne
     * contient aucune carte, donc aucun joueur n'en detient et aucun score de
     * collection ne bouge. Le bareme n'a pas d'avant, il n'a qu'un apres.
     */
    @Transactional
    public CardCategoryDto create(CardCategoryRequest request) {
        String name = normalizeName(request.name());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Cette catégorie de cartes existe déjà.");
        }

        CardCategory category = new CardCategory();
        category.setName(name);
        category.setDisplayOrder(request.displayOrder());
        category.setHidden(Boolean.TRUE.equals(request.hidden()));
        category.setGame(Boolean.TRUE.equals(request.game()));
        category.setExtension(Boolean.TRUE.equals(request.extension()));
        category.setFirstAcquisitionPoints(requireNonNegative(request.firstAcquisitionPoints(), "Les PC de première obtention"));
        category.setDuplicatePoints(requireNonNegative(request.duplicatePoints(), "Les PC de doublon"));
        category.setDuplicatesPerPoint(request.duplicatesPerPoint());
        category.setRevealAnimation(request.revealAnimation());
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public CardCategoryDto updateImage(UUID id, MultipartFile image) {
        CardCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));

        String oldImageKey = category.getImageObjectKey();
        String oldThumbnailKey = category.getThumbnailObjectKey();
        CardImageService.StoredCardImages images = imageService.storeCategory(category.getId(), image);
        applyImages(category, images);
        CardCategoryDto dto = toDto(categoryRepository.save(category));
        imageService.deleteQuietly(oldImageKey);
        imageService.deleteQuietly(oldThumbnailKey);
        return dto;
    }

    @Transactional
    public CardCategoryDto deleteImage(UUID id) {
        CardCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));

        String oldImageKey = category.getImageObjectKey();
        String oldThumbnailKey = category.getThumbnailObjectKey();
        category.setImageObjectKey(null);
        category.setThumbnailObjectKey(null);
        category.setImageUrl(null);
        category.setThumbnailUrl(null);
        category.setContentType(null);
        CardCategoryDto dto = toDto(categoryRepository.save(category));
        imageService.deleteQuietly(oldImageKey);
        imageService.deleteQuietly(oldThumbnailKey);
        return dto;
    }

    /**
     * Le bareme d'une rarete decide de ce que vaut chaque carte qu'elle porte :
     * le changer deplace le score de collection de tous ses detenteurs d'un
     * seul geste. La ligne du registre nomme donc la rarete et le nombre de
     * joueurs touches, pas un joueur.
     *
     * <p>Un enregistrement qui ne change aucune des trois valeurs de bareme
     * n'ecrit rien - meme discipline que la garde de changement de
     * proprietaire dans {@code CardInstanceService.updateAdmin}.</p>
     */
    @Transactional
    public CardCategoryDto update(UUID id, CardCategoryRequest request, String actorKeycloakSub) {
        CardCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));
        String name = normalizeName(request.name());
        Integer previousFirstAcquisitionPoints = category.getFirstAcquisitionPoints();
        Integer previousDuplicatePoints = category.getDuplicatePoints();
        Integer previousDuplicatesPerPoint = category.getDuplicatesPerPoint();

        if (categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .isPresent()) {
            throw new ConflictException("Cette catégorie de cartes existe déjà.");
        }

        category.setName(name);
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.hidden() != null) {
            category.setHidden(request.hidden());
        }
        if (request.game() != null) {
            category.setGame(request.game());
        }
        if (request.extension() != null) {
            category.setExtension(request.extension());
        }
        if (request.firstAcquisitionPoints() != null) {
            category.setFirstAcquisitionPoints(requireNonNegative(request.firstAcquisitionPoints(), "Les PC de première obtention"));
        }
        if (request.duplicatePoints() != null) {
            category.setDuplicatePoints(requireNonNegative(request.duplicatePoints(), "Les PC de doublon"));
            if (request.duplicatesPerPoint() != null) {
                category.setDuplicatesPerPoint(request.duplicatesPerPoint());
            }
            if (request.revealAnimation() != null) {
                category.setRevealAnimation(request.revealAnimation());
            }
        }
        CardCategoryDto dto = toDto(categoryRepository.save(category));
        journalScaleChange(
                actorKeycloakSub,
                category,
                previousFirstAcquisitionPoints,
                previousDuplicatePoints,
                previousDuplicatesPerPoint
        );
        return dto;
    }

    private void journalScaleChange(
            String actorKeycloakSub,
            CardCategory category,
            Integer previousFirstAcquisitionPoints,
            Integer previousDuplicatePoints,
            Integer previousDuplicatesPerPoint
    ) {
        boolean changed = !Objects.equals(previousFirstAcquisitionPoints, category.getFirstAcquisitionPoints())
                || !Objects.equals(previousDuplicatePoints, category.getDuplicatePoints())
                || !Objects.equals(previousDuplicatesPerPoint, category.getDuplicatesPerPoint());
        if (!changed) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "CARD_CATEGORY");
        // La categorie vient de findById dans update, seul appelant : son id ne
        // peut pas etre nul, et le comptage ci-dessous le passe deja tel quel.
        details.put("categoryId", category.getId().toString());
        details.put("categoryName", category.getName());
        details.put("fromFirstAcquisitionPoints", previousFirstAcquisitionPoints);
        details.put("toFirstAcquisitionPoints", category.getFirstAcquisitionPoints());
        details.put("fromDuplicatePoints", previousDuplicatePoints);
        details.put("toDuplicatePoints", category.getDuplicatePoints());
        details.put("fromDuplicatesPerPoint", previousDuplicatesPerPoint);
        details.put("toDuplicatesPerPoint", category.getDuplicatesPerPoint());
        // Sans ce nombre, "PC de la rarete Legendaire : 50 -> 80" ne dit pas
        // si le geste a touche trois joueurs ou trois cents.
        details.put("affectedPlayers", collectionRepository.countHoldersOfCategory(category.getId()));
        journal.recordGlobal(actorKeycloakSub, AdminActionType.COLLECTION_SCALE_CHANGED, details);
    }

    @Transactional
    public void delete(UUID id) {
        CardCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));
        if (cardRepository.existsByCategoryRef_IdAndDefaultCardFalse(id)) {
            throw new BadRequestException("Une catégorie utilisée par des cartes ne peut pas être supprimée.");
        }
        categoryRepository.delete(category);
    }

    private CardCategoryDto toDto(CardCategory category) {
        return new CardCategoryDto(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                category.getImageUrl(),
                category.getThumbnailUrl(),
                category.getContentType(),
                category.isHidden(),
                category.isGame(),
                category.isExtension(),
                category.getFirstAcquisitionPoints(),
                category.getDuplicatePoints(),
                category.getDuplicatesPerPoint(),
                category.getRevealAnimation(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    private void applyImages(CardCategory category, CardImageService.StoredCardImages images) {
        category.setImageObjectKey(images.imageObjectKey());
        category.setThumbnailObjectKey(images.thumbnailObjectKey());
        category.setImageUrl(images.imageUrl());
        category.setThumbnailUrl(images.thumbnailUrl());
        category.setContentType(images.contentType());
    }

    static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Le nom de catégorie est requis.");
        }
        String normalized = name.trim();
        if (normalized.length() > 50) {
            throw new BadRequestException("Le nom de catégorie doit contenir au plus 50 caractères.");
        }
        return normalized;
    }

    private static int requireNonNegative(Integer value, String label) {
        int resolved = value == null ? 0 : value;
        if (resolved < 0) {
            throw new BadRequestException(label + " doivent être positifs ou nuls.");
        }
        return resolved;
    }
}
