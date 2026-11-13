package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.article.domain.Category;
import fr.insastronaute.webapi.article.dto.CategoryDto;
import fr.insastronaute.webapi.article.dto.CategoryRequest;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.article.repository.CategoryRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;

    public CategoryService(CategoryRepository categoryRepository, ArticleRepository articleRepository) {
        this.categoryRepository = categoryRepository;
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String name = normalizeName(request.name());
        String slug = normalizeSlug(request.slug(), name);

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Cette catégorie d'articles existe déjà.");
        }
        if (categoryRepository.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Ce slug de catégorie d'articles existe déjà.");
        }

        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
        String name = normalizeName(request.name());
        String slug = normalizeSlug(request.slug(), name);

        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Cette catégorie d'articles existe déjà.");
                });
        categoryRepository.findBySlugIgnoreCase(slug)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Ce slug de catégorie d'articles existe déjà.");
                });

        category.setName(name);
        category.setSlug(slug);
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
        if (articleRepository.existsByCategoryId(id)) {
            throw new BadRequestException("Une catégorie utilisée par des articles ne peut pas être supprimée.");
        }
        categoryRepository.delete(category);
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getSlug());
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Le nom de catégorie est requis.");
        }

        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new BadRequestException("Le nom de catégorie doit contenir au plus 120 caractères.");
        }
        return normalized;
    }

    private String normalizeSlug(String slug, String fallbackName) {
        String source = slug == null || slug.isBlank() ? fallbackName : slug;
        String normalized = Normalizer.normalize(source.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (normalized.isBlank()) {
            throw new BadRequestException("Le slug de catégorie est requis.");
        }
        if (normalized.length() > 140) {
            throw new BadRequestException("Le slug de catégorie doit contenir au plus 140 caractères.");
        }
        return normalized;
    }
}
