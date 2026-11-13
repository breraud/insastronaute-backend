package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateBackSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import fr.insastronaute.webapi.card.print.dto.CardPrintCodeSettingsDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintCodeSettingsRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintPreviewRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintQrSettingsDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintQrSettingsRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintSlotCardRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateCalibrationRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateSlotDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateSlotRequest;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CardPrintTemplateService {

    private static final double BOUNDS_TOLERANCE_PT = 0.1D;
    private static final String PREVIEW_PUBLIC_ID = "ABCDE-FGHJK-MNPQR";

    private final CardPrintTemplateRepository templateRepository;
    private final CardPrintRunRepository printRunRepository;
    private final CardRepository cardRepository;
    private final CardPrintStorageService storageService;
    private final CardPrintPdfRenderer pdfRenderer;

    public CardPrintTemplateService(
            CardPrintTemplateRepository templateRepository,
            CardPrintRunRepository printRunRepository,
            CardRepository cardRepository,
            CardPrintStorageService storageService,
            CardPrintPdfRenderer pdfRenderer
    ) {
        this.templateRepository = templateRepository;
        this.printRunRepository = printRunRepository;
        this.cardRepository = cardRepository;
        this.storageService = storageService;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional(readOnly = true)
    public List<CardPrintTemplateDto> findAll() {
        return templateRepository.findAll().stream()
                .sorted(Comparator.comparing(CardPrintTemplate::getCreatedAt).reversed())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CardPrintTemplateDto create(String name, MultipartFile pdf) {
        UUID templateId = UUID.randomUUID();
        CardPrintStorageService.StoredTemplatePdf storedPdf = storageService.storeTemplate(templateId, pdf);

        CardPrintTemplate template = new CardPrintTemplate();
        template.setId(templateId);
        template.setName(normalizeName(name));
        template.setPdfObjectKey(storedPdf.objectKey());
        template.setPdfUrl(storedPdf.url());
        template.setPageCount(storedPdf.pageCount());
        template.setPageWidthPt(storedPdf.pageWidthPt());
        template.setPageHeightPt(storedPdf.pageHeightPt());
        return toDto(templateRepository.save(template));
    }

    @Transactional
    public CardPrintTemplateDto updateCalibration(UUID templateId, CardPrintTemplateCalibrationRequest request) {
        CardPrintTemplate template = findTemplate(templateId);
        if (request == null) {
            throw new BadRequestException("La calibration du patron est requise.");
        }

        validateSlotsWithinPage(template, request.slots());
        applyCodeSettings(template, request.code());
        validateCodeWithinSlots(template, request.slots());
        validateBackCalibration(template, request.backSlots(), request.qr());

        template.setEnabled(request.enabled());
        template.setFitMode(request.fitMode());
        template.setBleedPt(request.bleedPt());
        applySlots(template, request.slots());
        applyBackSlots(template, request.backSlots());
        applyQrSettings(template, request.qr());
        return toDto(templateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public byte[] preview(UUID templateId, CardPrintPreviewRequest request) {
        CardPrintTemplate template = findTemplate(templateId);
        validateTemplateReady(template, false);
        List<SelectedCard> selectedCards = resolveSelectedCards(request == null ? null : request.slots());
        List<Card> cards = selectedCards.stream().map(SelectedCard::card).toList();
        Map<UUID, byte[]> imageBytesByCardId = loadImageBytes(cards);
        List<CardPrintPdfRenderer.PrintableCard> printableCards = selectedCards.stream()
                .map(selectedCard -> new CardPrintPdfRenderer.PrintableCard(
                        selectedCard.slotIndex(),
                        selectedCard.card().getName(),
                        imageBytesByCardId.get(selectedCard.card().getId()),
                        PREVIEW_PUBLIC_ID
                ))
                .toList();
        byte[] templatePdfBytes = storageService.readPublicObject(template.getPdfObjectKey());
        return pdfRenderer.render(template, templatePdfBytes, List.of(new CardPrintPdfRenderer.PrintSheet(
                printableCards,
                "TEST-1",
                "TEST-2"
        )));
    }

    @Transactional
    public void delete(UUID templateId) {
        CardPrintTemplate template = findTemplate(templateId);
        deleteTemplate(template);
    }

    @Transactional
    public void deleteAll(List<UUID> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            throw new BadRequestException("La liste des patrons à supprimer est requise.");
        }
        if (new HashSet<>(templateIds).size() != templateIds.size()) {
            throw new BadRequestException("La liste des patrons à supprimer contient des doublons.");
        }
        if (templateIds.size() > 100) {
            throw new BadRequestException("Vous pouvez supprimer au plus 100 patrons à la fois.");
        }

        List<CardPrintTemplate> templates = templateIds.stream()
                .map(this::findTemplate)
                .toList();
        templates.forEach(this::validateTemplateCanBeDeleted);
        templates.forEach(this::deleteDeletableTemplate);
    }

    @Transactional(readOnly = true)
    public CardPrintTemplate findTemplate(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Patron d'impression introuvable."));
    }

    @Transactional(readOnly = true)
    public List<SelectedCard> resolveSelectedCards(List<CardPrintSlotCardRequest> slots) {
        validateSelectedSlots(slots);
        Map<UUID, Card> cardsById = cardRepository.findAllById(slots.stream()
                        .map(CardPrintSlotCardRequest::cardId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

        return slots.stream()
                .sorted(Comparator.comparing(CardPrintSlotCardRequest::slotIndex))
                .map(slot -> {
                    Card card = cardsById.get(slot.cardId());
                    if (card == null) {
                        throw new NotFoundException("Carte introuvable.");
                    }
                    if (card.isDefaultCard()) {
                        throw new BadRequestException("La carte par défaut ne peut pas être imprimée.");
                    }
                    return new SelectedCard(slot.slotIndex(), card);
                })
                .toList();
    }

    public void validateTemplateReady(CardPrintTemplate template, boolean requireEnabled) {
        if (requireEnabled && !template.isEnabled()) {
            throw new BadRequestException("Le patron d'impression doit être activé avant un tirage.");
        }
        if (template.getSlots().size() != CardPrintTemplate.SLOT_COUNT) {
            throw new BadRequestException("Le patron d'impression doit contenir 9 emplacements calibrés.");
        }
        validateSlotIndexes(template.getSlots().stream().map(CardPrintTemplateSlot::getSlotIndex).toList());
        if (template.isQrEnabled()) {
            if (template.getBackSlots().size() != CardPrintTemplate.SLOT_COUNT) {
                throw new BadRequestException("Le patron d'impression doit contenir 9 emplacements verso calibrés.");
            }
            validateSlotIndexes(template.getBackSlots().stream().map(CardPrintTemplateBackSlot::getSlotIndex).toList());
        }
    }

    public Map<UUID, byte[]> loadImageBytes(List<Card> cards) {
        Map<UUID, byte[]> bytesByCardId = new HashMap<>();
        for (Card card : cards) {
            bytesByCardId.computeIfAbsent(card.getId(), ignored -> storageService.readPublicObject(card.getImageObjectKey()));
        }
        return bytesByCardId;
    }

    public CardPrintTemplateDto toDto(CardPrintTemplate template) {
        return new CardPrintTemplateDto(
                template.getId(),
                template.getName(),
                template.getPdfUrl(),
                template.getPageCount(),
                template.getPageWidthPt(),
                template.getPageHeightPt(),
                template.isEnabled(),
                template.getFitMode(),
                template.getBleedPt(),
                template.getSlots().stream()
                        .sorted(Comparator.comparing(CardPrintTemplateSlot::getSlotIndex))
                        .map(slot -> new CardPrintTemplateSlotDto(
                                slot.getId(),
                                slot.getSlotIndex(),
                                slot.getXPt(),
                                slot.getYPt(),
                                slot.getWidthPt(),
                                slot.getHeightPt()
                        ))
                        .toList(),
                new CardPrintCodeSettingsDto(
                        template.getCodeXPt(),
                        template.getCodeYPt(),
                        template.getCodeWidthPt(),
                        template.getCodeHeightPt(),
                        template.getCodeFontFamily(),
                        template.getCodeFontWeight(),
                        template.getCodeFontSizePt(),
                        template.getCodeColor(),
                        template.getCodeBackgroundColor(),
                        template.getCodeBackgroundOpacity(),
                        template.getCodeAlign()
                ),
                template.getBackSlots().stream()
                        .sorted(Comparator.comparing(CardPrintTemplateBackSlot::getSlotIndex))
                        .map(slot -> new CardPrintTemplateSlotDto(
                                slot.getId(), slot.getSlotIndex(), slot.getXPt(), slot.getYPt(),
                                slot.getWidthPt(), slot.getHeightPt()
                        ))
                        .toList(),
                new CardPrintQrSettingsDto(
                        template.isQrEnabled(), template.getQrCorner(), template.getQrSizePt(),
                        template.getQrMarginModules(), template.getQrInsetPt()
                ),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Le nom du patron est requis.");
        }
        String normalizedName = name.trim();
        if (normalizedName.length() > 120) {
            throw new BadRequestException("Le nom du patron ne doit pas dépasser 120 caractères.");
        }
        return normalizedName;
    }

    private void deleteTemplate(CardPrintTemplate template) {
        validateTemplateCanBeDeleted(template);
        deleteDeletableTemplate(template);
    }

    private void validateTemplateCanBeDeleted(CardPrintTemplate template) {
        if (printRunRepository.existsByTemplateId(template.getId())) {
            throw new ConflictException("Ce patron a déjà été utilisé pour un tirage et ne peut pas être supprimé.");
        }
    }

    private void deleteDeletableTemplate(CardPrintTemplate template) {
        String pdfObjectKey = template.getPdfObjectKey();
        templateRepository.delete(template);
        storageService.deletePublicObjectQuietly(pdfObjectKey);
    }

    private void validateSlotsWithinPage(CardPrintTemplate template, List<CardPrintTemplateSlotRequest> slots) {
        if (slots == null || slots.size() != CardPrintTemplate.SLOT_COUNT) {
            throw new BadRequestException("Le patron doit contenir exactement 9 emplacements.");
        }
        validateSlotIndexes(slots.stream().map(CardPrintTemplateSlotRequest::slotIndex).toList());

        for (CardPrintTemplateSlotRequest slot : slots) {
            if (slot.xPt() + slot.widthPt() > template.getPageWidthPt() + BOUNDS_TOLERANCE_PT
                    || slot.yPt() + slot.heightPt() > template.getPageHeightPt() + BOUNDS_TOLERANCE_PT) {
                throw new BadRequestException("Un emplacement dépasse les limites de la page PDF.");
            }
        }
    }

    private void validateCodeWithinSlots(CardPrintTemplate template, List<CardPrintTemplateSlotRequest> slots) {
        for (CardPrintTemplateSlotRequest slot : slots) {
            if (template.getCodeXPt() + template.getCodeWidthPt() > slot.widthPt() + BOUNDS_TOLERANCE_PT
                    || template.getCodeYPt() + template.getCodeHeightPt() > slot.heightPt() + BOUNDS_TOLERANCE_PT) {
                throw new BadRequestException("La zone de code dépasse au moins un emplacement de carte.");
            }
        }
    }

    private void validateBackCalibration(
            CardPrintTemplate template,
            List<CardPrintTemplateSlotRequest> backSlots,
            CardPrintQrSettingsRequest qr
    ) {
        boolean qrEnabled = qr != null && Boolean.TRUE.equals(qr.enabled());
        if ((backSlots == null || backSlots.isEmpty()) && !qrEnabled) return;
        validateSlotsWithinPage(template, backSlots);
        if (qrEnabled) {
            if (qr.corner() == null || qr.sizePt() == null || qr.sizePt() < 8D || qr.sizePt() > 300D) {
                throw new BadRequestException("La configuration du QR code est invalide.");
            }
            int marginModules = qr.marginModules() == null ? 4 : qr.marginModules();
            double insetPt = qr.insetPt() == null ? 4D : qr.insetPt();
            if (marginModules < 0 || marginModules > 8 || insetPt < 0D || insetPt > 150D) {
                throw new BadRequestException("La bordure ou l'offset du QR code est invalide.");
            }
            for (CardPrintTemplateSlotRequest slot : backSlots) {
                if (qr.sizePt() > slot.widthPt() - 2D * insetPt + BOUNDS_TOLERANCE_PT
                        || qr.sizePt() > slot.heightPt() - 2D * insetPt + BOUNDS_TOLERANCE_PT) {
                    throw new BadRequestException("Le QR code dépasse au moins un emplacement verso.");
                }
            }
        }
    }

    private void validateSelectedSlots(List<CardPrintSlotCardRequest> slots) {
        if (slots == null || slots.size() != CardPrintTemplate.SLOT_COUNT) {
            throw new BadRequestException("La sélection doit contenir exactement 9 cartes.");
        }
        validateSlotIndexes(slots.stream().map(CardPrintSlotCardRequest::slotIndex).toList());
    }

    private void validateSlotIndexes(List<Integer> indexes) {
        Set<Integer> uniqueIndexes = new HashSet<>(indexes);
        if (uniqueIndexes.size() != CardPrintTemplate.SLOT_COUNT) {
            throw new BadRequestException("Les emplacements doivent être uniques.");
        }
        for (int index = 0; index < CardPrintTemplate.SLOT_COUNT; index++) {
            if (!uniqueIndexes.contains(index)) {
                throw new BadRequestException("Les emplacements doivent couvrir les index 0 à 8.");
            }
        }
    }

    private void applyCodeSettings(CardPrintTemplate template, CardPrintCodeSettingsRequest code) {
        if (code == null) {
            throw new BadRequestException("La configuration du code est requise.");
        }
        template.setCodeXPt(code.xPt());
        template.setCodeYPt(code.yPt());
        template.setCodeWidthPt(code.widthPt());
        template.setCodeHeightPt(code.heightPt());
        template.setCodeFontFamily(code.fontFamily());
        template.setCodeFontWeight(code.fontWeight());
        template.setCodeFontSizePt(code.fontSizePt());
        template.setCodeColor(code.color().toUpperCase());
        template.setCodeBackgroundColor(code.backgroundColor() == null || code.backgroundColor().isBlank()
                ? null
                : code.backgroundColor().toUpperCase());
        template.setCodeBackgroundOpacity(code.backgroundOpacity());
        template.setCodeAlign(code.align());
    }

    private void applyQrSettings(CardPrintTemplate template, CardPrintQrSettingsRequest qr) {
        if (qr == null) {
            template.setQrEnabled(false);
            return;
        }
        template.setQrEnabled(Boolean.TRUE.equals(qr.enabled()));
        template.setQrCorner(qr.corner());
        template.setQrSizePt(qr.sizePt());
        template.setQrMarginModules(qr.marginModules());
        template.setQrInsetPt(qr.insetPt());
    }

    private CardPrintTemplateSlot toSlot(CardPrintTemplateSlotRequest request) {
        CardPrintTemplateSlot slot = new CardPrintTemplateSlot();
        applySlotValues(slot, request);
        return slot;
    }

    private void applySlots(CardPrintTemplate template, List<CardPrintTemplateSlotRequest> requestedSlots) {
        Map<Integer, CardPrintTemplateSlot> existingSlots = template.getSlots().stream()
                .collect(Collectors.toMap(CardPrintTemplateSlot::getSlotIndex, Function.identity()));
        Set<Integer> requestedIndexes = requestedSlots.stream()
                .map(CardPrintTemplateSlotRequest::slotIndex)
                .collect(Collectors.toSet());

        template.getSlots().removeIf(slot -> !requestedIndexes.contains(slot.getSlotIndex()));

        requestedSlots.stream()
                .sorted(Comparator.comparing(CardPrintTemplateSlotRequest::slotIndex))
                .forEach(request -> {
                    CardPrintTemplateSlot slot = existingSlots.get(request.slotIndex());
                    if (slot == null) {
                        template.addSlot(toSlot(request));
                        return;
                    }
                    applySlotValues(slot, request);
                });
    }

    private void applySlotValues(CardPrintTemplateSlot slot, CardPrintTemplateSlotRequest request) {
        slot.setSlotIndex(request.slotIndex());
        slot.setXPt(request.xPt());
        slot.setYPt(request.yPt());
        slot.setWidthPt(request.widthPt());
        slot.setHeightPt(request.heightPt());
    }

    private void applyBackSlots(CardPrintTemplate template, List<CardPrintTemplateSlotRequest> requestedSlots) {
        if (requestedSlots == null || requestedSlots.isEmpty()) {
            template.getBackSlots().clear();
            return;
        }
        Map<Integer, CardPrintTemplateBackSlot> existingSlots = template.getBackSlots().stream()
                .collect(Collectors.toMap(CardPrintTemplateBackSlot::getSlotIndex, Function.identity()));
        Set<Integer> requestedIndexes = requestedSlots.stream()
                .map(CardPrintTemplateSlotRequest::slotIndex)
                .collect(Collectors.toSet());
        template.getBackSlots().removeIf(slot -> !requestedIndexes.contains(slot.getSlotIndex()));
        requestedSlots.stream()
                .sorted(Comparator.comparing(CardPrintTemplateSlotRequest::slotIndex))
                .forEach(request -> {
                    CardPrintTemplateBackSlot slot = existingSlots.get(request.slotIndex());
                    if (slot == null) {
                        slot = new CardPrintTemplateBackSlot();
                        template.addBackSlot(slot);
                    }
                    slot.setSlotIndex(request.slotIndex());
                    slot.setXPt(request.xPt());
                    slot.setYPt(request.yPt());
                    slot.setWidthPt(request.widthPt());
                    slot.setHeightPt(request.heightPt());
                });
    }

    public record SelectedCard(Integer slotIndex, Card card) {
    }
}
