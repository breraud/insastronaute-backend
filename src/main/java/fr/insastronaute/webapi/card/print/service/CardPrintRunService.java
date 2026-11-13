package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchPageDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchPageLookupDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunCreateRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunSlotDto;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
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
public class CardPrintRunService {

    private final CardPrintRunRepository printRunRepository;
    private final CardPrintTemplateService templateService;
    private final CardInstanceService cardInstanceService;
    private final CardInstanceRepository cardInstanceRepository;
    private final CardPrintStorageService storageService;
    private final CardPrintPdfRenderer pdfRenderer;

    public CardPrintRunService(
            CardPrintRunRepository printRunRepository,
            CardPrintTemplateService templateService,
            CardInstanceService cardInstanceService,
            CardInstanceRepository cardInstanceRepository,
            CardPrintStorageService storageService,
            CardPrintPdfRenderer pdfRenderer
    ) {
        this.printRunRepository = printRunRepository;
        this.templateService = templateService;
        this.cardInstanceService = cardInstanceService;
        this.cardInstanceRepository = cardInstanceRepository;
        this.storageService = storageService;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional
    public CardPrintRunDto create(CardPrintRunCreateRequest request, String actorKeycloakSub) {
        if (request == null) {
            throw new BadRequestException("La demande de tirage est requise.");
        }

        CardPrintTemplate template = templateService.findTemplate(request.templateId());
        templateService.validateTemplateReady(template, true);
        List<CardPrintTemplateService.SelectedCard> selectedCards = templateService.resolveSelectedCards(request.slots()).stream()
                .sorted(Comparator.comparing(CardPrintTemplateService.SelectedCard::slotIndex))
                .toList();
        int instanceCount = request.sheetCount() * selectedCards.size();

        List<Card> cardsToInstantiate = new ArrayList<>(instanceCount);
        for (int pageIndex = 0; pageIndex < request.sheetCount(); pageIndex++) {
            selectedCards.stream()
                    .map(CardPrintTemplateService.SelectedCard::card)
                    .forEach(cardsToInstantiate::add);
        }
        List<CardInstance> instances = cardInstanceService.createUnassignedInstances(cardsToInstantiate, actorKeycloakSub);

        CardPrintRun printRun = new CardPrintRun();
        printRun.setTemplate(template);
        printRun.setSheetCount(request.sheetCount());
        printRun.setInstanceCount(instanceCount);
        printRun.setStatus(CardPrintRunStatus.PROCESSING);
        printRun = printRunRepository.save(printRun);

        int instanceIndex = 0;
        for (int pageIndex = 0; pageIndex < request.sheetCount(); pageIndex++) {
            for (CardPrintTemplateService.SelectedCard selectedCard : selectedCards) {
                CardInstance instance = instances.get(instanceIndex++);
                CardPrintRunSlot slot = new CardPrintRunSlot();
                slot.setPageIndex(pageIndex);
                slot.setSlotIndex(selectedCard.slotIndex());
                slot.setCard(selectedCard.card());
                slot.setCardInstance(instance);
                printRun.addSlot(slot);
            }
        }

        try {
            Map<UUID, byte[]> imageBytesByCardId = templateService.loadImageBytes(selectedCards.stream()
                    .map(CardPrintTemplateService.SelectedCard::card)
                    .toList());
            List<CardPrintPdfRenderer.PrintSheet> sheets = buildPrintSheets(printRun, imageBytesByCardId);
            byte[] templatePdfBytes = storageService.readPublicObject(template.getPdfObjectKey());
            byte[] generatedPdf = pdfRenderer.render(template, templatePdfBytes, sheets);
            CardPrintStorageService.StoredRunPdf storedRunPdf = storageService.storeRun(printRun.getId(), generatedPdf);
            printRun.setPdfObjectKey(storedRunPdf.objectKey());
            printRun.setPdfUrl(pdfUrl(printRun));
            printRun.setStatus(CardPrintRunStatus.COMPLETED);
            printRun.setCompletedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        } catch (RuntimeException exception) {
            printRun.setStatus(CardPrintRunStatus.FAILED);
            printRun.setPdfObjectKey(null);
            printRun.setPdfUrl(null);
            printRun.setCompletedAt(null);
        }

        return toDto(printRun);
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

    @Transactional(readOnly = true)
    public List<CardPrintBatchDto> findAllBatches() {
        return printRunRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toBatchDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CardPrintBatchDto findBatch(UUID batchId) {
        return toBatchDto(findPrintRun(batchId));
    }

    @Transactional(readOnly = true)
    public List<CardPrintRunSlotDto> findBatchCards(UUID batchId) {
        return toSlotDtos(findPrintRun(batchId));
    }

    @Transactional(readOnly = true)
    public byte[] readBatchPdf(UUID batchId) {
        CardPrintRun printRun = findPrintRun(batchId);
        if (printRun.getPdfObjectKey() == null || printRun.getPdfObjectKey().isBlank()) {
            throw new NotFoundException("PDF d'impression introuvable.");
        }
        return storageService.readPublicObject(printRun.getPdfObjectKey());
    }

    @Transactional
    public void deleteBatch(UUID batchId) {
        CardPrintRun printRun = findPrintRun(batchId);
        if (!canDeleteBatch(printRun.getStatus())) {
            throw new ConflictException("Seul un batch d'impression terminé ou en échec peut être supprimé.");
        }

        List<CardInstance> instances = printRun.getSlots().stream()
                .map(CardPrintRunSlot::getCardInstance)
                .distinct()
                .toList();
        boolean hasAssignedInstance = instances.stream().anyMatch(instance -> instance.getOwner() != null);
        if (hasAssignedInstance) {
            throw new ConflictException("Ce batch contient une instance déjà attribuée et ne peut pas être supprimé.");
        }

        String pdfObjectKey = printRun.getPdfObjectKey();
        printRun.getSlots().clear();
        printRunRepository.delete(printRun);
        printRunRepository.flush();
        cardInstanceRepository.deleteAll(instances);
        if (pdfObjectKey != null && !pdfObjectKey.isBlank()) {
            storageService.deletePublicObjectQuietly(pdfObjectKey);
        }
    }

    private boolean canDeleteBatch(CardPrintRunStatus status) {
        return status == CardPrintRunStatus.FAILED || status == CardPrintRunStatus.COMPLETED;
    }

    @Transactional(readOnly = true)
    public CardPrintBatchPageLookupDto findPage(String printPageId) {
        ParsedPrintPageId parsedPageId = parsePrintPageId(printPageId);
        CardPrintRun printRun = findPrintRun(parsedPageId.batchId());
        CardPrintBatchPageDto page = toPageDto(printRun, parsedPageId.pageNumber());
        return new CardPrintBatchPageLookupDto(
                printRun.getId(),
                page.printPageId(),
                page.pageNumber(),
                page.sheetIndex(),
                page.side(),
                pdfUrl(printRun),
                page
        );
    }

    private CardPrintRunDto toDto(CardPrintRun printRun) {
        return new CardPrintRunDto(
                printRun.getId(),
                printRun.getTemplate().getId(),
                printRun.getTemplate().getName(),
                printRun.getSheetCount(),
                printRun.getInstanceCount(),
                printRun.getStatus(),
                pdfUrl(printRun),
                toSlotDtos(printRun),
                printRun.getCreatedAt(),
                printRun.getCompletedAt()
        );
    }

    private CardPrintBatchDto toBatchDto(CardPrintRun printRun) {
        List<CardPrintRunSlotDto> slots = toSlotDtos(printRun);
        return new CardPrintBatchDto(
                printRun.getId(),
                printRun.getTemplate().getId(),
                printRun.getTemplate().getName(),
                printRun.getSheetCount(),
                printRun.getSheetCount() * 2,
                printRun.getInstanceCount(),
                printRun.getStatus(),
                pdfUrl(printRun),
                java.util.stream.IntStream.rangeClosed(1, printRun.getSheetCount() * 2)
                        .mapToObj(pageNumber -> toPageDto(printRun, pageNumber))
                        .toList(),
                slots,
                printRun.getCreatedAt(),
                printRun.getCompletedAt()
        );
    }

    private CardPrintRun findPrintRun(UUID batchId) {
        return printRunRepository.findWithSlotsById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch d'impression introuvable."));
    }

    private String pdfUrl(CardPrintRun printRun) {
        if (printRun.getPdfObjectKey() == null || printRun.getPdfObjectKey().isBlank()) {
            return null;
        }
        return "/api/admin/batches/" + printRun.getId() + "/pdf";
    }

    private CardPrintBatchPageDto toPageDto(CardPrintRun printRun, int pageNumber) {
        int pageCount = printRun.getSheetCount() * 2;
        if (pageNumber < 1 || pageNumber > pageCount) {
            throw new NotFoundException("Page d'impression introuvable.");
        }

        int sheetIndex = (pageNumber - 1) / 2;
        String side = pageNumber % 2 == 1 ? "FRONT" : "BACK";
        List<CardPrintRunSlotDto> slots = printRun.getSlots().stream()
                .filter(slot -> slot.getPageIndex() == sheetIndex)
                .sorted(Comparator.comparing(CardPrintRunSlot::getSlotIndex))
                .map(this::toSlotDto)
                .toList();
        return new CardPrintBatchPageDto(
                printPageId(printRun.getId(), pageNumber),
                pageNumber,
                sheetIndex,
                side,
                slots
        );
    }

    private List<CardPrintRunSlotDto> toSlotDtos(CardPrintRun printRun) {
        return printRun.getSlots().stream()
                .sorted(Comparator.comparing(CardPrintRunSlot::getPageIndex)
                        .thenComparing(CardPrintRunSlot::getSlotIndex))
                .map(this::toSlotDto)
                .toList();
    }

    private CardPrintRunSlotDto toSlotDto(CardPrintRunSlot slot) {
        return new CardPrintRunSlotDto(
                slot.getPageIndex(),
                slot.getSlotIndex(),
                slot.getCard().getId(),
                slot.getCard().getName(),
                slot.getCardInstance().getId(),
                slot.getCardInstance().getPublicId()
        );
    }

    private ParsedPrintPageId parsePrintPageId(String printPageId) {
        if (printPageId == null || printPageId.isBlank()) {
            throw new BadRequestException("L'identifiant de page d'impression est requis.");
        }

        String trimmed = printPageId.trim();
        int separatorIndex = trimmed.lastIndexOf('-');
        if (separatorIndex < 1 || separatorIndex == trimmed.length() - 1) {
            throw new BadRequestException("L'identifiant de page d'impression est invalide.");
        }

        try {
            UUID batchId = UUID.fromString(trimmed.substring(0, separatorIndex));
            int pageNumber = Integer.parseInt(trimmed.substring(separatorIndex + 1));
            return new ParsedPrintPageId(batchId, pageNumber);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("L'identifiant de page d'impression est invalide.");
        }
    }

    private String printPageId(UUID printRunId, int pageNumber) {
        return printRunId + "-" + pageNumber;
    }

    private record ParsedPrintPageId(UUID batchId, int pageNumber) {
    }
}
