package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeCategory;
import fr.insastronaute.webapi.challenge.dto.ChallengeCategoryRequest;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeCategoryServiceTest {

    @Mock
    private ChallengeCategoryRepository categoryRepository;

    @Mock
    private ChallengeRepository challengeRepository;

    private ChallengeCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeCategoryService(categoryRepository, challengeRepository);
    }

    @Test
    void createRejectsDuplicateCategoryName() {
        when(categoryRepository.existsById("photo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ChallengeCategoryRequest("photo")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cette catégorie existe déjà.");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteReassignsChallengesToDefaultBeforeDeletingCategory() {
        ChallengeCategory category = category("photo", false);

        when(categoryRepository.findById("photo")).thenReturn(Optional.of(category));
        when(categoryRepository.existsById(ChallengeCategory.DEFAULT_NAME)).thenReturn(true);

        service.delete("photo");

        verify(challengeRepository).updateCategory("photo", ChallengeCategory.DEFAULT_NAME);
        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteRejectsDefaultCategory() {
        ChallengeCategory category = category(ChallengeCategory.DEFAULT_NAME, true);

        when(categoryRepository.findById(ChallengeCategory.DEFAULT_NAME)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.delete(ChallengeCategory.DEFAULT_NAME))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La catégorie DEFAULT ne peut pas être supprimée.");

        verify(challengeRepository, never()).updateCategory(any(), any());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteRejectsDailySystemCategory() {
        ChallengeCategory category = category(ChallengeCategory.DAILY_NAME, false);
        category.setSystemCategory(true);
        when(categoryRepository.findById(ChallengeCategory.DAILY_NAME)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.delete(ChallengeCategory.DAILY_NAME))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La catégorie DAILY ne peut pas être supprimée.");

        verify(challengeRepository, never()).updateCategory(any(), any());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void updateRejectsRenamingDailySystemCategory() {
        ChallengeCategory category = category(ChallengeCategory.DAILY_NAME, false);
        category.setSystemCategory(true);
        when(categoryRepository.findById(ChallengeCategory.DAILY_NAME)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.update(ChallengeCategory.DAILY_NAME, new ChallengeCategoryRequest("other")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La catégorie DAILY ne peut pas être renommée.");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findAllReportsSystemCategories() {
        ChallengeCategory daily = category(ChallengeCategory.DAILY_NAME, false);
        daily.setSystemCategory(true);
        when(categoryRepository.findAll()).thenReturn(java.util.List.of(daily));

        assertThat(service.findAll()).singleElement().satisfies(dto -> assertThat(dto.systemCategory()).isTrue());
    }

    @Test
    void updateRenamesCategoryAndReassignsChallenges() {
        ChallengeCategory category = category("photo", false);
        ChallengeCategory savedCategory = category("media", false);

        when(categoryRepository.findById("photo")).thenReturn(Optional.of(category));
        when(categoryRepository.existsById("media")).thenReturn(false);
        when(categoryRepository.save(any())).thenReturn(savedCategory);

        var result = service.update("photo", new ChallengeCategoryRequest("media"));

        assertThat(result.name()).isEqualTo("media");
        verify(challengeRepository).updateCategory("photo", "media");
        verify(categoryRepository).delete(category);
    }

    private static ChallengeCategory category(String name, boolean defaultCategory) {
        ChallengeCategory category = new ChallengeCategory();
        category.setName(name);
        category.setDefaultCategory(defaultCategory);
        return category;
    }
}
