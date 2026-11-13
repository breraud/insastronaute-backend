package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CardPrintPdfRegenerationService {

    private final CardPrintRunRepository printRunRepository;
    private final CardPrintTemplateService templateService;
    private final CardPrintStorageService storageService;
    private final CardPrintPdfRenderer pdfRenderer;

    public CardPrintPdfRegenerationService(
            CardPrintRunRepository printRunRepository,
            CardPrintTemplateService templateService,
            CardPrintStorageService storageService,
            CardPrintPdfRenderer pdfRenderer
    ) {
        this.printRunRepository = printRunRepository;
        this.templateService = templateService;
        this.storageService = storageService;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional
    public CardPrintPdfRegenerationDto regenerateUncompressedRunPdfs() {
        int scannedCount = 0;
        int skippedCount = 0;
        int regeneratedCount = 0;
        int failedCount = 0;

        List<CardPrintRun> printRuns = printRunRepository.findAllByOrderByCreatedAtDesc();
        for (CardPrintRun printRun : printRuns) {
            if (!isRegenerationCandidate(printRun)) {
                continue;
            }

            scannedCount++;
            try {
                byte[] existingPdf = storageService.readPublicObject(printRun.getPdfObjectKey());
                if (pdfRenderer.isRenderedWithCurrentCompression(existingPdf)) {
                    skippedCount++;
                    continue;
                }

                regenerate(printRun);
                regeneratedCount++;
            } catch (RuntimeException exception) {
                failedCount++;
            }
        }

        return new CardPrintPdfRegenerationDto(scannedCount, skippedCount, regeneratedCount, failedCount);
    }

    @Transactional
    public CardPrintPdfRegenerationDto regenerateTemplateBatches(UUID templateId) {
        templateService.findTemplate(templateId);
        List<CardPrintRun> printRuns = printRunRepository.findAllByTemplateIdOrderByCreatedAtDesc(templateId);
        int skippedCount = 0;
        int regeneratedCount = 0;
        int failedCount = 0;
        for (CardPrintRun printRun : printRuns) {
            if (!isRegenerationCandidate(printRun)) {
                skippedCount++;
                continue;
            }
            try {
                regenerate(printRun);
                regeneratedCount++;
            } catch (RuntimeException exception) {
                failedCount++;
            }
        }
        return new CardPrintPdfRegenerationDto(printRuns.size(), skippedCount, regeneratedCount, failedCount);
    }

    @Transactional
    public CardPrintPdfRegenerationDto regenerateBatch(UUID batchId) {
        CardPrintRun printRun = printRunRepository.findWithSlotsById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch d'impression introuvable."));
        if (!isRegenerationCandidate(printRun)) {
            throw new BadRequestException("Seul un batch d'impression terminé avec un PDF peut être régénéré.");
        }
        regenerate(printRun);
        return new CardPrintPdfRegenerationDto(1, 0, 1, 0);
    }

    private boolean isRegenerationCandidate(CardPrintRun printRun) {
        return printRun.getStatus() == CardPrintRunStatus.COMPLETED
                && printRun.getPdfObjectKey() != null
                && !printRun.getPdfObjectKey().isBlank();
    }

    private void regenerate(CardPrintRun printRun) {
        List<Card> cards = printRun.getSlots().stream()
                .map(CardPrintRunSlot::getCard)
                .distinct()
                .toList();
        Map<UUID, byte[]> imageBytesByCardId = templateService.loadImageBytes(cards);
        List<CardPrintPdfRenderer.PrintSheet> sheets = buildPrintSheets(printRun, imageBytesByCardId);
        byte[] templatePdfBytes = storageService.readPublicObject(printRun.getTemplate().getPdfObjectKey());
        byte[] generatedPdf = pdfRenderer.render(printRun.getTemplate(), templatePdfBytes, sheets);
        CardPrintStorageService.StoredRunPdf storedRunPdf = storageService.storeRun(printRun.getId(), generatedPdf);
        printRun.setPdfObjectKey(storedRunPdf.objectKey());
        printRun.setPdfUrl("/api/admin/batches/" + printRun.getId() + "/pdf");
        printRun.setCompletedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
    }

    private List<CardPrintPdfRenderer.PrintSheet> buildPrintSheets(CardPrintRun printRun, Map<UUID, byte[]> imageBytesByCardId) {
        List<CardPrintPdfRenderer.PrintSheet> sheets = new ArrayList<>(printRun.getSheetCount());
        for (int pageIndex = 0; pageIndex < printRun.getSheetCount(); pageIndex++) {
            int currentPageIndex = pageIndex;
            List<CardPrintPdfRenderer.PrintableCard> printableCards = printRun.getSlots().stream()
                    .filter(slot -> slot.getPageIndex() == currentPageIndex)
                    .sorted(Comparator.comparing(CardPrintRunSlot::getSlotIndex))
                    .map(slot -> new CardPrintPdfRenderer.PrintableCard(
                            slot.getSlotIndex(),
                            slot.getCard().getName(),
                            imageBytesByCardId.get(slot.getCard().getId()),
                            slot.getCardInstance().getPublicId()
                    ))
                    .toList();
            sheets.add(new CardPrintPdfRenderer.PrintSheet(
                    printableCards,
                    printPageId(printRun.getId(), pageIndex * 2 + 1),
                    printPageId(printRun.getId(), pageIndex * 2 + 2)
            ));
        }
        return sheets;
    }

    private String printPageId(UUID batchId, int pageNumber) {
        return batchId + "-" + pageNumber;
    }
}
