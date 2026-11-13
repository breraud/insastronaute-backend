package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetCode;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetId;
import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetCodeDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetSummaryDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import fr.insastronaute.webapi.card.print.repository.CardDatasetCropRepository;
import fr.insastronaute.webapi.card.print.repository.CardDatasetSheetRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CardDatasetServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardPrintRunRepository printRunRepository;

    @Mock
    private CardPrintTemplateRepository templateRepository;

    @Mock
    private CardPrintTemplateService templateService;

    @Mock
    private CardDatasetPdfRenderer pdfRenderer;

    @Mock
    private CardDatasetSheetRepository sheetRepository;

    @Mock
    private CardDatasetSheetStorageService sheetStorage;

    @Mock
    private CardDatasetCropRepository cropRepository;

    @Mock
    private CardDatasetCropStorageService cropStorage;

    @InjectMocks
    private CardDatasetService datasetService;

    /** Image de chaque carte du fixture : un octet unique, qui identifie la carte tiree. */
    private final Map<UUID, byte[]> fixtureImages = new HashMap<>();

    @Test
    void theTestSheetRemembersEveryCodeItPrintsInTheOrderItPrintsThem() {
        // Sans cette memoire, une planche de test scannee n'est pas etiquetable : on a les
        // images et pas les codes. L'ordre compte : la tache 5 affiche le code attendu a
        // cote de la bande en cours, et c'est ce qui rend une desynchronisation visible.
        givenAnActiveTemplate();
        givenEnabledCards(4);
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetService.GeneratedDatasetSheet generated = datasetService.generateTestDatasetPdf();

        ArgumentCaptor<List<CardDatasetPdfRenderer.DatasetCardInfo>> printedCaptor = captorOfPrintedCards();
        ArgumentCaptor<String> documentIdCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(pdfRenderer).render(printedCaptor.capture(), any(), documentIdCaptor.capture());

        ArgumentCaptor<CardDatasetSheet> sheetCaptor = ArgumentCaptor.forClass(CardDatasetSheet.class);
        org.mockito.Mockito.verify(sheetRepository).save(sheetCaptor.capture());
        CardDatasetSheet saved = sheetCaptor.getValue();

        List<String> printedCodes = printedCaptor.getValue().stream()
                .map(CardDatasetPdfRenderer.DatasetCardInfo::publicId)
                .toList();

        assertThat(saved.getCodes()).extracting(CardDatasetSheetCode::getCode)
                .containsExactlyElementsOf(printedCodes);
        assertThat(saved.getCodes()).extracting(CardDatasetSheetCode::getPosition)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, printedCodes.size()).boxed().toList());
        assertThat(saved.getCodes()).allSatisfy(code -> assertThat(code.getSheet()).isSameAs(saved));

        // L'identifiant memorise est celui qui part a l'impression : c'est le seul lien entre
        // le papier et la base.
        assertThat(generated.sheetId()).isEqualTo(saved.getSheetId());
        assertThat(documentIdCaptor.getValue()).isEqualTo(saved.getSheetId());
        assertThat(CardDatasetSheetId.isValid(generated.sheetId())).isTrue();
        assertThat(generated.pdf()).isEqualTo(new byte[]{'%', 'P', 'D', 'F'});
    }

    @Test
    void theTestSheetRemembersWhichCardItPrintedUnderEachCode() {
        // Sans cette memoire, la planche ne peut pas etre re-rendue : l'affectation
        // carte -> emplacement vient d'un tirage aleatoire. C'est elle qui permet au rendu
        // groupe de partager une seule image par carte au lieu d'une par emplacement.
        givenAnActiveTemplate();
        givenEnabledCards(4);
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        datasetService.generateTestDatasetPdf();

        ArgumentCaptor<List<CardDatasetPdfRenderer.DatasetCardInfo>> printedCaptor = captorOfPrintedCards();
        org.mockito.Mockito.verify(pdfRenderer).render(printedCaptor.capture(), any(), anyString());
        ArgumentCaptor<CardDatasetSheet> sheetCaptor = ArgumentCaptor.forClass(CardDatasetSheet.class);
        org.mockito.Mockito.verify(sheetRepository).save(sheetCaptor.capture());

        List<CardDatasetPdfRenderer.DatasetCardInfo> printed = printedCaptor.getValue();
        List<CardDatasetSheetCode> codes = sheetCaptor.getValue().getCodes();

        assertThat(codes).hasSameSizeAs(printed);
        for (int position = 0; position < codes.size(); position++) {
            Card remembered = codes.get(position).getCard();
            assertThat(remembered).isNotNull();
            // L'image dessinee a cette position est bien celle de la carte memorisee.
            assertThat(printed.get(position).imageBytes()).isEqualTo(fixtureImages.get(remembered.getId()));
        }
    }

    @Test
    void theTestSheetKeepsDrawingCodesThatDoNotExistInTheDatabase() {
        // Imprimer de vrais codes de carte sur du papier de brouillon est une fuite. La
        // distribution des caracteres est de toute facon la meme : les publicId reels sont
        // tires par le meme tirage uniforme.
        givenAnActiveTemplate();
        givenEnabledCards(4);
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%'});
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        datasetService.generateTestDatasetPdf();

        ArgumentCaptor<List<CardDatasetPdfRenderer.DatasetCardInfo>> printedCaptor = captorOfPrintedCards();
        org.mockito.Mockito.verify(pdfRenderer).render(printedCaptor.capture(), any(), anyString());

        assertThat(printedCaptor.getValue()).allSatisfy(info ->
                assertThat(info.publicId()).matches("^[A-Z0-9]{5}-[A-Z0-9]{5}-[A-Z0-9]{5}$"));
        org.mockito.Mockito.verifyNoInteractions(printRunRepository);
    }

    @Test
    void theBatchSheetRemembersTheRealCodesItPrints() {
        givenAnActiveTemplate();
        UUID batchId = UUID.randomUUID();
        CardPrintRun run = printRun(batchId, "AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF");
        when(printRunRepository.findWithSlotsById(batchId)).thenReturn(Optional.of(run));
        when(templateService.loadImageBytes(anyList())).thenReturn(new HashMap<>());
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%'});
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDatasetService.GeneratedDatasetSheet generated = datasetService.generateBatchDatasetPdf(batchId);

        ArgumentCaptor<CardDatasetSheet> sheetCaptor = ArgumentCaptor.forClass(CardDatasetSheet.class);
        org.mockito.Mockito.verify(sheetRepository).save(sheetCaptor.capture());
        CardDatasetSheet saved = sheetCaptor.getValue();

        assertThat(saved.getCodes()).extracting(CardDatasetSheetCode::getCode)
                .containsExactly("AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF");
        assertThat(saved.getPrintRun()).isSameAs(run);
        assertThat(CardDatasetSheetId.isValid(generated.sheetId())).isTrue();
    }

    @Test
    void aSheetHandsBackItsCodesToWhoeverTypesItsPrintedIdentifier() {
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));

        CardDatasetSheetDto dto = datasetService.findSheet("pl 48213 3");

        assertThat(dto.sheetId()).isEqualTo("PL-48213-3");
        assertThat(dto.codes()).extracting(CardDatasetSheetCodeDto::position).containsExactly(0, 1);
        assertThat(dto.codes()).extracting(CardDatasetSheetCodeDto::code)
                .containsExactly("AAAAA-BBBBB-CCCCC", "DDDDD-EEEEE-FFFFF");
    }

    @Test
    void aMistypedIdentifierIsRefusedInsteadOfPointingAtAnotherSheet() {
        assertThatThrownBy(() -> datasetService.findSheet("PL-48214-3"))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verifyNoInteractions(sheetRepository);
    }

    @Test
    void anUnknownButWellFormedIdentifierIsNotFound() {
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetService.findSheet("PL-48213-3"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void generatingATestSheetStoresTheExactRenderedPdfAndMakesItDownloadable() {
        givenAnActiveTemplate();
        givenEnabledCards(2);
        byte[] pdf = new byte[]{'%', 'P', 'D', 'F', 4, 2};
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(pdf);
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> {
            CardDatasetSheet source = invocation.getArgument(0);
            CardDatasetSheet managed = new CardDatasetSheet();
            managed.setId(source.getId());
            managed.setSheetId(source.getSheetId());
            managed.setPrintRun(source.getPrintRun());
            managed.setPdfObjectKey(source.getPdfObjectKey());
            managed.setCreatedAt(Instant.parse("2026-08-25T12:00:00Z"));
            return managed;
        });
        when(sheetStorage.store(any(UUID.class), any(byte[].class)))
                .thenReturn("card-print/dataset-sheets/original.pdf");

        CardDatasetSheetSummaryDto summary = datasetService.createTestSheet();

        ArgumentCaptor<CardDatasetSheet> sheet = ArgumentCaptor.forClass(CardDatasetSheet.class);
        org.mockito.Mockito.verify(sheetRepository).save(sheet.capture());
        org.mockito.Mockito.verify(sheetStorage).store(sheet.getValue().getId(), pdf);
        assertThat(sheet.getValue().getPdfObjectKey())
                .isEqualTo("card-print/dataset-sheets/original.pdf");
        assertThat(summary.sheetId()).isEqualTo(sheet.getValue().getSheetId());
        assertThat(summary.createdAt()).isEqualTo(Instant.parse("2026-08-25T12:00:00Z"));
        assertThat(summary.scanCount()).isZero();
        assertThat(summary.downloadable()).isTrue();
    }

    @Test
    void listsLegacyAndDownloadableSheetsWithTheirScanCounts() {
        UUID legacyId = UUID.randomUUID();
        UUID downloadableId = UUID.randomUUID();
        when(sheetRepository.findAllSummaries(any(Pageable.class))).thenReturn(List.of(
                new CardDatasetSheetRepository.CardDatasetSheetSummaryProjection() {
                    public UUID getId() { return downloadableId; }
                    public String getSheetId() { return "PL-48213-3"; }
                    public UUID getBatchId() { return null; }
                    public Instant getCreatedAt() { return Instant.parse("2026-08-25T12:00:00Z"); }
                    public long getScanCount() { return 7; }
                    public boolean getDownloadable() { return true; }
                },
                new CardDatasetSheetRepository.CardDatasetSheetSummaryProjection() {
                    public UUID getId() { return legacyId; }
                    public String getSheetId() { return "PL-01234-8"; }
                    public UUID getBatchId() { return null; }
                    public Instant getCreatedAt() { return Instant.parse("2026-08-24T12:00:00Z"); }
                    public long getScanCount() { return 3; }
                    public boolean getDownloadable() { return false; }
                }
        ));

        List<CardDatasetSheetSummaryDto> summaries = datasetService.findAllSheets();

        assertThat(summaries).extracting(CardDatasetSheetSummaryDto::id)
                .containsExactly(downloadableId, legacyId);
        assertThat(summaries).extracting(CardDatasetSheetSummaryDto::scanCount)
                .containsExactly(7L, 3L);
        assertThat(summaries).extracting(CardDatasetSheetSummaryDto::downloadable)
                .containsExactly(true, false);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(sheetRepository).findAllSummaries(page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void downloadsTheExactStoredPdfForAnExistingSheet() {
        CardDatasetSheet sheet = sheetWithCodes("PL-48213-3");
        sheet.setPdfObjectKey("card-print/dataset-sheets/original.pdf");
        byte[] original = new byte[]{'%', 'P', 'D', 'F', 1, 9};
        when(sheetRepository.findBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(sheetStorage.read("card-print/dataset-sheets/original.pdf")).thenReturn(original);

        assertThat(datasetService.readSheetPdf("pl 48213 3")).isEqualTo(original);
    }

    @Test
    void aLegacySheetWithoutStoredPdfRemainsKnownButIsNotDownloadable() {
        CardDatasetSheet legacy = sheetWithCodes("PL-48213-3");
        when(sheetRepository.findBySheetId("PL-48213-3")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> datasetService.readSheetPdf("PL-48213-3"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("PDF");
        org.mockito.Mockito.verifyNoInteractions(sheetStorage);
    }

    @Test
    void aRolledBackSheetCreationDeletesItsOrphanedS3Pdf() {
        givenAnActiveTemplate();
        givenEnabledCards(1);
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%'});
        when(sheetStorage.store(any(UUID.class), any(byte[].class)))
                .thenReturn("card-print/dataset-sheets/orphan.pdf");
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            datasetService.createTestSheet();
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            org.mockito.Mockito.verify(sheetStorage).delete("card-print/dataset-sheets/orphan.pdf");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aCommittedSheetCreationKeepsItsS3Pdf() {
        givenAnActiveTemplate();
        givenEnabledCards(1);
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%'});
        when(sheetStorage.store(any(UUID.class), any(byte[].class)))
                .thenReturn("card-print/dataset-sheets/kept.pdf");
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            datasetService.createTestSheet();
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

            org.mockito.Mockito.verify(sheetStorage, never()).delete(anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ArgumentCaptor<List<CardDatasetPdfRenderer.DatasetCardInfo>> captorOfPrintedCards() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardDatasetPdfRenderer.DatasetCardInfo>> captor =
                ArgumentCaptor.forClass(List.class);
        return captor;
    }

    private void givenAnActiveTemplate() {
        CardPrintTemplate template = new CardPrintTemplate();
        template.setId(UUID.randomUUID());
        template.setEnabled(true);
        when(templateRepository.findAll()).thenReturn(List.of(template));
    }

    private void givenEnabledCards(int count) {
        List<Card> cards = new ArrayList<>();
        Map<UUID, byte[]> images = fixtureImages;
        for (int i = 0; i < count; i++) {
            Card card = new Card();
            card.setId(UUID.randomUUID());
            card.setName("Carte " + i);
            card.setEnabled(true);
            cards.add(card);
            images.put(card.getId(), new byte[]{(byte) i});
        }
        when(cardRepository.findByEnabledTrue()).thenReturn(cards);
        when(templateService.loadImageBytes(anyList())).thenReturn(images);
    }

    private CardPrintRun printRun(UUID batchId, String... publicIds) {
        CardPrintRun run = new CardPrintRun();
        run.setId(batchId);
        int index = 0;
        for (String publicId : publicIds) {
            Card card = new Card();
            card.setId(UUID.randomUUID());
            card.setName("Carte " + index);
            CardInstance instance = new CardInstance();
            instance.setId(UUID.randomUUID());
            instance.setPublicId(publicId);
            CardPrintRunSlot slot = new CardPrintRunSlot();
            slot.setPageIndex(0);
            slot.setSlotIndex(index++);
            slot.setCard(card);
            slot.setCardInstance(instance);
            run.addSlot(slot);
        }
        return run;
    }

    @Test
    void severalSheetsAreGeneratedInOnePass() {
        givenAnActiveTemplate();
        givenEnabledCards(4);
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CardDatasetSheetSummaryDto> created = datasetService.createTestSheets(3);

        assertThat(created).hasSize(3);
        assertThat(created).extracting(CardDatasetSheetSummaryDto::sheetId).doesNotHaveDuplicates();
        org.mockito.Mockito.verify(pdfRenderer, org.mockito.Mockito.times(3)).render(anyList(), any(), anyString());
    }

    @Test
    void aBatchOfSheetsIsRefusedOutsideItsBounds() {
        assertThatThrownBy(() -> datasetService.createTestSheets(0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> datasetService.createTestSheets(21))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verify(sheetRepository, never()).save(any());
    }

    @Test
    void theGroupedPdfReproducesTheArchivedOriginals() {
        // Une planche doit avoir la meme tete seule ou en lot. Re-rendre au patron courant
        // ferait mentir l'original archive sur ce qui a ete imprime -- meme convention que
        // CardPrintPdfRegenerationService, ou la regeneration est une action explicite.
        CardDatasetSheet first = sheetRememberingCards("PL-48213-3", card());
        CardDatasetSheet second = sheetWithCodes("PL-31337-9", "AAAAA-BBBBB-CCCCC");
        first.setPdfObjectKey("dataset/first.pdf");
        second.setPdfObjectKey("dataset/second.pdf");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(first));
        when(sheetRepository.findWithCodesBySheetId("PL-31337-9")).thenReturn(Optional.of(second));
        when(sheetStorage.read("dataset/first.pdf")).thenReturn(onePagePdf());
        when(sheetStorage.read("dataset/second.pdf")).thenReturn(onePagePdf());

        byte[] pdf = datasetService.renderSheetsPdf(List.of("PL-48213-3", "PL-31337-9"));

        try (PDDocument merged = Loader.loadPDF(pdf)) {
            assertThat(merged.getNumberOfPages()).isEqualTo(2);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        org.mockito.Mockito.verify(pdfRenderer, never()).renderSheets(anyList(), any());
        org.mockito.Mockito.verify(pdfRenderer, never()).render(anyList(), any(), anyString());
    }

    @Test
    void regeneratingASheetDoesNotDeleteThePdfItJustWrote() {
        // La cle de stockage est derivee de l'identifiant de la planche, donc re-rendre ecrase
        // l'objet en place et renvoie la meme cle. Supprimer << l'ancien >> objet effacerait
        // celui qu'on vient d'ecrire : la ligne pointerait vers un objet disparu et le
        // telechargement suivant tomberait en 503.
        String objectKey = "card-print/dataset-sheets/" + UUID.randomUUID() + ".pdf";
        givenAnActiveTemplate();
        CardDatasetSheet sheet = sheetRememberingCards("PL-48213-3", card(), card());
        sheet.setPdfObjectKey(objectKey);
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(templateService.loadImageBytes(anyList())).thenReturn(Map.of());
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetStorage.store(any(), any())).thenReturn(objectKey);
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] pdf = datasetService.regenerateSheetPdf("PL-48213-3");

        assertThat(pdf).isEqualTo(new byte[]{'%', 'P', 'D', 'F'});
        assertThat(sheet.getPdfObjectKey()).isEqualTo(objectKey);
        org.mockito.Mockito.verify(sheetStorage, never()).delete(anyString());
    }

    @Test
    void regeneratingASheetDropsTheOldObjectWhenTheKeyActuallyChanges() {
        givenAnActiveTemplate();
        CardDatasetSheet sheet = sheetRememberingCards("PL-48213-3", card());
        sheet.setPdfObjectKey("card-print/dataset-sheets/legacy.pdf");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(templateService.loadImageBytes(anyList())).thenReturn(Map.of());
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetStorage.store(any(), any())).thenReturn("card-print/dataset-sheets/current.pdf");
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        datasetService.regenerateSheetPdf("PL-48213-3");

        org.mockito.Mockito.verify(sheetStorage).delete("card-print/dataset-sheets/legacy.pdf");
    }

    @Test
    void aSheetThatForgotItsCardsCannotBeRegenerated() {
        CardDatasetSheet legacy = sheetWithCodes("PL-48213-3", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> datasetService.regenerateSheetPdf("PL-48213-3"))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verify(sheetStorage, never()).store(any(), any());
    }

    @Test
    void regeneratingEverythingSkipsTheSheetsThatForgotTheirCards() {
        // Une planche non regenerable n'est pas un echec : elle est simplement hors de portee.
        // Distinguer les deux compte, sinon un compte-rendu alarme pour rien.
        givenAnActiveTemplate();
        CardDatasetSheet fresh = sheetRememberingCards("PL-48213-3", card());
        CardDatasetSheet legacy = sheetWithCodes("PL-31337-9", "AAAAA-BBBBB-CCCCC");
        when(sheetRepository.findAll()).thenReturn(List.of(fresh, legacy));
        when(templateService.loadImageBytes(anyList())).thenReturn(Map.of());
        when(pdfRenderer.render(anyList(), any(), anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetStorage.store(any(), any())).thenReturn("card-print/dataset-sheets/a.pdf");
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPrintPdfRegenerationDto result = datasetService.regenerateAllSheetPdfs();

        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.regeneratedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void regeneratingEverythingCountsAFailureWithoutStoppingTheRest() {
        givenAnActiveTemplate();
        CardDatasetSheet first = sheetRememberingCards("PL-48213-3", card());
        CardDatasetSheet second = sheetRememberingCards("PL-31337-9", card());
        when(sheetRepository.findAll()).thenReturn(List.of(first, second));
        when(templateService.loadImageBytes(anyList())).thenReturn(Map.of());
        when(pdfRenderer.render(anyList(), any(), anyString()))
                .thenThrow(new IllegalStateException("rendu impossible"))
                .thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(sheetStorage.store(any(), any())).thenReturn("card-print/dataset-sheets/a.pdf");
        when(sheetRepository.save(any(CardDatasetSheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPrintPdfRegenerationDto result = datasetService.regenerateAllSheetPdfs();

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.regeneratedCount()).isEqualTo(1);
    }

    @Test
    void deletingASheetTakesItsCropsAndItsStoredFilesWithIt() {
        CardDatasetSheet sheet = sheetRememberingCards("PL-48213-3", card());
        sheet.setPdfObjectKey("dataset/sheet.pdf");
        CardDatasetCrop crop = new CardDatasetCrop();
        crop.setId(UUID.randomUUID());
        crop.setImageObjectKey("dataset/crop.jpg");
        crop.setFrameObjectKey("dataset/frame.jpg");
        when(sheetRepository.findWithCodesBySheetId("PL-48213-3")).thenReturn(Optional.of(sheet));
        when(cropRepository.findBySheetOrderByCreatedAtAsc(sheet)).thenReturn(List.of(crop));

        datasetService.deleteSheet("PL-48213-3");

        org.mockito.Mockito.verify(cropRepository).deleteAll(List.of(crop));
        org.mockito.Mockito.verify(sheetRepository).delete(sheet);
        org.mockito.Mockito.verify(cropStorage).delete("dataset/crop.jpg");
        org.mockito.Mockito.verify(cropStorage).delete("dataset/frame.jpg");
        org.mockito.Mockito.verify(sheetStorage).delete("dataset/sheet.pdf");
    }

    private Card card() {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte " + UUID.randomUUID());
        return card;
    }

    private CardDatasetSheet sheetRememberingCards(String sheetId, Card... cards) {
        CardDatasetSheet sheet = new CardDatasetSheet();
        sheet.setId(UUID.randomUUID());
        sheet.setSheetId(sheetId);
        int position = 0;
        for (Card card : cards) {
            CardDatasetSheetCode code = new CardDatasetSheetCode();
            code.setPosition(position);
            code.setCode("CODE-" + position);
            code.setCard(card);
            sheet.addCode(code);
            position++;
        }
        return sheet;
    }

    private byte[] onePagePdf() {
        try (PDDocument document = new PDDocument();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CardDatasetSheet sheetWithCodes(String sheetId, String... codes) {
        CardDatasetSheet sheet = new CardDatasetSheet();
        sheet.setId(UUID.randomUUID());
        sheet.setSheetId(sheetId);
        int position = 0;
        for (String code : codes) {
            CardDatasetSheetCode sheetCode = new CardDatasetSheetCode();
            sheetCode.setPosition(position++);
            sheetCode.setCode(code);
            sheet.addCode(sheetCode);
        }
        return sheet;
    }
}
