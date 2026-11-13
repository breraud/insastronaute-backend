package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardPrintPdfRegenerationServiceTest {

    @Mock CardPrintRunRepository printRunRepository;
    @Mock CardPrintTemplateService templateService;
    @Mock CardPrintStorageService storageService;
    @Mock CardPrintPdfRenderer pdfRenderer;
    @InjectMocks CardPrintPdfRegenerationService service;

    @Test
    void forceRegeneratesOneBatchWithItsExistingPublicIds() {
        CardPrintRun run = completedRun();
        Card card = run.getSlots().getFirst().getCard();
        when(printRunRepository.findWithSlotsById(run.getId())).thenReturn(Optional.of(run));
        when(templateService.loadImageBytes(List.of(card))).thenReturn(Map.of(card.getId(), new byte[]{1}));
        when(storageService.readPublicObject(run.getTemplate().getPdfObjectKey())).thenReturn(new byte[]{2});
        when(pdfRenderer.render(eq(run.getTemplate()), any(), any())).thenReturn(new byte[]{3});
        when(storageService.storeRun(eq(run.getId()), any())).thenReturn(
                new CardPrintStorageService.StoredRunPdf("card-print/runs/new.pdf")
        );

        CardPrintPdfRegenerationDto result = service.regenerateBatch(run.getId());

        assertThat(result.regeneratedCount()).isEqualTo(1);
        ArgumentCaptor<List<CardPrintPdfRenderer.PrintSheet>> sheets = ArgumentCaptor.forClass(List.class);
        verify(pdfRenderer).render(eq(run.getTemplate()), any(), sheets.capture());
        assertThat(sheets.getValue().getFirst().cards().getFirst().publicId()).isEqualTo("ABCDE-FGHJK-MNPQR");
        assertThat(run.getPdfObjectKey()).isEqualTo("card-print/runs/new.pdf");
    }

    @Test
    void forceRegeneratesAllCompletedBatchesForTemplate() {
        CardPrintRun run = completedRun();
        UUID templateId = run.getTemplate().getId();
        when(templateService.findTemplate(templateId)).thenReturn(run.getTemplate());
        when(printRunRepository.findAllByTemplateIdOrderByCreatedAtDesc(templateId)).thenReturn(List.of(run));
        Card card = run.getSlots().getFirst().getCard();
        when(templateService.loadImageBytes(List.of(card))).thenReturn(Map.of(card.getId(), new byte[]{1}));
        when(storageService.readPublicObject(run.getTemplate().getPdfObjectKey())).thenReturn(new byte[]{2});
        when(pdfRenderer.render(eq(run.getTemplate()), any(), any())).thenReturn(new byte[]{3});
        when(storageService.storeRun(eq(run.getId()), any())).thenReturn(
                new CardPrintStorageService.StoredRunPdf("card-print/runs/new.pdf")
        );

        CardPrintPdfRegenerationDto result = service.regenerateTemplateBatches(templateId);

        assertThat(result).isEqualTo(new CardPrintPdfRegenerationDto(1, 0, 1, 0));
    }

    private CardPrintRun completedRun() {
        CardPrintTemplate template = new CardPrintTemplate();
        template.setId(UUID.randomUUID());
        template.setPdfObjectKey("card-print/templates/template.pdf");
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte");
        card.setImageObjectKey("cards/card.jpg");
        CardInstance instance = new CardInstance();
        instance.setPublicId("ABCDE-FGHJK-MNPQR");
        CardPrintRun run = new CardPrintRun();
        run.setId(UUID.randomUUID());
        run.setTemplate(template);
        run.setSheetCount(1);
        run.setStatus(CardPrintRunStatus.COMPLETED);
        run.setPdfObjectKey("card-print/runs/old.pdf");
        CardPrintRunSlot slot = new CardPrintRunSlot();
        slot.setPageIndex(0);
        slot.setSlotIndex(0);
        slot.setCard(card);
        slot.setCardInstance(instance);
        run.addSlot(slot);
        return run;
    }
}
