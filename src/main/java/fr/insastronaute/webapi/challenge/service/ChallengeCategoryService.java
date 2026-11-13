package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeCategory;
import fr.insastronaute.webapi.challenge.dto.ChallengeCategoryDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeCategoryRequest;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ChallengeCategoryService {

    private final ChallengeCategoryRepository categoryRepository;
    private final ChallengeRepository challengeRepository;

    public ChallengeCategoryService(
            ChallengeCategoryRepository categoryRepository,
            ChallengeRepository challengeRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.challengeRepository = challengeRepository;
    }

    @Transactional(readOnly = true)
    public List<ChallengeCategoryDto> findAll() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(ChallengeCategory::isDefaultCategory).reversed()
                        .thenComparing(ChallengeCategory::getName))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ChallengeCategoryDto create(ChallengeCategoryRequest request) {
        String name = normalizeName(request.name());

        if (categoryRepository.existsById(name)) {
            throw new ConflictException("Cette catégorie existe déjà.");
        }

        ChallengeCategory category = new ChallengeCategory();
        category.setName(name);
        category.setDefaultCategory(ChallengeCategory.DEFAULT_NAME.equals(name));

        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public ChallengeCategoryDto update(String currentName, ChallengeCategoryRequest request) {
        String normalizedCurrentName = normalizeName(currentName);
        String newName = normalizeName(request.name());

        ChallengeCategory category = categoryRepository.findById(normalizedCurrentName)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable"));

        if (category.isSystemCategory() && !Objects.equals(normalizedCurrentName, newName)) {
            throw new BadRequestException("La catégorie " + normalizedCurrentName + " ne peut pas être renommée.");
        }

        if (Objects.equals(normalizedCurrentName, newName)) {
            return toDto(category);
        }

        if (categoryRepository.existsById(newName)) {
            throw new ConflictException("Cette catégorie existe déjà.");
        }

        ChallengeCategory replacement = new ChallengeCategory();
        replacement.setName(newName);
        replacement.setDefaultCategory(category.isDefaultCategory());
        replacement.setSystemCategory(category.isSystemCategory());

        ChallengeCategory saved = categoryRepository.save(replacement);
        challengeRepository.updateCategory(normalizedCurrentName, newName);
        categoryRepository.delete(category);

        return toDto(saved);
    }

    @Transactional
    public void delete(String name) {
        String normalizedName = normalizeName(name);
        ChallengeCategory category = categoryRepository.findById(normalizedName)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable"));

        if (category.isSystemCategory() || category.isDefaultCategory()) {
            throw new BadRequestException("La catégorie " + normalizedName + " ne peut pas être supprimée.");
        }

        ensureDefaultCategoryExists();
        challengeRepository.updateCategory(normalizedName, ChallengeCategory.DEFAULT_NAME);
        categoryRepository.delete(category);
    }

    private void ensureDefaultCategoryExists() {
        if (categoryRepository.existsById(ChallengeCategory.DEFAULT_NAME)) {
            return;
        }

        ChallengeCategory defaultCategory = new ChallengeCategory();
        defaultCategory.setName(ChallengeCategory.DEFAULT_NAME);
        defaultCategory.setDefaultCategory(true);
        defaultCategory.setSystemCategory(true);
        categoryRepository.save(defaultCategory);
    }

    private ChallengeCategoryDto toDto(ChallengeCategory category) {
        return new ChallengeCategoryDto(
                category.getName(),
                category.isDefaultCategory(),
                category.isSystemCategory(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return ChallengeCategory.DEFAULT_NAME;
        }

        String normalized = name.trim();
        if (normalized.length() > 50) {
            throw new BadRequestException(
                    "Nom de catégorie invalide.",
                    "name",
                    "Le nom de catégorie doit contenir au plus 50 caractères."
            );
        }

        return normalized;
    }
}
