package fr.insastronaute.webapi.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.dto.CardArchiveImportResultDto;
import fr.insastronaute.webapi.card.print.domain.CardPrintFitMode;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight;
import fr.insastronaute.webapi.card.print.domain.CardPrintRun;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintTextAlign;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.card.domain.CardType;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class CardArchiveService {

    private static final String FORMAT = "insastronaute-card-archive";
    private static final int VERSION = 1;
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String OBJECT_ENTRY_PREFIX = "objects/";
    private static final int MAX_ARCHIVE_ENTRIES = 2_048;
    private static final long MAX_ARCHIVE_SIZE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_MANIFEST_SIZE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_ENTRY_SIZE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_EXPANDED_SIZE_BYTES = 256L * 1024 * 1024;
    private static final long MIN_RATIO_CHECK_BYTES = 1024L * 1024;
    private static final int MAX_COMPRESSION_RATIO = 100;

    private final CardCategoryRepository categoryRepository;
    private final CardRepository cardRepository;
    private final CardInstanceRepository instanceRepository;
    private final CardCollectionRepository collectionRepository;
    private final CardPrintTemplateRepository templateRepository;
    private final CardPrintRunRepository printRunRepository;
    private final CardTypeRepository typeRepository;
    private final EntityManager entityManager;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;

    public CardArchiveService(
            CardCategoryRepository categoryRepository,
            CardRepository cardRepository,
            CardInstanceRepository instanceRepository,
            CardCollectionRepository collectionRepository,
            CardPrintTemplateRepository templateRepository,
            CardPrintRunRepository printRunRepository,
            CardTypeRepository typeRepository,
            EntityManager entityManager,
            S3Client s3Client,
            S3Properties s3Properties,
            ObjectMapper objectMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.cardRepository = cardRepository;
        this.instanceRepository = instanceRepository;
        this.collectionRepository = collectionRepository;
        this.templateRepository = templateRepository;
        this.printRunRepository = printRunRepository;
        this.typeRepository = typeRepository;
        this.entityManager = entityManager;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportArchive() {
        CardArchiveManifest manifest = buildManifest();
        List<ObjectReference> objectReferences = objectReferences(manifest, true);

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(objectMapper.writeValueAsBytes(manifest));
            zip.closeEntry();

            for (ObjectReference objectReference : objectReferences) {
                zip.putNextEntry(new ZipEntry(objectEntryName(objectReference.objectKey())));
                zip.write(readPublicObject(objectReference.objectKey()));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de générer l'archive de cartes.");
        }
    }

    @Transactional
    public CardArchiveImportResultDto importArchive(MultipartFile archiveFile) {
        return importArchive(archiveFile, true);
    }

    @Transactional
    public CardArchiveImportResultDto importArchive(MultipartFile archiveFile, boolean importPhysical) {
        ParsedArchive archive = readArchive(archiveFile);
        validateManifest(archive.manifest());
        List<ObjectReference> manifestReferences = objectReferences(archive.manifest(), true);
        List<ObjectReference> objectReferences = importPhysical
                ? manifestReferences
                : objectReferences(archive.manifest(), false);
        ensureAllObjectsPresent(objectReferences, manifestReferences, archive.objectsByKey());

        List<ObjectReference> objectsToCreate = objectsToCreate(objectReferences, archive.objectsByKey());
        List<String> newlyCreatedKeys = new ArrayList<>();
        boolean rollbackCleanupRegistered = !objectsToCreate.isEmpty()
                && registerRollbackCleanup(newlyCreatedKeys);

        try {
            for (ObjectReference objectReference : objectsToCreate) {
                putPublicObject(objectReference, archive.objectsByKey().get(objectReference.objectKey()));
                newlyCreatedKeys.add(objectReference.objectKey());
            }

            resetCardSystem();
            ImportedData importedData = importManifest(archive.manifest(), importPhysical);

            return new CardArchiveImportResultDto(
                    importedData.typeCount(),
                    importedData.categoryCount(),
                    importedData.cardCount(),
                    importedData.instanceCount(),
                    importedData.templateCount(),
                    importedData.printRunCount(),
                    objectReferences.size()
            );
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                deleteObjectsQuietly(newlyCreatedKeys);
            }
            throw exception;
        }
    }

    private CardArchiveManifest buildManifest() {
        List<CardCategoryArchive> categories = categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(CardCategory::getDisplayOrder).thenComparing(CardCategory::getName))
                .map(category -> new CardCategoryArchive(
                        category.getId(),
                        category.getName(),
                        category.getDisplayOrder(),
                        category.getImageObjectKey(),
                        category.getThumbnailObjectKey(),
                        category.getContentType(),
                        category.isHidden(),
                        category.isGame(),
                        category.getFirstAcquisitionPoints(),
                        category.getDuplicatePoints(),
                        instant(category.getCreatedAt()),
                        instant(category.getUpdatedAt())
                ))
                .toList();

        List<CardArchive> cards = cardRepository.findAll().stream()
                .sorted(Comparator.comparing(Card::getCategory).thenComparing(Card::getDisplayOrder).thenComparing(Card::getName))
                .map(card -> new CardArchive(
                        card.getId(),
                        card.getName(),
                        card.getCategory(),
                        card.getCategoryId(),
                        card.getTypeId(),
                        card.isEnabled(),
                        card.isDefaultCard(),
                        card.getDisplayOrder(),
                        card.getImageObjectKey(),
                        card.getThumbnailObjectKey(),
                        card.getContentType(),
                        card.getFirstAcquisitionPoints(),
                        card.getDuplicatePoints(),
                        instant(card.getCreatedAt()),
                        instant(card.getUpdatedAt())
                ))
                .toList();

        List<CardInstanceArchive> instances = instanceRepository.findAll().stream()
                .sorted(Comparator.comparing(CardInstance::getCreatedAt))
                .map(instance -> new CardInstanceArchive(
                        instance.getId(),
                        instance.getCard().getId(),
                        instance.getPublicId(),
                        instant(instance.getCreatedAt()),
                        instant(instance.getUpdatedAt())
                ))
                .toList();

        List<CardPrintTemplateArchive> templates = templateRepository.findAll().stream()
                .sorted(Comparator.comparing(CardPrintTemplate::getCreatedAt))
                .map(template -> new CardPrintTemplateArchive(
                        template.getId(),
                        template.getName(),
                        template.getPdfObjectKey(),
                        template.getPageCount(),
                        template.getPageWidthPt(),
                        template.getPageHeightPt(),
                        template.isEnabled(),
                        template.getFitMode(),
                        template.getBleedPt(),
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
                        template.getCodeAlign(),
                        template.getSlots().stream()
                                .sorted(Comparator.comparing(CardPrintTemplateSlot::getSlotIndex))
                                .map(slot -> new CardPrintTemplateSlotArchive(
                                        slot.getId(),
                                        slot.getSlotIndex(),
                                        slot.getXPt(),
                                        slot.getYPt(),
                                        slot.getWidthPt(),
                                        slot.getHeightPt()
                                ))
                                .toList(),
                        instant(template.getCreatedAt()),
                        instant(template.getUpdatedAt())
                ))
                .toList();

        List<CardPrintRunArchive> printRuns = printRunRepository.findAllByOrderByCreatedAtDesc().stream()
                .sorted(Comparator.comparing(CardPrintRun::getCreatedAt))
                .map(printRun -> new CardPrintRunArchive(
                        printRun.getId(),
                        printRun.getTemplate().getId(),
                        printRun.getSheetCount(),
                        printRun.getInstanceCount(),
                        printRun.getStatus(),
                        printRun.getPdfObjectKey(),
                        printRun.getSlots().stream()
                                .sorted(Comparator.comparing(CardPrintRunSlot::getPageIndex)
                                        .thenComparing(CardPrintRunSlot::getSlotIndex))
                                .map(slot -> new CardPrintRunSlotArchive(
                                        slot.getId(),
                                        slot.getPageIndex(),
                                        slot.getSlotIndex(),
                                        slot.getCard().getId(),
                                        slot.getCardInstance().getId()
                                ))
                                .toList(),
                        instant(printRun.getCreatedAt()),
                        instant(printRun.getCompletedAt())
                ))
                .toList();

        List<CardTypeArchive> types = typeRepository.findAll().stream()
                .sorted(Comparator.comparing(CardType::getName))
                .map(type -> new CardTypeArchive(
                        type.getId(),
                        type.getName(),
                        instant(type.getCreatedAt()),
                        instant(type.getUpdatedAt())
                ))
                .toList();

        return new CardArchiveManifest(
                FORMAT,
                VERSION,
                instant(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()),
                types,
                categories,
                cards,
                instances,
                templates,
                printRuns
        );
    }

    private ImportedData importManifest(CardArchiveManifest manifest, boolean importPhysical) {
        Map<UUID, CardType> typesById = manifest.types().stream()
                .map(this::toType)
                .collect(Collectors.toMap(CardType::getId, Function.identity(), this::keepFirst, LinkedHashMap::new));
        persistAll(typesById.values());

        Map<UUID, CardCategory> categoriesById = manifest.categories().stream()
                .map(this::toCategory)
                .collect(Collectors.toMap(CardCategory::getId, Function.identity(), this::keepFirst, LinkedHashMap::new));
        persistAll(categoriesById.values());

        Map<UUID, Card> cardsById = manifest.cards().stream()
                .map(cardArchive -> toCard(cardArchive, typesById, categoriesById))
                .collect(Collectors.toMap(Card::getId, Function.identity(), this::keepFirst, LinkedHashMap::new));
        persistAll(cardsById.values());

        Map<UUID, CardInstance> instancesById = importPhysical
                ? manifest.instances().stream()
                        .map(instanceArchive -> toInstance(instanceArchive, cardsById))
                        .collect(Collectors.toMap(CardInstance::getId, Function.identity(), this::keepFirst, LinkedHashMap::new))
                : new LinkedHashMap<>();
        persistAll(instancesById.values());

        Map<UUID, CardPrintTemplate> templatesById = manifest.templates().stream()
                .map(this::toTemplate)
                .collect(Collectors.toMap(CardPrintTemplate::getId, Function.identity(), this::keepFirst, LinkedHashMap::new));
        persistAll(templatesById.values());

        List<CardPrintRun> printRuns = importPhysical
                ? manifest.printRuns().stream()
                        .map(printRunArchive -> toPrintRun(printRunArchive, templatesById, cardsById, instancesById))
                        .toList()
                : List.<CardPrintRun>of();
        persistAll(printRuns);


        return new ImportedData(
                typesById.size(),
                categoriesById.size(),
                cardsById.size(),
                instancesById.size(),
                templatesById.size(),
                printRuns.size()
        );
    }

    private void persistAll(Iterable<?> entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
    }

    private <T> T keepFirst(T first, T second) {
        return first == null ? second : first;
    }

    private CardType toType(CardTypeArchive archive) {
        CardType type = new CardType();
        type.setId(archive.id());
        type.setName(archive.name());
        type.setCreatedAt(parseInstant(archive.createdAt()));
        type.setUpdatedAt(parseInstant(archive.updatedAt()));
        return type;
    }

    private CardCategory toCategory(CardCategoryArchive archive) {
        CardCategory category = new CardCategory();
        category.setId(archive.id());
        category.setName(archive.name());
        category.setDisplayOrder(archive.displayOrder());
        category.setImageObjectKey(archive.imageObjectKey());
        category.setThumbnailObjectKey(archive.thumbnailObjectKey());
        category.setImageUrl(archive.imageObjectKey() == null ? null : publicUrl(archive.imageObjectKey()));
        category.setThumbnailUrl(archive.thumbnailObjectKey() == null ? null : publicUrl(archive.thumbnailObjectKey()));
        category.setContentType(archive.contentType());
        category.setHidden(archive.hidden());
        category.setGame(archive.game());
        category.setFirstAcquisitionPoints(archive.firstAcquisitionPoints());
        category.setDuplicatePoints(archive.duplicatePoints());
        category.setCreatedAt(parseInstant(archive.createdAt()));
        category.setUpdatedAt(parseInstant(archive.updatedAt()));
        return category;
    }

    private Card toCard(CardArchive archive, Map<UUID, CardType> typesById, Map<UUID, CardCategory> categoriesById) {
        Card card = new Card();
        card.setId(archive.id());
        card.setName(archive.name());
        card.setCategory(archive.category());
        if (archive.categoryId() != null) {
            card.setCategoryRef(required(categoriesById, archive.categoryId(), "Catégorie de carte introuvable dans l'archive."));
        }
        if (archive.typeId() != null) {
            card.setTypeRef(required(typesById, archive.typeId(), "Type de carte introuvable dans l'archive."));
        }
        card.setEnabled(archive.enabled());
        card.setDefaultCard(archive.defaultCard());
        card.setDisplayOrder(archive.displayOrder());
        card.setFirstAcquisitionPoints(archive.firstAcquisitionPoints());
        card.setDuplicatePoints(archive.duplicatePoints());
        card.setImageObjectKey(archive.imageObjectKey());
        card.setThumbnailObjectKey(archive.thumbnailObjectKey());
        card.setImageUrl(publicUrl(archive.imageObjectKey()));
        card.setThumbnailUrl(publicUrl(archive.thumbnailObjectKey()));
        card.setContentType(archive.contentType());
        card.setCreatedAt(parseInstant(archive.createdAt()));
        card.setUpdatedAt(parseInstant(archive.updatedAt()));
        return card;
    }

    private CardInstance toInstance(CardInstanceArchive archive, Map<UUID, Card> cardsById) {
        CardInstance instance = new CardInstance();
        instance.setId(archive.id());
        instance.setCard(required(cardsById, archive.cardId(), "Carte d'instance introuvable dans l'archive."));
        instance.setPublicId(archive.publicId());
        instance.setOwner(null);
        instance.setAssignedAt(null);
        instance.setCreatedAt(parseInstant(archive.createdAt()));
        instance.setUpdatedAt(parseInstant(archive.updatedAt()));
        return instance;
    }

    private CardPrintTemplate toTemplate(CardPrintTemplateArchive archive) {
        CardPrintTemplate template = new CardPrintTemplate();
        template.setId(archive.id());
        template.setName(archive.name());
        template.setPdfObjectKey(archive.pdfObjectKey());
        template.setPdfUrl(publicUrl(archive.pdfObjectKey()));
        template.setPageCount(archive.pageCount());
        template.setPageWidthPt(archive.pageWidthPt());
        template.setPageHeightPt(archive.pageHeightPt());
        template.setEnabled(archive.enabled());
        template.setFitMode(archive.fitMode());
        template.setBleedPt(archive.bleedPt());
        template.setCodeXPt(archive.codeXPt());
        template.setCodeYPt(archive.codeYPt());
        template.setCodeWidthPt(archive.codeWidthPt());
        template.setCodeHeightPt(archive.codeHeightPt());
        template.setCodeFontFamily(archive.codeFontFamily());
        template.setCodeFontWeight(archive.codeFontWeight());
        template.setCodeFontSizePt(archive.codeFontSizePt());
        template.setCodeColor(archive.codeColor());
        template.setCodeBackgroundColor(archive.codeBackgroundColor());
        template.setCodeBackgroundOpacity(archive.codeBackgroundOpacity());
        template.setCodeAlign(archive.codeAlign());
        archive.slots().stream()
                .sorted(Comparator.comparing(CardPrintTemplateSlotArchive::slotIndex))
                .map(this::toTemplateSlot)
                .forEach(template::addSlot);
        template.setCreatedAt(parseInstant(archive.createdAt()));
        template.setUpdatedAt(parseInstant(archive.updatedAt()));
        return template;
    }

    private CardPrintTemplateSlot toTemplateSlot(CardPrintTemplateSlotArchive archive) {
        CardPrintTemplateSlot slot = new CardPrintTemplateSlot();
        slot.setId(archive.id());
        slot.setSlotIndex(archive.slotIndex());
        slot.setXPt(archive.xPt());
        slot.setYPt(archive.yPt());
        slot.setWidthPt(archive.widthPt());
        slot.setHeightPt(archive.heightPt());
        return slot;
    }

    private CardPrintRun toPrintRun(
            CardPrintRunArchive archive,
            Map<UUID, CardPrintTemplate> templatesById,
            Map<UUID, Card> cardsById,
            Map<UUID, CardInstance> instancesById
    ) {
        CardPrintRun printRun = new CardPrintRun();
        printRun.setId(archive.id());
        printRun.setTemplate(required(templatesById, archive.templateId(), "Patron de batch introuvable dans l'archive."));
        printRun.setSheetCount(archive.sheetCount());
        printRun.setInstanceCount(archive.instanceCount());
        printRun.setStatus(archive.status());
        printRun.setPdfObjectKey(archive.pdfObjectKey());
        printRun.setPdfUrl(archive.pdfObjectKey() == null ? null : "/api/admin/batches/" + archive.id() + "/pdf");
        archive.slots().stream()
                .sorted(Comparator.comparing(CardPrintRunSlotArchive::pageIndex).thenComparing(CardPrintRunSlotArchive::slotIndex))
                .map(slotArchive -> toPrintRunSlot(slotArchive, cardsById, instancesById))
                .forEach(printRun::addSlot);
        printRun.setCreatedAt(parseInstant(archive.createdAt()));
        printRun.setCompletedAt(parseInstant(archive.completedAt()));
        return printRun;
    }

    private CardPrintRunSlot toPrintRunSlot(
            CardPrintRunSlotArchive archive,
            Map<UUID, Card> cardsById,
            Map<UUID, CardInstance> instancesById
    ) {
        CardPrintRunSlot slot = new CardPrintRunSlot();
        slot.setId(archive.id());
        slot.setPageIndex(archive.pageIndex());
        slot.setSlotIndex(archive.slotIndex());
        slot.setCard(required(cardsById, archive.cardId(), "Carte de batch introuvable dans l'archive."));
        slot.setCardInstance(required(instancesById, archive.cardInstanceId(), "Instance de batch introuvable dans l'archive."));
        return slot;
    }


    private void resetCardSystem() {
        entityManager.flush();
        deleteFrom("card_print_run_slots");
        deleteFrom("card_print_runs");
        deleteFrom("card_print_template_slots");
        deleteFrom("card_print_templates");
        deleteFrom("deck_drop_tier_cards");
        collectionRepository.deleteAllInBatch();
        deleteFrom("card_instances");
        deleteFrom("cards");
        deleteFrom("card_types");
        deleteFrom("card_categories");
        entityManager.flush();
        entityManager.clear();
    }

    @SuppressWarnings("SqlWithoutWhere")
    private void deleteFrom(String tableName) {
        entityManager.createNativeQuery("DELETE FROM " + tableName).executeUpdate();
    }

    private ParsedArchive readArchive(MultipartFile archiveFile) {
        if (archiveFile == null || archiveFile.isEmpty()) {
            throw new BadRequestException("L'archive de cartes est requise.");
        }
        if (archiveFile.getSize() > MAX_ARCHIVE_SIZE_BYTES) {
            throw new BadRequestException(
                    "L'archive de cartes ne peut pas dépasser " + megabytes(MAX_ARCHIVE_SIZE_BYTES) + " MB.");
        }

        CardArchiveManifest manifest = null;
        Map<String, byte[]> objectsByKey = new LinkedHashMap<>();
        Set<String> entryNames = new HashSet<>();
        long expandedBytes = 0;
        int entryCount = 0;
        try (CountingInputStream countedInput = new CountingInputStream(archiveFile.getInputStream());
             ZipInputStream zip = new ZipInputStream(countedInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new BadRequestException("L'archive de cartes contient trop d'entrées.");
                }
                if (!entryNames.add(entry.getName())) {
                    throw new BadRequestException("L'archive de cartes contient une entrée dupliquée.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                boolean manifestEntry = MANIFEST_ENTRY.equals(entry.getName());
                long entryLimit = manifestEntry ? MAX_MANIFEST_SIZE_BYTES : MAX_ENTRY_SIZE_BYTES;
                byte[] bytes = readEntryBytes(zip, entryLimit, expandedBytes, manifestEntry);
                expandedBytes = Math.addExact(expandedBytes, bytes.length);
                validateCompressionRatio(expandedBytes, countedInput.count());
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    if (manifest != null) {
                        throw new BadRequestException("L'archive de cartes contient une entrée dupliquée.");
                    }
                    manifest = objectMapper.readValue(bytes, CardArchiveManifest.class);
                } else if (entry.getName().startsWith(OBJECT_ENTRY_PREFIX)) {
                    String objectKey = entry.getName().substring(OBJECT_ENTRY_PREFIX.length());
                    validateObjectKey(objectKey);
                    if (objectsByKey.putIfAbsent(objectKey, bytes) != null) {
                        throw new BadRequestException("L'archive de cartes contient une entrée dupliquée.");
                    }
                } else {
                    throw new BadRequestException("L'archive de cartes contient une entrée non supportée.");
                }
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (ArithmeticException | IOException exception) {
            throw new BadRequestException("L'archive de cartes est invalide.");
        }

        if (manifest == null) {
            throw new BadRequestException("L'archive ne contient pas de manifeste.");
        }
        return new ParsedArchive(manifest, objectsByKey);
    }

    private byte[] readEntryBytes(
            ZipInputStream zip,
            long entryLimit,
            long expandedBeforeEntry,
            boolean manifestEntry
    ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        long entryBytes = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            entryBytes += read;
            if (entryBytes > entryLimit) {
                throw new BadRequestException(manifestEntry
                        ? "Le manifeste de l'archive de cartes dépasse " + megabytes(entryLimit) + " MB."
                        : "Une entrée de l'archive de cartes dépasse " + megabytes(entryLimit) + " MB.");
            }
            if (expandedBeforeEntry + entryBytes > MAX_EXPANDED_SIZE_BYTES) {
                throw new BadRequestException(
                        "L'archive de cartes dépasse " + megabytes(MAX_EXPANDED_SIZE_BYTES) + " MB après décompression.");
            }
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private static long megabytes(long bytes) {
        return bytes / (1024 * 1024);
    }

    private void validateCompressionRatio(long expandedBytes, long compressedBytes) {
        if (expandedBytes >= MIN_RATIO_CHECK_BYTES
                && compressedBytes > 0
                && expandedBytes > compressedBytes * MAX_COMPRESSION_RATIO) {
            throw new BadRequestException("Le ratio de compression de l'archive de cartes est excessif.");
        }
    }

    private void validateManifest(CardArchiveManifest manifest) {
        if (!FORMAT.equals(manifest.format()) || manifest.version() != VERSION) {
            throw new BadRequestException("Le format de l'archive de cartes n'est pas supporté.");
        }
    }

    private void ensureAllObjectsPresent(
            List<ObjectReference> requiredReferences,
            List<ObjectReference> manifestReferences,
            Map<String, byte[]> objectsByKey
    ) {
        for (ObjectReference objectReference : requiredReferences) {
            if (!objectsByKey.containsKey(objectReference.objectKey())) {
                throw new BadRequestException("L'archive ne contient pas l'objet " + objectReference.objectKey() + ".");
            }
        }
        Set<String> manifestKeys = manifestReferences.stream()
                .map(ObjectReference::objectKey)
                .collect(Collectors.toSet());
        if (!manifestKeys.containsAll(objectsByKey.keySet())) {
            throw new BadRequestException("L'archive contient un objet non référencé par le manifeste.");
        }
    }

    private List<ObjectReference> objectReferences(CardArchiveManifest manifest, boolean includePhysical) {
        Map<String, ObjectReference> referencesByKey = new LinkedHashMap<>();
        for (CardCategoryArchive category : manifest.categories()) {
            addObjectReference(referencesByKey, category.imageObjectKey(), "card-categories/", "image/jpeg", ObjectCannedACL.PUBLIC_READ, true);
            addObjectReference(referencesByKey, category.thumbnailObjectKey(), "card-categories/", "image/jpeg", ObjectCannedACL.PUBLIC_READ, true);
        }
        for (CardArchive card : manifest.cards()) {
            addObjectReference(referencesByKey, card.imageObjectKey(), "cards/", "image/jpeg", ObjectCannedACL.PUBLIC_READ, true);
            addObjectReference(referencesByKey, card.thumbnailObjectKey(), "cards/", "image/jpeg", ObjectCannedACL.PUBLIC_READ, true);
        }
        for (CardPrintTemplateArchive template : manifest.templates()) {
            addObjectReference(referencesByKey, template.pdfObjectKey(), "card-print/templates/", "application/pdf", ObjectCannedACL.PUBLIC_READ, true);
        }
        if (includePhysical) {
            for (CardPrintRunArchive printRun : manifest.printRuns()) {
                addObjectReference(referencesByKey, printRun.pdfObjectKey(), "card-print/runs/", "application/pdf", ObjectCannedACL.PRIVATE, false);
            }
        }
        return new ArrayList<>(referencesByKey.values());
    }

    private void addObjectReference(
            Map<String, ObjectReference> referencesByKey,
            String objectKey,
            String expectedPrefix,
            String contentType,
            ObjectCannedACL acl,
            boolean immutable
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        validateObjectKey(objectKey);
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new BadRequestException("Une clé d'objet de l'archive ne respecte pas son préfixe métier.");
        }
        referencesByKey.putIfAbsent(objectKey, new ObjectReference(objectKey, contentType, acl, immutable));
    }

    private byte[] readPublicObject(String objectKey) {
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .key(objectKey)
                .build());
        return bytes.asByteArray();
    }

    private void putPublicObject(ObjectReference objectReference, byte[] bytes) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectReference.objectKey())
                        .contentType(objectReference.contentType())
                        .cacheControl(objectReference.immutable()
                                ? "public, max-age=31536000, immutable"
                                : "private, max-age=0, no-cache")
                        .acl(objectReference.acl())
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    private List<ObjectReference> objectsToCreate(
            List<ObjectReference> objectReferences,
            Map<String, byte[]> objectsByKey
    ) {
        List<ObjectReference> objectsToCreate = new ArrayList<>();
        for (ObjectReference objectReference : objectReferences) {
            byte[] archiveBytes = objectsByKey.get(objectReference.objectKey());
            byte[] existingBytes = readExistingObject(objectReference.objectKey());
            if (existingBytes == null) {
                objectsToCreate.add(objectReference);
            } else if (!MessageDigest.isEqual(sha256(existingBytes), sha256(archiveBytes))) {
                throw new BadRequestException(
                        "L'objet " + objectReference.objectKey() + " existe déjà avec un contenu différent."
                );
            }
        }
        return objectsToCreate;
    }

    private byte[] readExistingObject(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
            return response == null ? null : response.asByteArray();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return null;
            }
            throw exception;
        }
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible.", exception);
        }
    }

    private boolean registerRollbackCleanup(List<String> objectKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteObjectsQuietly(objectKeys);
                }
            }
        });
        return true;
    }

    private void deleteObjectsQuietly(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .build());
            } catch (RuntimeException ignored) {
                // Best-effort compensation after an unsuccessful database transaction.
            }
        }
    }

    private String publicUrl(String objectKey) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour importer les cartes.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectKey).toString();
    }

    private String objectEntryName(String objectKey) {
        validateObjectKey(objectKey);
        return OBJECT_ENTRY_PREFIX + objectKey;
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey.startsWith("/") || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new BadRequestException("Une clé d'objet de l'archive est invalide.");
        }
    }

    private <T> T required(Map<UUID, T> valuesById, UUID id, String message) {
        T value = valuesById.get(id);
        if (value == null) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Instant parseInstant(String instant) {
        return instant == null || instant.isBlank() ? null : Instant.parse(instant);
    }

    private record ParsedArchive(CardArchiveManifest manifest, Map<String, byte[]> objectsByKey) {
    }

    private record ObjectReference(String objectKey, String contentType, ObjectCannedACL acl, boolean immutable) {
    }

    private record ImportedData(
            int typeCount,
            int categoryCount,
            int cardCount,
            int instanceCount,
            int templateCount,
            int printRunCount
    ) {
    }

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        private long count() {
            return count;
        }
    }

    private record CardArchiveManifest(
            String format,
            int version,
            String exportedAt,
            List<CardTypeArchive> types,
            List<CardCategoryArchive> categories,
            List<CardArchive> cards,
            List<CardInstanceArchive> instances,
            List<CardPrintTemplateArchive> templates,
            List<CardPrintRunArchive> printRuns
    ) {
        private CardArchiveManifest {
            types = types == null ? List.of() : types;
            categories = categories == null ? List.of() : categories;
            cards = cards == null ? List.of() : cards;
            instances = instances == null ? List.of() : instances;
            templates = templates == null ? List.of() : templates;
            printRuns = printRuns == null ? List.of() : printRuns;
        }
    }

    private record CardCategoryArchive(
            UUID id,
            String name,
            Integer displayOrder,
            String imageObjectKey,
            String thumbnailObjectKey,
            String contentType,
            boolean hidden,
            boolean game,
            Integer firstAcquisitionPoints,
            Integer duplicatePoints,
            String createdAt,
            String updatedAt
    ) {
    }

    private record CardTypeArchive(
            UUID id,
            String name,
            String createdAt,
            String updatedAt
    ) {
    }

    private record CardArchive(
            UUID id,
            String name,
            String category,
            UUID categoryId,
            UUID typeId,
            boolean enabled,
            boolean defaultCard,
            Integer displayOrder,
            String imageObjectKey,
            String thumbnailObjectKey,
            String contentType,
            Integer firstAcquisitionPoints,
            Integer duplicatePoints,
            String createdAt,
            String updatedAt
    ) {
    }

    private record CardInstanceArchive(UUID id, UUID cardId, String publicId, String createdAt, String updatedAt) {
    }

    private record CardPrintTemplateArchive(
            UUID id,
            String name,
            String pdfObjectKey,
            Integer pageCount,
            Double pageWidthPt,
            Double pageHeightPt,
            boolean enabled,
            CardPrintFitMode fitMode,
            Double bleedPt,
            Double codeXPt,
            Double codeYPt,
            Double codeWidthPt,
            Double codeHeightPt,
            CardPrintFontFamily codeFontFamily,
            CardPrintFontWeight codeFontWeight,
            Double codeFontSizePt,
            String codeColor,
            String codeBackgroundColor,
            Double codeBackgroundOpacity,
            CardPrintTextAlign codeAlign,
            List<CardPrintTemplateSlotArchive> slots,
            String createdAt,
            String updatedAt
    ) {
        private CardPrintTemplateArchive {
            slots = slots == null ? List.of() : slots;
        }
    }

    private record CardPrintTemplateSlotArchive(
            UUID id,
            Integer slotIndex,
            Double xPt,
            Double yPt,
            Double widthPt,
            Double heightPt
    ) {
    }

    private record CardPrintRunArchive(
            UUID id,
            UUID templateId,
            Integer sheetCount,
            Integer instanceCount,
            CardPrintRunStatus status,
            String pdfObjectKey,
            List<CardPrintRunSlotArchive> slots,
            String createdAt,
            String completedAt
    ) {
        private CardPrintRunArchive {
            slots = slots == null ? List.of() : slots;
        }
    }

    private record CardPrintRunSlotArchive(
            UUID id,
            Integer pageIndex,
            Integer slotIndex,
            UUID cardId,
            UUID cardInstanceId
    ) {
    }

}
