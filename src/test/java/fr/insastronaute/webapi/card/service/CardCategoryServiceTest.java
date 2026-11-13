package fr.insastronaute.webapi.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.dto.CardCategoryRequest;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardCategoryServiceTest {

    @Mock
    private CardCategoryRepository categoryRepository;

    @Mock
    private CardImageService imageService;

    @Mock
    private CardCollectionRepository collectionRepository;

    @Mock
    private AdminActionEventRepository events;

    @Mock
    private UserRepository users;

    private CardCategoryService categoryService;

    /**
     * Le vrai journal sur un depot mocke, comme dans AdminBoundaryTest : toute
     * ecriture atterrit sur {@code events} et devient observable, y compris
     * celles qu'on ne veut pas voir.
     */
    @BeforeEach
    void setUp() {
        AppUser admin = new AppUser();
        admin.setId(UUID.randomUUID());
        admin.setKeycloakSub(ADMIN_SUB);
        lenient().when(users.findByKeycloakSub(ADMIN_SUB)).thenReturn(Optional.of(admin));
        lenient().when(collectionRepository.countHoldersOfCategory(any())).thenReturn(7L);

        categoryService = new CardCategoryService(
                categoryRepository,
                mock(CardRepository.class),
                collectionRepository,
                imageService,
                new AdminActionJournal(events, users, new ObjectMapper())
        );
    }

    private static final String ADMIN_SUB = "sub-admin";

    @Test
    void createPersistsHiddenFlagWhenRequested() {
        when(categoryRepository.existsByNameIgnoreCase("Secret")).thenReturn(false);
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = categoryService.create(new CardCategoryRequest(
                "Secret",
                5,
                true,
                true,
                120,
                15
        ));

        assertThat(result.hidden()).isTrue();
        assertThat(result.game()).isTrue();
        assertThat(result.firstAcquisitionPoints()).isEqualTo(120);
        assertThat(result.duplicatePoints()).isEqualTo(15);
        verify(categoryRepository).save(any(CardCategory.class));
    }

    @Test
    void createAndUpdateExposeThePersonalExtensionFlag() {
        when(categoryRepository.existsByNameIgnoreCase("Magellan")).thenReturn(false);
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = categoryService.create(new CardCategoryRequest(
                "Magellan", 5, false, false, 120, 15, 1, true, null
        ));
        assertThat(created.extension()).isTrue();

        CardCategory category = category(UUID.randomUUID());
        category.setName("Magellan");
        category.setExtension(true);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Magellan")).thenReturn(Optional.of(category));

        var updated = categoryService.update(category.getId(), new CardCategoryRequest(
                "Magellan", null, null, null, null, null, null, false, null
        ), ADMIN_SUB);
        assertThat(updated.extension()).isFalse();
    }

    @Test
    void theRevealAnimationIsChosenPerCategoryAndSurvivesAnUpdateThatOmitsIt() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setRevealAnimation(fr.insastronaute.webapi.card.domain.CardRevealAnimation.SPECTACULAR);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase(category.getName())).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Un client qui ne connaît pas encore le champ ne doit pas réinitialiser
        // le choix en enregistrant le reste.
        var updated = categoryService.update(categoryId, new CardCategoryRequest(
                category.getName(), null, null, null, null, null, null, null, null
        ), ADMIN_SUB);

        assertThat(updated.revealAnimation())
                .isEqualTo(fr.insastronaute.webapi.card.domain.CardRevealAnimation.SPECTACULAR);
    }

    @Test
    void everyAnimationOfferedInTheAdminIsAccepted() {
        // La contrainte de base n'accepte que ces valeurs : les ajouter à l'enum
        // sans les ajouter à la migration passerait la compilation et casserait
        // à l'enregistrement.
        assertThat(fr.insastronaute.webapi.card.domain.CardRevealAnimation.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "AUTO", "CALM", "SPARKLE", "RADIANT", "SPECTACULAR",
                        "HOLOGRAM", "EMBER", "FROST", "COSMIC", "GOLD", "GLITCH"
                );
    }

    @Test
    void updateKeepsHiddenFlagWhenOmitted() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setHidden(true);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Rare")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = categoryService.update(categoryId, new CardCategoryRequest(
                "Rare",
                null,
                null,
                null,
                null,
                null
        ), ADMIN_SUB);

        assertThat(result.hidden()).isTrue();
        assertThat(result.firstAcquisitionPoints()).isZero();
        assertThat(result.duplicatePoints()).isZero();
    }

    @Test
    void updateCanUnhideCategory() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setHidden(true);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Rare")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = categoryService.update(categoryId, new CardCategoryRequest(
                "Rare",
                null,
                false,
                true,
                80,
                10
        ), ADMIN_SUB);

        assertThat(result.hidden()).isFalse();
        assertThat(result.game()).isTrue();
        assertThat(result.firstAcquisitionPoints()).isEqualTo(80);
        assertThat(result.duplicatePoints()).isEqualTo(10);
    }

    @Test
    void updateImageStoresNewCategoryImageAndDeletesOldObjects() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setImageObjectKey("card-categories/old/original.jpg");
        category.setThumbnailObjectKey("card-categories/old/thumbnail.jpg");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(imageService.storeCategory(eq(categoryId), any())).thenReturn(new CardImageService.StoredCardImages(
                "card-categories/new/original.jpg",
                "card-categories/new/thumbnail.jpg",
                "https://s3.example.com/card-categories/new/original.jpg",
                "https://s3.example.com/card-categories/new/thumbnail.jpg",
                "image/jpeg"
        ));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = categoryService.updateImage(categoryId, null);

        assertThat(result.imageUrl()).isEqualTo("https://s3.example.com/card-categories/new/original.jpg");
        assertThat(result.hidden()).isFalse();
        assertThat(category.getImageObjectKey()).isEqualTo("card-categories/new/original.jpg");
        verify(imageService).deleteQuietly("card-categories/old/original.jpg");
        verify(imageService).deleteQuietly("card-categories/old/thumbnail.jpg");
    }

    @Test
    void deleteImageClearsImageMetadataAndDeletesObjects() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setImageObjectKey("card-categories/old/original.jpg");
        category.setThumbnailObjectKey("card-categories/old/thumbnail.jpg");
        category.setImageUrl("https://s3.example.com/card-categories/old/original.jpg");
        category.setThumbnailUrl("https://s3.example.com/card-categories/old/thumbnail.jpg");
        category.setContentType("image/jpeg");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = categoryService.deleteImage(categoryId);

        assertThat(result.imageUrl()).isNull();
        assertThat(result.hidden()).isFalse();
        assertThat(category.getImageObjectKey()).isNull();
        assertThat(category.getThumbnailObjectKey()).isNull();
        assertThat(category.getContentType()).isNull();
        verify(imageService).deleteQuietly("card-categories/old/original.jpg");
        verify(imageService).deleteQuietly("card-categories/old/thumbnail.jpg");
    }

    /**
     * Le bareme d'une rarete decide de ce que vaut chaque carte qu'elle porte :
     * une ligne du registre doit nommer la rarete, l'avant/apres, et combien de
     * joueurs le geste a touches.
     */
    @Test
    void changingTheScaleOfARarityWritesOneGlobalRowNamingItsScope() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setName("Legendaire");
        category.setFirstAcquisitionPoints(50);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Legendaire")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionRepository.countHoldersOfCategory(categoryId)).thenReturn(42L);

        categoryService.update(categoryId, new CardCategoryRequest(
                "Legendaire", null, null, null, 80, null
        ), ADMIN_SUB);

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(events).save(captor.capture());
        AdminActionEvent saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AdminActionType.COLLECTION_SCALE_CHANGED);
        assertThat(saved.getTarget()).isNull();
        assertThat(saved.getDetailsJson())
                .contains("\"scope\":\"CARD_CATEGORY\"")
                .contains("\"categoryName\":\"Legendaire\"")
                .contains("\"fromFirstAcquisitionPoints\":50")
                .contains("\"toFirstAcquisitionPoints\":80")
                .contains("\"affectedPlayers\":42");
    }

    /**
     * Meme discipline que la garde de changement de proprietaire dans
     * CardInstanceService.updateAdmin : un enregistrement qui ne deplace aucun
     * point ne laisse pas de ligne. Sinon le registre se remplit de gestes qui
     * n'ont rien fait, et les vrais deviennent introuvables.
     */
    @Test
    void reSavingTheSameScaleWritesNothing() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = category(categoryId);
        category.setFirstAcquisitionPoints(50);
        category.setDuplicatePoints(5);
        category.setDuplicatesPerPoint(10);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Rare")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.update(categoryId, new CardCategoryRequest(
                "Rare", 3, true, null, 50, 5, 10, null, null
        ), ADMIN_SUB);

        verify(events, never()).save(any());
        verify(collectionRepository, never()).countHoldersOfCategory(any());
    }

    /**
     * Une categorie neuve ne contient aucune carte : personne n'en detient,
     * aucun score ne bouge, et le bareme n'a pas d'avant.
     */
    @Test
    void creatingACategoryWritesNothing() {
        when(categoryRepository.existsByNameIgnoreCase("Neuve")).thenReturn(false);
        when(categoryRepository.save(any(CardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.create(new CardCategoryRequest("Neuve", 1, false, false, 120, 15));

        verify(events, never()).save(any());
    }

    private CardCategory category(UUID categoryId) {
        CardCategory category = new CardCategory();
        category.setId(categoryId);
        category.setName("Rare");
        category.setDisplayOrder(1);
        return category;
    }
}
