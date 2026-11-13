package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchPageLookupDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunCreateRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintSlotCardRequest;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardPrintRunServiceTest {

    @Mock
    private CardPrintRunRepository printRunRepository;

    @Mock
    private CardPrintTemplateService templateService;

    @Mock
    private CardInstanceService cardInstanceService;

    @Mock
    private CardInstanceRepository cardInstanceRepository;

    @Mock
    private CardPrintStorageService storageService;

    @Mock
    private CardPrintPdfRenderer pdfRenderer;

    @InjectMocks
    private CardPrintRunService printRunService;

    @Test
    void createsInstancesAndStoresGeneratedPdf() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = template(templateId);
        List<CardPrintSlotCardRequest> slotRequests = slotRequests();
        List<Card> cards = cards();
        List<CardPrintTemplateService.SelectedCard> selectedCards = selectedCards(cards);
        List<CardInstance> instances = instances(cards, 18);
        Map<UUID, byte[]> imageBytesByCardId = new HashMap<>();
        cards.forEach(card -> imageBytesByCardId.put(card.getId(), new byte[]{1, 2, 3}));
        byte[] templatePdf = new byte[]{4, 5, 6};
        byte[] generatedPdf = new byte[]{7, 8, 9};
        UUID generatedPrintRunId = UUID.randomUUID();
        AtomicBoolean savedWithoutPreassignedId = new AtomicBoolean(false);

        when(templateService.findTemplate(templateId)).thenReturn(template);
        when(templateService.resolveSelectedCards(slotRequests)).thenReturn(selectedCards);
        when(templateService.loadImageBytes(cards)).thenReturn(imageBytesByCardId);
        when(cardInstanceService.createUnassignedInstances(any(), any())).thenReturn(instances);
        when(storageService.readPublicObject("card-print/templates/template/template.pdf")).thenReturn(templatePdf);
        when(pdfRenderer.render(eq(template), eq(templatePdf), any())).thenReturn(generatedPdf);
        when(storageService.storeRun(any(), eq(generatedPdf)))
                .thenReturn(new CardPrintStorageService.StoredRunPdf("card-print/runs/run.pdf"));
        when(printRunRepository.save(any(CardPrintRun.class))).thenAnswer(invocation -> {
            CardPrintRun run = invocation.getArgument(0);
            savedWithoutPreassignedId.set(run.getId() == null);
            run.setId(generatedPrintRunId);
            return run;
        });

        CardPrintRunDto printRun = printRunService.create(new CardPrintRunCreateRequest(templateId, 2, slotRequests), "sub-admin");

        assertThat(savedWithoutPreassignedId).isTrue();
        assertThat(printRun.id()).isEqualTo(generatedPrintRunId);
        ArgumentCaptor<List<Card>> cardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cardInstanceService).createUnassignedInstances(cardsCaptor.capture(), any());
        assertThat(cardsCaptor.getValue()).hasSize(18);
        ArgumentCaptor<List<CardPrintPdfRenderer.PrintSheet>> sheetsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pdfRenderer).render(eq(template), eq(templatePdf), sheetsCaptor.capture());
        assertThat(sheetsCaptor.getValue())
                .extracting(CardPrintPdfRenderer.PrintSheet::frontPageStamp, CardPrintPdfRenderer.PrintSheet::backPageStamp)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(printRun.id() + "-1", printRun.id() + "-2"),
                        org.assertj.core.groups.Tuple.tuple(printRun.id() + "-3", printRun.id() + "-4")
                );
        verify(storageService).storeRun(eq(generatedPrintRunId), eq(generatedPdf));
        assertThat(printRun.sheetCount()).isEqualTo(2);
        assertThat(printRun.instanceCount()).isEqualTo(18);
        assertThat(printRun.pdfUrl()).isEqualTo("/api/admin/batches/" + generatedPrintRunId + "/pdf");
        assertThat(printRun.slots()).hasSize(18);
    }

    @Test
    void readsCompletedBatchPdfFromStorage() {
        UUID printRunId = UUID.randomUUID();
        CardPrintRun printRun = printRun(printRunId, template(UUID.randomUUID()), cards());
        printRun.setPdfObjectKey("card-print/runs/run.pdf");
        when(printRunRepository.findWithSlotsById(printRunId)).thenReturn(java.util.Optional.of(printRun));
        when(storageService.readPublicObject("card-print/runs/run.pdf")).thenReturn(new byte[]{'%','P','D','F'});

        byte[] pdf = printRunService.readBatchPdf(printRunId);

        assertThat(pdf).containsExactly('%','P','D','F');
        verify(storageService).readPublicObject("card-print/runs/run.pdf");
    }

    @Test
    void keepsFailedBatchAndInstancesWhenPdfGenerationFails() {
        UUID templateId = UUID.randomUUID();
        CardPrintTemplate template = template(templateId);
        List<CardPrintSlotCardRequest> slotRequests = slotRequests();
        List<Card> cards = cards();
        List<CardPrintTemplateService.SelectedCard> selectedCards = selectedCards(cards);
        List<CardInstance> instances = instances(cards, 9);
        UUID generatedPrintRunId = UUID.randomUUID();

        when(templateService.findTemplate(templateId)).thenReturn(template);
        when(templateService.resolveSelectedCards(slotRequests)).thenReturn(selectedCards);
        when(cardInstanceService.createUnassignedInstances(any(), any())).thenReturn(instances);
        when(templateService.loadImageBytes(cards)).thenThrow(new IllegalStateException("S3 unavailable"));
        when(printRunRepository.save(any(CardPrintRun.class))).thenAnswer(invocation -> {
            CardPrintRun run = invocation.getArgument(0);
            run.setId(generatedPrintRunId);
            return run;
        });

        CardPrintRunDto printRun = printRunService.create(new CardPrintRunCreateRequest(templateId, 1, slotRequests), "sub-admin");

        assertThat(printRun.id()).isEqualTo(generatedPrintRunId);
        assertThat(printRun.status()).isEqualTo(CardPrintRunStatus.FAILED);
        assertThat(printRun.pdfUrl()).isNull();
        assertThat(printRun.slots()).hasSize(9);
        verify(cardInstanceService).createUnassignedInstances(any(), any());
        verifyNoInteractions(pdfRenderer);
    }

    @Test
    void deletesFailedBatchAndItsUnassignedInstances() {
        UUID printRunId = UUID.randomUUID();
        CardPrintRun printRun = printRun(printRunId, template(UUID.randomUUID()), cards());
        printRun.setStatus(CardPrintRunStatus.FAILED);
        when(printRunRepository.findWithSlotsById(printRunId)).thenReturn(java.util.Optional.of(printRun));

        printRunService.deleteBatch(printRunId);

        verify(printRunRepository).delete(printRun);
        verify(printRunRepository).flush();
        verify(cardInstanceRepository).deleteAll(any());
    }

    @Test
    void deletesCompletedBatchAndItsUnassignedInstancesAndPdf() {
        UUID printRunId = UUID.randomUUID();
        CardPrintRun printRun = printRun(printRunId, template(UUID.randomUUID()), cards());
        printRun.setStatus(CardPrintRunStatus.COMPLETED);
        printRun.setPdfObjectKey("card-print/runs/run.pdf");
        when(printRunRepository.findWithSlotsById(printRunId)).thenReturn(java.util.Optional.of(printRun));

        printRunService.deleteBatch(printRunId);

        ArgumentCaptor<List<CardInstance>> instancesCaptor = ArgumentCaptor.forClass(List.class);
        verify(printRunRepository).delete(printRun);
        verify(printRunRepository).flush();
        verify(cardInstanceRepository).deleteAll(instancesCaptor.capture());
        assertThat(instancesCaptor.getValue()).hasSize(18);
        verify(storageService).deletePublicObjectQuietly("card-print/runs/run.pdf");
    }

    @Test
    void rejectsProcessingBatchDeletion() {
        UUID printRunId = UUID.randomUUID();
        CardPrintRun printRun = printRun(printRunId, template(UUID.randomUUID()), cards());
        printRun.setStatus(CardPrintRunStatus.PROCESSING);
        when(printRunRepository.findWithSlotsById(printRunId)).thenReturn(java.util.Optional.of(printRun));

        assertThatThrownBy(() -> printRunService.deleteBatch(printRunId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Seul un batch d'impression terminé ou en échec peut être supprimé.");

        verify(printRunRepository, never()).delete(any());
        verify(cardInstanceRepository, never()).deleteAll(any());
    }

    @Test
    void rejectsBatchDeletionWhenAnInstanceIsAlreadyAssigned() {
        UUID printRunId = UUID.randomUUID();
        CardPrintRun printRun = printRun(printRunId, template(UUID.randomUUID()), cards());
        printRun.setStatus(CardPrintRunStatus.COMPLETED);
        AppUser owner = new AppUser();
        owner.setId(UUID.randomUUID());
        printRun.getSlots().getFirst().getCardInstance().setOwner(owner);
        when(printRunRepository.findWithSlotsById(printRunId)).thenReturn(java.util.Optional.of(printRun));

        assertThatThrownBy(() -> printRunService.deleteBatch(printRunId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ce batch contient une instance déjà attribuée et ne peut pas être supprimé.");

        verify(printRunRepository, never()).delete(any());
        verify(cardInstanceRepository, never()).deleteAll(any());
    }

    @Test
    void findsBatchPageFromPrintedPageId() {
        UUID printRunId = UUID.randomUUID();
        CardPrintRun printRun = printRun(printRunId, template(UUID.randomUUID()), cards());
        when(printRunRepository.findWithSlotsById(printRunId)).thenReturn(java.util.Optional.of(printRun));

        CardPrintBatchPageLookupDto lookup = printRunService.findPage(printRunId + "-4");

        assertThat(lookup.batchId()).isEqualTo(printRunId);
        assertThat(lookup.pageNumber()).isEqualTo(4);
        assertThat(lookup.sheetIndex()).isEqualTo(1);
        assertThat(lookup.side()).isEqualTo("BACK");
        assertThat(lookup.page().slots()).hasSize(9);
    }

    private CardPrintTemplate template(UUID id) {
        CardPrintTemplate template = new CardPrintTemplate();
        template.setId(id);
        template.setName("Patron");
        template.setPdfObjectKey("card-print/templates/template/template.pdf");
        template.setPageCount(2);
        template.setEnabled(true);
        for (int index = 0; index < 9; index++) {
            CardPrintTemplateSlot slot = new CardPrintTemplateSlot();
            slot.setSlotIndex(index);
            slot.setXPt(10D + index);
            slot.setYPt(20D + index);
            slot.setWidthPt(100D);
            slot.setHeightPt(150D);
            template.addSlot(slot);
        }
        return template;
    }

    private List<CardPrintSlotCardRequest> slotRequests() {
        return java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new CardPrintSlotCardRequest(index, UUID.nameUUIDFromBytes(("card-" + index).getBytes())))
                .toList();
    }

    private List<Card> cards() {
        return slotRequests().stream()
                .map(request -> {
                    Card card = new Card();
                    card.setId(request.cardId());
                    card.setName("Carte " + request.slotIndex());
                    card.setImageObjectKey("cards/" + request.cardId() + "/original.jpg");
                    return card;
                })
                .toList();
    }

    private List<CardPrintTemplateService.SelectedCard> selectedCards(List<Card> cards) {
        return java.util.stream.IntStream.range(0, cards.size())
                .mapToObj(index -> new CardPrintTemplateService.SelectedCard(index, cards.get(index)))
                .toList();
    }

    private List<CardInstance> instances(List<Card> cards, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    CardInstance instance = new CardInstance();
                    instance.setId(UUID.randomUUID());
                    instance.setCard(cards.get(index % cards.size()));
                    instance.setPublicId("ABCDE-FGHJK-" + String.format("MNP%02d", index).replace('0', 'A'));
                    return instance;
                })
                .toList();
    }

    private CardPrintRun printRun(UUID id, CardPrintTemplate template, List<Card> cards) {
        CardPrintRun printRun = new CardPrintRun();
        printRun.setId(id);
        printRun.setTemplate(template);
        printRun.setSheetCount(2);
        printRun.setInstanceCount(18);
        printRun.setPdfUrl("https://s3.example.com/run.pdf");
        printRun.setPdfObjectKey("card-print/runs/run.pdf");
        List<CardInstance> instances = instances(cards, 18);
        int instanceIndex = 0;
        for (int sheetIndex = 0; sheetIndex < 2; sheetIndex++) {
            for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
                CardPrintRunSlot slot = new CardPrintRunSlot();
                slot.setPageIndex(sheetIndex);
                slot.setSlotIndex(slotIndex);
                slot.setCard(cards.get(slotIndex));
                slot.setCardInstance(instances.get(instanceIndex++));
                printRun.addSlot(slot);
            }
        }
        return printRun;
    }
}
