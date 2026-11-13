package fr.insastronaute.webapi.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.dto.CardArchiveImportResultDto;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.storage.S3Properties;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
class CardArchiveServiceTest {

    @Autowired
    private CardCategoryRepository categoryRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardInstanceRepository instanceRepository;

    @Autowired
    private CardCollectionRepository collectionRepository;

    @Autowired
    private CardPrintTemplateRepository templateRepository;

    @Autowired
    private CardPrintRunRepository printRunRepository;

    @Autowired
    private CardTypeRepository typeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private CardArchiveService archiveService;
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1, 2, 3}));

        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicBucket("insastronaute-public");
        s3Properties.setPublicEndpoint("https://cdn.target.example.com");

        archiveService = new CardArchiveService(
                categoryRepository,
                cardRepository,
                instanceRepository,
                collectionRepository,
                templateRepository,
                printRunRepository,
                typeRepository,
                entityManager,
                s3Client,
                s3Properties,
                new ObjectMapper()
        );
    }

    @Test
    void importResetsCardsAndRestoresInstancesWithoutOwners() {
        CardCategory category = saveCategory();
        UUID categoryId = Objects.requireNonNull(category.getId());
        Card card = saveCard(category);
        UUID cardId = Objects.requireNonNull(card.getId());
        AppUser owner = saveUser();
        CardInstance instance = saveOwnedInstance(card, owner);
        savePhysicalCollection(card, owner);

        byte[] archive = archiveService.exportArchive();

        CardArchiveImportResultDto result = archiveService.importArchive(new MockMultipartFile(
                "archive",
                "cards.zip",
                "application/zip",
                archive
        ));

        entityManager.flush();
        entityManager.clear();

        CardInstance restoredInstance = instanceRepository.findById(instance.getId()).orElseThrow();
        Card restoredCard = cardRepository.findById(cardId).orElseThrow();
        CardCategory restoredCategory = categoryRepository.findById(categoryId).orElseThrow();

        assertThat(result.cardCount()).isEqualTo(1);
        assertThat(result.instanceCount()).isEqualTo(1);
        assertThat(result.objectCount()).isEqualTo(2);
        assertThat(restoredInstance.getPublicId()).isEqualTo("ABCDE-FGHJK-MNPQR");
        assertThat(restoredInstance.getOwner()).isNull();
        assertThat(restoredInstance.getAssignedAt()).isNull();
        assertThat(restoredCard.getImageUrl())
                .isEqualTo("https://cdn.target.example.com/insastronaute-public/cards/card/original.jpg");
        assertThat(restoredCategory.isHidden()).isTrue();
        assertThat(restoredCategory.isGame()).isTrue();
        assertThat(restoredCategory.getFirstAcquisitionPoints()).isEqualTo(100);
        assertThat(restoredCategory.getDuplicatePoints()).isEqualTo(10);
        assertThat(restoredCard.getFirstAcquisitionPoints()).isEqualTo(150);
        assertThat(restoredCard.getDuplicatePoints()).isZero();
        assertThat(collectionRepository.findAll()).isEmpty();
        assertThat(userRepository.findById(owner.getId())).isPresent();
    }

    @Test
    void importSkipsPhysicalInstancesWhenPhysicalImportIsDisabled() {
        CardCategory category = saveCategory();
        Card card = saveCard(category);
        AppUser owner = saveUser();
        saveOwnedInstance(card, owner);

        byte[] archive = archiveService.exportArchive();

        CardArchiveImportResultDto result = archiveService.importArchive(new MockMultipartFile(
                "archive",
                "cards.zip",
                "application/zip",
                archive
        ), false);

        assertThat(result.cardCount()).isEqualTo(1);
        assertThat(result.instanceCount()).isZero();
        assertThat(result.printRunCount()).isZero();
        assertThat(cardRepository.findAll()).hasSize(1);
        assertThat(instanceRepository.findAll()).isEmpty();
    }

    @Test
    void importKeepsPrintRunObjectsOutOfStorageWhenPhysicalImportIsDisabled() {
        UUID runId = UUID.randomUUID();
        String pdfObjectKey = "card-print/runs/" + runId + ".pdf";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifestWithPrintRun(runId, pdfObjectKey));
        entries.put("objects/" + pdfObjectKey, new byte[] {1, 2, 3});

        CardArchiveImportResultDto result = archiveService.importArchive(zipFile(entries), false);

        assertThat(result.printRunCount()).isZero();
        assertThat(result.objectCount()).isZero();
        assertThat(printRunRepository.findAll()).isEmpty();
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void importRejectsAnExcessiveNumberOfZipEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", emptyManifest());
        for (int index = 0; index < 2_049; index++) {
            entries.put("objects/cards/unreferenced/" + index + ".jpg", new byte[] {1});
        }

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'archive de cartes contient trop d'entrées.");
    }

    @Test
    void importRejectsAnExcessivelyLargeExpandedEntry() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", emptyManifest());
        entries.put("objects/cards/oversized/original.jpg", new byte[(64 * 1024 * 1024) + 1]);

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Une entrée de l'archive de cartes dépasse 64 MB.");
    }

    @Test
    void importRejectsAnOversizedManifest() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", new byte[(32 * 1024 * 1024) + 1]);

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le manifeste de l'archive de cartes dépasse 32 MB.");
    }

    @Test
    void importRejectsAnExcessiveCompressionRatio() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", emptyManifest());
        entries.put("objects/cards/bomb/original.jpg", new byte[10 * 1024 * 1024]);

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le ratio de compression de l'archive de cartes est excessif.");
    }

    @Test
    void importRejectsAnUnsupportedZipEntry() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", emptyManifest());
        entries.put("scripts/payload.sh", new byte[] {1});

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'archive de cartes contient une entrée non supportée.");
    }

    @Test
    void importRejectsAnObjectOutsideItsBusinessPrefix() {
        String objectKey = "articles/hijack.jpg";
        Map<String, byte[]> entries = Map.of(
                "manifest.json", manifestWithCategoryImage(objectKey),
                "objects/" + objectKey, new byte[] {1, 2, 3}
        );

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Une clé d'objet de l'archive ne respecte pas son préfixe métier.");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void importRejectsReplacingAnExistingObjectWithDifferentContent() {
        String objectKey = "card-categories/category/original.jpg";
        Map<String, byte[]> entries = Map.of(
                "manifest.json", manifestWithCategoryImage(objectKey),
                "objects/" + objectKey, new byte[] {9, 9, 9}
        );

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'objet " + objectKey + " existe déjà avec un contenu différent.");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rollbackCleanupDeletesOnlyObjectsSuccessfullyCreatedByTheImport() {
        String createdKey = "card-categories/category/original.jpg";
        String failedKey = "card-categories/category/thumbnail.jpg";
        Map<String, byte[]> entries = Map.of(
                "manifest.json", manifestWithCategoryImages(createdKey, failedKey),
                "objects/" + createdKey, new byte[] {1},
                "objects/" + failedKey, new byte[] {2}
        );
        when(s3Client.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(S3Exception.builder().statusCode(500).message("write failed").build());
        HashSet<TransactionSynchronization> synchronizationsBefore =
                new HashSet<>(TransactionSynchronizationManager.getSynchronizations());

        assertThatThrownBy(() -> archiveService.importArchive(zipFile(entries)))
                .isInstanceOf(S3Exception.class);

        TransactionSynchronization cleanup = TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(synchronization -> !synchronizationsBefore.contains(synchronization))
                .findFirst()
                .orElseThrow();
        cleanup.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        org.mockito.ArgumentCaptor<DeleteObjectRequest> deleteCaptor =
                org.mockito.ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().key()).isEqualTo(createdKey);
    }

    private byte[] emptyManifest() {
        return manifest(List.of());
    }

    private byte[] manifestWithCategoryImage(String objectKey) {
        return manifestWithCategoryImages(objectKey, null);
    }

    private byte[] manifestWithCategoryImages(String imageObjectKey, String thumbnailObjectKey) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", UUID.randomUUID());
        category.put("name", "Imported");
        category.put("displayOrder", 1);
        category.put("imageObjectKey", imageObjectKey);
        category.put("thumbnailObjectKey", thumbnailObjectKey);
        category.put("contentType", "image/jpeg");
        category.put("hidden", false);
        category.put("game", false);
        return manifest(List.of(category));
    }

    private byte[] manifestWithPrintRun(UUID runId, String pdfObjectKey) {
        try {
            Map<String, Object> printRun = new LinkedHashMap<>();
            printRun.put("id", runId);
            printRun.put("templateId", UUID.randomUUID());
            printRun.put("sheetCount", 1);
            printRun.put("instanceCount", 0);
            printRun.put("status", "COMPLETED");
            printRun.put("pdfObjectKey", pdfObjectKey);
            printRun.put("slots", List.of());
            printRun.put("createdAt", "2026-07-30T00:00:00Z");
            printRun.put("completedAt", "2026-07-30T00:00:00Z");
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", "insastronaute-card-archive");
            manifest.put("version", 1);
            manifest.put("exportedAt", "2026-07-30T00:00:00Z");
            manifest.put("types", List.of());
            manifest.put("categories", List.of());
            manifest.put("cards", List.of());
            manifest.put("instances", List.of());
            manifest.put("templates", List.of());
            manifest.put("printRuns", List.of(printRun));
            return new ObjectMapper().writeValueAsBytes(manifest);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] manifest(List<Map<String, Object>> categories) {
        try {
            return new ObjectMapper().writeValueAsBytes(Map.of(
                    "format", "insastronaute-card-archive",
                    "version", 1,
                    "exportedAt", "2026-07-30T00:00:00Z",
                    "types", List.of(),
                    "categories", categories,
                    "cards", List.of(),
                    "instances", List.of(),
                    "templates", List.of(),
                    "printRuns", List.of()
            ));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MockMultipartFile zipFile(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return new MockMultipartFile("archive", "cards.zip", "application/zip", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CardCategory saveCategory() {
        CardCategory category = new CardCategory();
        category.setName("Rare");
        category.setDisplayOrder(1);
        category.setHidden(true);
        category.setGame(true);
        category.setFirstAcquisitionPoints(100);
        category.setDuplicatePoints(10);
        return categoryRepository.saveAndFlush(category);
    }

    private Card saveCard(CardCategory category) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte Alpha");
        card.setCategoryRef(category);
        card.setEnabled(true);
        card.setDisplayOrder(1);
        card.setFirstAcquisitionPoints(150);
        card.setDuplicatePoints(0);
        card.setImageObjectKey("cards/card/original.jpg");
        card.setThumbnailObjectKey("cards/card/thumbnail.jpg");
        card.setImageUrl("https://cdn.source.example.com/cards/card/original.jpg");
        card.setThumbnailUrl("https://cdn.source.example.com/cards/card/thumbnail.jpg");
        card.setContentType("image/jpeg");
        return cardRepository.saveAndFlush(card);
    }

    private AppUser saveUser() {
        AppUser user = new AppUser();
        user.setKeycloakSub("owner-sub");
        user.setEmail("owner@example.com");
        user.setDisplayName("Owner");
        return userRepository.saveAndFlush(user);
    }

    private CardInstance saveOwnedInstance(Card card, AppUser owner) {
        CardInstance instance = new CardInstance();
        instance.setCard(card);
        instance.setPublicId("ABCDE-FGHJK-MNPQR");
        instance.setOwner(owner);
        instance.setAssignedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return instanceRepository.saveAndFlush(instance);
    }

    private void savePhysicalCollection(Card card, AppUser owner) {
        CardCollection collection = new CardCollection();
        collection.setUser(owner);
        collection.setCard(card);
        collection.setCollectionType(CardCollectionType.PHYSICAL);
        collection.setQuantity(1);
        collectionRepository.saveAndFlush(collection);
    }
}
