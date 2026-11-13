package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.domain.Card;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class CardDatasetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDatasetService.class);
    private static final int MAX_SHEETS_PER_REQUEST = 20;
    private static final int SHEET_ID_ATTEMPTS = 50;

    private final CardRepository cardRepository;
    private final CardPrintRunRepository printRunRepository;
    private final CardPrintTemplateRepository templateRepository;
    private final CardPrintTemplateService templateService;
    private final CardDatasetPdfRenderer pdfRenderer;
    private final CardDatasetSheetRepository sheetRepository;
    private final CardDatasetSheetStorageService sheetStorage;
    private final CardDatasetCropRepository cropRepository;
    private final CardDatasetCropStorageService cropStorage;
    private final Random random = new Random();

    public CardDatasetService(
            CardRepository cardRepository,
            CardPrintRunRepository printRunRepository,
            CardPrintTemplateRepository templateRepository,
            CardPrintTemplateService templateService,
            CardDatasetPdfRenderer pdfRenderer,
            CardDatasetSheetRepository sheetRepository,
            CardDatasetSheetStorageService sheetStorage,
            CardDatasetCropRepository cropRepository,
            CardDatasetCropStorageService cropStorage
    ) {
        this.cardRepository = cardRepository;
        this.printRunRepository = printRunRepository;
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.pdfRenderer = pdfRenderer;
        this.sheetRepository = sheetRepository;
        this.sheetStorage = sheetStorage;
        this.cropRepository = cropRepository;
        this.cropStorage = cropStorage;
    }

    /**
     * Une planche de brouillon : les codes qu'elle porte n'existent pas en base.
     *
     * <p>C'est deliberé. Imprimer de vrais codes de carte sur du papier de brouillon serait
     * une fuite, et l'argument de distribution ne tient pas : les {@code publicId} reels sont
     * tires par exactement le meme tirage uniforme. Ce que la planche doit faire, en revanche,
     * c'est <strong>se souvenir</strong> de ce qu'elle a imprime -- sinon elle n'est pas
     * etiquetable une fois photographiee.
     */
    @Transactional
    public GeneratedDatasetSheet generateTestDatasetPdf() {
        return generateTestDataset();
    }

    @Transactional
    public CardDatasetSheetSummaryDto createTestSheet() {
        GeneratedDataset generated = generateTestDatasetWithEntity();
        return toSummary(generated.sheet(), 0);
    }

    private GeneratedDatasetSheet generateTestDataset() {
        GeneratedDataset generated = generateTestDatasetWithEntity();
        return new GeneratedDatasetSheet(generated.sheet().getSheetId(), generated.pdf());
    }

    private GeneratedDataset generateTestDatasetWithEntity() {
        CardPrintTemplate activeTemplate = activeTemplate();

        List<Card> allCards = cardRepository.findByEnabledTrue();
        if (allCards.isEmpty()) {
            throw new NotFoundException("Aucune carte active trouvée.");
        }

        Collections.shuffle(allCards);

        List<Card> selected = new ArrayList<>();
        int sheetSize = CardDatasetPdfRenderer.cardsPerPage();
        for (int i = 0; i < sheetSize; i++) {
            selected.add(allCards.get(i % allCards.size()));
        }

        Map<UUID, byte[]> imageBytes = templateService.loadImageBytes(selected.stream().distinct().toList());

        List<CardDatasetPdfRenderer.DatasetCardInfo> infos = selected.stream()
                .map(card -> new CardDatasetPdfRenderer.DatasetCardInfo(
                        generateRandomCode(),
                        imageBytes.get(card.getId())
                ))
                .toList();

        CardDatasetSheet sheet = buildSheet(infos, selected, null);
        byte[] pdf = pdfRenderer.render(infos, activeTemplate, sheet.getSheetId());
        sheet = persistOriginalPdf(sheet, pdf);
        return new GeneratedDataset(sheet, pdf);
    }

    @Transactional
    public GeneratedDatasetSheet generateBatchDatasetPdf(UUID batchId) {
        CardPrintTemplate activeTemplate = activeTemplate();

        CardPrintRun run = printRunRepository.findWithSlotsById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch introuvable."));

        List<Card> cards = run.getSlots().stream()
                .map(CardPrintRunSlot::getCard)
                .distinct()
                .toList();

        Map<UUID, byte[]> imageBytes = templateService.loadImageBytes(cards);

        List<CardDatasetPdfRenderer.DatasetCardInfo> infos = run.getSlots().stream()
                .map(slot -> new CardDatasetPdfRenderer.DatasetCardInfo(
                        slot.getCardInstance().getPublicId(),
                        imageBytes.get(slot.getCard().getId())
                ))
                .toList();

        List<Card> printedCards = run.getSlots().stream()
                .map(CardPrintRunSlot::getCard)
                .toList();

        CardDatasetSheet sheet = buildSheet(infos, printedCards, run);
        byte[] pdf = pdfRenderer.render(infos, activeTemplate, sheet.getSheetId());
        sheet = persistOriginalPdf(sheet, pdf);
        return new GeneratedDatasetSheet(sheet.getSheetId(), pdf);
    }

    /**
     * Genere plusieurs planches en une passe.
     *
     * <p>Une seule transaction : si la septieme echoue, les six precedentes sont annulees et
     * leurs PDF retires du stockage. On repart propre plutot qu'avec un lot a moitie cree.
     */
    @Transactional
    public List<CardDatasetSheetSummaryDto> createTestSheets(int count) {
        if (count < 1 || count > MAX_SHEETS_PER_REQUEST) {
            throw new BadRequestException(
                    "Le nombre de planches doit etre compris entre 1 et " + MAX_SHEETS_PER_REQUEST + ".");
        }
        List<CardDatasetSheetSummaryDto> created = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            created.add(createTestSheet());
        }
        return created;
    }

    /**
     * Un seul PDF pour plusieurs planches.
     *
     * <p>Quand toutes les planches demandees se souviennent des cartes qu'elles portent, elles
     * sont re-rendues d'un seul tenant : le document ne contient alors qu'une image par carte
     * distincte, au lieu d'une par emplacement. Une planche anterieure a cette memoire ne peut
     * pas etre re-rendue, et la selection retombe sur la concatenation des PDF stockes.
     */
    @Transactional(readOnly = true)
    public byte[] renderSheetsPdf(List<String> rawSheetIds) {
        if (rawSheetIds == null || rawSheetIds.isEmpty()) {
            throw new BadRequestException("Aucune planche demandee.");
        }
        if (rawSheetIds.size() > MAX_SHEETS_PER_REQUEST) {
            throw new BadRequestException(
                    "Pas plus de " + MAX_SHEETS_PER_REQUEST + " planches par PDF groupe.");
        }

        return concatenateStoredPdfs(rawSheetIds.stream().map(this::requireSheet).toList());
    }

    /**
     * Re-rend le PDF d'une planche avec le patron courant, et remplace l'original archive.
     *
     * <p>Explicite, comme {@code CardPrintPdfRegenerationService} le fait pour les batchs :
     * changer un patron ne doit jamais reecrire silencieusement ce qui a deja ete imprime.
     * Une planche generee avant que les codes ne memorisent leur carte ne peut pas etre
     * re-rendue -- l'affectation carte -> emplacement est perdue.
     */
    @Transactional
    public byte[] regenerateSheetPdf(String rawSheetId) {
        CardDatasetSheet sheet = requireSheet(rawSheetId);
        if (!remembersItsCards(sheet)) {
            throw new BadRequestException(
                    "Cette planche ne se souvient pas des cartes qu'elle porte et ne peut pas etre regeneree.");
        }
        return regenerate(sheet, activeTemplate());
    }

    /**
     * Re-rend toutes les planches qui le peuvent, avec le patron courant.
     *
     * <p>Une planche qui ne se souvient pas de ses cartes est ignoree, pas comptee en echec :
     * elle n'est pas regenerable, ce n'est pas une anomalie. Meme forme de compte-rendu que
     * {@code CardPrintPdfRegenerationService.regenerateTemplateBatches}.
     */
    @Transactional
    public CardPrintPdfRegenerationDto regenerateAllSheetPdfs() {
        List<CardDatasetSheet> sheets = sheetRepository.findAll();
        CardPrintTemplate activeTemplate = activeTemplate();
        int skippedCount = 0;
        int regeneratedCount = 0;
        int failedCount = 0;
        for (CardDatasetSheet sheet : sheets) {
            if (!remembersItsCards(sheet)) {
                skippedCount++;
                continue;
            }
            try {
                regenerate(sheet, activeTemplate);
                regeneratedCount++;
            } catch (RuntimeException exception) {
                LOGGER.warn("Regeneration de la planche {} impossible", sheet.getSheetId(), exception);
                failedCount++;
            }
        }
        return new CardPrintPdfRegenerationDto(sheets.size(), skippedCount, regeneratedCount, failedCount);
    }

    private boolean remembersItsCards(CardDatasetSheet sheet) {
        return !sheet.getCodes().isEmpty()
                && sheet.getCodes().stream().allMatch(code -> code.getCard() != null);
    }

    private byte[] regenerate(CardDatasetSheet sheet, CardPrintTemplate activeTemplate) {
        List<CardDatasetSheetCode> codes = sheet.getCodes().stream()
                .sorted(Comparator.comparing(CardDatasetSheetCode::getPosition))
                .toList();
        List<Card> cards = codes.stream().map(CardDatasetSheetCode::getCard).distinct().toList();
        Map<UUID, byte[]> imageBytes = templateService.loadImageBytes(cards);

        List<CardDatasetPdfRenderer.DatasetCardInfo> infos = codes.stream()
                .map(code -> new CardDatasetPdfRenderer.DatasetCardInfo(
                        code.getCode(),
                        imageBytes.get(code.getCard().getId())
                ))
                .toList();

        byte[] pdf = pdfRenderer.render(infos, activeTemplate, sheet.getSheetId());
        String previousObjectKey = sheet.getPdfObjectKey();
        String objectKey = sheetStorage.store(sheet.getId(), pdf);
        sheet.setPdfObjectKey(objectKey);
        sheetRepository.save(sheet);

        // La cle est derivee de l'identifiant de la planche : store() ecrase l'objet en place
        // et renvoie la meme cle. Supprimer sans comparer effacerait le PDF qu'on vient
        // d'ecrire. La comparaison n'est pas defensive pour rien : elle reste juste si le
        // schema de cles change un jour, et evite alors d'abandonner un objet orphelin.
        if (previousObjectKey != null && !previousObjectKey.isBlank() && !previousObjectKey.equals(objectKey)) {
            deleteQuietly(() -> sheetStorage.delete(previousObjectKey));
        }
        return pdf;
    }

    /**
     * Supprime une planche, ses codes, ses decoupes et tout ce qu'elle a depose en stockage.
     *
     * <p>La suppression est en cascade : une planche ratee entraine les scans qu'elle a
     * produits, qui ne valent rien sans elle.
     */
    @Transactional
    public void deleteSheet(String rawSheetId) {
        CardDatasetSheet sheet = requireSheet(rawSheetId);

        List<CardDatasetCrop> crops = cropRepository.findBySheetOrderByCreatedAtAsc(sheet);
        List<String> objectKeys = new ArrayList<>();
        for (CardDatasetCrop crop : crops) {
            objectKeys.add(crop.getImageObjectKey());
            objectKeys.add(crop.getFrameObjectKey());
        }
        cropRepository.deleteAll(crops);

        String pdfObjectKey = sheet.getPdfObjectKey();
        sheetRepository.delete(sheet);

        // Le stockage n'est purge qu'une fois la transaction validee : un rollback laisserait
        // sinon des lignes qui pointent vers des objets disparus.
        afterCommit(() -> {
            for (String objectKey : objectKeys) {
                if (objectKey != null && !objectKey.isBlank()) {
                    deleteQuietly(() -> cropStorage.delete(objectKey));
                }
            }
            if (pdfObjectKey != null && !pdfObjectKey.isBlank()) {
                deleteQuietly(() -> sheetStorage.delete(pdfObjectKey));
            }
        });
    }

    private void deleteQuietly(Runnable deletion) {
        try {
            deletion.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Suppression d'un objet de dataset impossible", exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private byte[] concatenateStoredPdfs(List<CardDatasetSheet> sheets) {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        merger.setDestinationStream(output);
        for (CardDatasetSheet sheet : sheets) {
            if (sheet.getPdfObjectKey() == null || sheet.getPdfObjectKey().isBlank()) {
                throw new NotFoundException(
                        "PDF original de la planche " + sheet.getSheetId() + " indisponible.");
            }
            merger.addSource(new RandomAccessReadBuffer(sheetStorage.read(sheet.getPdfObjectKey())));
        }
        try {
            merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de fusionner les planches demandees.");
        }
        return output.toByteArray();
    }

    @Transactional(readOnly = true)
    public CardDatasetSheetDto findSheet(String rawSheetId) {
        return toDto(requireSheet(rawSheetId));
    }

    @Transactional(readOnly = true)
    public List<CardDatasetSheetSummaryDto> findAllSheets() {
        return sheetRepository.findAllSummaries(PageRequest.of(0, 100)).stream()
                .map(summary -> new CardDatasetSheetSummaryDto(
                        summary.getId(),
                        summary.getSheetId(),
                        summary.getBatchId(),
                        summary.getCreatedAt(),
                        summary.getScanCount(),
                        summary.getDownloadable()))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] readSheetPdf(String rawSheetId) {
        String sheetId = normalizeSheetId(rawSheetId);
        CardDatasetSheet sheet = sheetRepository.findBySheetId(sheetId)
                .orElseThrow(() -> new NotFoundException("Planche de dataset introuvable."));
        if (sheet.getPdfObjectKey() == null || sheet.getPdfObjectKey().isBlank()) {
            throw new NotFoundException("PDF original de la planche indisponible.");
        }
        return sheetStorage.read(sheet.getPdfObjectKey());
    }

    /**
     * Retrouve une planche depuis l'identifiant qu'un humain a recopie du papier.
     *
     * <p>Une saisie qui ne passe pas la somme de controle est refusee : elle ne doit jamais
     * etre resolue vers une planche voisine, ce qui etiquetterait toute une seance avec les
     * codes du mauvais papier.
     */
    private CardDatasetSheet requireSheet(String rawSheetId) {
        String sheetId = normalizeSheetId(rawSheetId);
        return sheetRepository.findWithCodesBySheetId(sheetId)
                .orElseThrow(() -> new NotFoundException("Planche de dataset introuvable."));
    }

    private String normalizeSheetId(String rawSheetId) {
        return CardDatasetSheetId.normalize(rawSheetId)
                .orElseThrow(() -> new BadRequestException("Identifiant de planche invalide."));
    }

    private static CardDatasetSheetDto toDto(CardDatasetSheet sheet) {
        return new CardDatasetSheetDto(
                sheet.getId(),
                sheet.getSheetId(),
                sheet.getPrintRun() == null ? null : sheet.getPrintRun().getId(),
                sheet.getCreatedAt(),
                sheet.getCodes().stream()
                        .map(code -> new CardDatasetSheetCodeDto(code.getId(), code.getPosition(), code.getCode()))
                        .toList()
        );
    }

    private CardPrintTemplate activeTemplate() {
        return templateRepository.findAll().stream()
                .filter(CardPrintTemplate::isEnabled)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Aucun patron d'impression actif."));
    }

    private CardDatasetSheet buildSheet(
            List<CardDatasetPdfRenderer.DatasetCardInfo> infos,
            List<Card> printedCards,
            CardPrintRun run
    ) {
        CardDatasetSheet sheet = new CardDatasetSheet();
        sheet.setId(UUID.randomUUID());
        sheet.setSheetId(nextSheetId());
        sheet.setPrintRun(run);
        for (int position = 0; position < infos.size(); position++) {
            CardDatasetSheetCode code = new CardDatasetSheetCode();
            code.setId(UUID.randomUUID());
            code.setPosition(position);
            code.setCode(infos.get(position).publicId());
            code.setCard(printedCards.get(position));
            sheet.addCode(code);
        }
        return sheet;
    }

    private CardDatasetSheet persistOriginalPdf(CardDatasetSheet sheet, byte[] pdf) {
        String objectKey = sheetStorage.store(sheet.getId(), pdf);
        deletePdfAfterRollback(objectKey);
        sheet.setPdfObjectKey(objectKey);
        return sheetRepository.save(sheet);
    }

    private void deletePdfAfterRollback(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    sheetStorage.delete(objectKey);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not delete rolled-back dataset sheet PDF '{}'.", objectKey, exception);
                }
            }
        });
    }

    private CardDatasetSheetSummaryDto toSummary(CardDatasetSheet sheet, long scanCount) {
        return new CardDatasetSheetSummaryDto(
                sheet.getId(),
                sheet.getSheetId(),
                sheet.getPrintRun() == null ? null : sheet.getPrintRun().getId(),
                sheet.getCreatedAt(),
                scanCount,
                sheet.getPdfObjectKey() != null && !sheet.getPdfObjectKey().isBlank());
    }

    private String nextSheetId() {
        for (int attempt = 0; attempt < SHEET_ID_ATTEMPTS; attempt++) {
            String candidate = CardDatasetSheetId.generate(random);
            if (!sheetRepository.existsBySheetId(candidate)) {
                return candidate;
            }
        }
        throw new BadRequestException("Impossible d'attribuer un identifiant de planche.");
    }

    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.substring(0, 5) + "-" + sb.substring(5, 10) + "-" + sb.substring(10, 15);
    }

    public record GeneratedDatasetSheet(String sheetId, byte[] pdf) {
    }

    private record GeneratedDataset(CardDatasetSheet sheet, byte[] pdf) {
    }
}
