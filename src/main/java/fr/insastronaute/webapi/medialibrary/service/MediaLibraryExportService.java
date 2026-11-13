package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.RateLimitExceededException;
import fr.insastronaute.webapi.medialibrary.domain.MediaExportItemKey;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExport;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExportItem;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import fr.insastronaute.webapi.medialibrary.dto.MediaExportDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaExportPartDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaPersonRefDto;
import fr.insastronaute.webapi.medialibrary.export.MediaExportIndexCsv;
import fr.insastronaute.webapi.medialibrary.export.MediaExportNaming;
import fr.insastronaute.webapi.medialibrary.export.MediaExportPlanner;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryExportItemRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryExportRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * L'export de la selection : un instantane fige, puis des parties de 2 Gio au plus
 * produites en flux.
 *
 * <p>Rien ne passe par le disque : chaque fichier est lu depuis S3 et ecrit aussitot
 * dans la reponse. Les fichiers ne sont pas recompresses (JPEG et MP4 n'y gagnent rien),
 * ce qui garde le calcul negligeable. Une panne de S3 au milieu d'un fichier laisse le
 * zip sans repertoire central : il est visiblement casse plutot que faussement complet.</p>
 */
@Service
public class MediaLibraryExportService {

    static final String EMPTY = "MEDIA_LIBRARY_SELECTION_EMPTY";
    private static final int MAX_CONCURRENT_EXPORTS = 2;
    private static final long RETRY_AFTER_MS = 5_000;
    private static final int BUFFER_BYTES = 64 * 1024;

    public record PartEntry(UUID submissionId, int mediaIndex, String selectedByName, Instant selectedAt) {
    }

    public record PartPlan(Instant snapshotAt, int partNumber, int partCount, List<PartEntry> entries) {
    }

    /** Une place parmi les exports simultanes, rendue une seule fois. */
    public static class ExportSlot implements AutoCloseable {
        private final Semaphore slots;
        private final AtomicBoolean released = new AtomicBoolean();

        ExportSlot(Semaphore slots) {
            this.slots = slots;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                slots.release();
            }
        }
    }

    private final MediaLibraryAccess access;
    private final MediaLibraryRetainedMedia retained;
    private final MediaSizeLookup sizes;
    private final MediaLibraryStorage storage;
    private final ProofStorageService proofStorage;
    private final MediaLibraryExportRepository exports;
    private final MediaLibraryExportItemRepository items;
    private final MediaLibrarySubmissionRepository submissions;
    private final UserRepository users;
    private final AdminActionJournal journal;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_EXPORTS);

    public MediaLibraryExportService(MediaLibraryAccess access, MediaLibraryRetainedMedia retained, MediaSizeLookup sizes,
                                     MediaLibraryStorage storage, ProofStorageService proofStorage,
                                     MediaLibraryExportRepository exports, MediaLibraryExportItemRepository items,
                                     MediaLibrarySubmissionRepository submissions, UserRepository users,
                                     AdminActionJournal journal) {
        this.access = access;
        this.retained = retained;
        this.sizes = sizes;
        this.storage = storage;
        this.proofStorage = proofStorage;
        this.exports = exports;
        this.items = items;
        this.submissions = submissions;
        this.users = users;
        this.journal = journal;
    }

    @Transactional
    public MediaExportDto create(String keycloakSub, Collection<String> authorities) {
        access.require(keycloakSub, authorities);
        AppUser actor = users.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        List<MediaLibraryRetainedMedia.Retained> available = retained.available();
        if (available.isEmpty()) {
            throw new CodedBadRequestException(EMPTY, "La sélection est vide : il n'y a rien à exporter.");
        }

        Map<String, Long> known = sizes.sizesOf(available.stream().map(MediaLibraryRetainedMedia.Retained::objectKey).toList());
        Map<MediaSelectionKey, MediaLibraryRetainedMedia.Retained> byKey = new HashMap<>();
        List<MediaExportPlanner.Candidate> candidates = new ArrayList<>();
        for (MediaLibraryRetainedMedia.Retained item : available) {
            long size = known.containsKey(item.objectKey()) ? known.get(item.objectKey()) : storage.sizeOrZero(item.objectKey());
            MediaSelectionKey key = item.selection().getId();
            byKey.put(key, item);
            candidates.add(new MediaExportPlanner.Candidate(key.getSubmissionId(), key.getMediaIndex(),
                    item.submission().getChallenge().getTitle(), item.submission().getCreatedAt(), size));
        }
        List<MediaExportPlanner.PlannedItem> planned = MediaExportPlanner.plan(candidates, MediaExportPlanner.PART_LIMIT_BYTES);
        long totalBytes = planned.stream().mapToLong(item -> item.candidate().sizeBytes()).sum();
        int partCount = MediaExportPlanner.partCount(planned);

        MediaLibraryExport export = exports.save(new MediaLibraryExport(UUID.randomUUID(), actor, Instant.now(),
                planned.size(), totalBytes, partCount));
        items.saveAll(planned.stream().map(item -> {
            MediaLibraryRetainedMedia.Retained source = byKey.get(
                    new MediaSelectionKey(item.candidate().submissionId(), item.candidate().mediaIndex()));
            return new MediaLibraryExportItem(new MediaExportItemKey(export.getId(), item.position()), item.partNumber(),
                    item.candidate().submissionId(), item.candidate().mediaIndex(), item.candidate().sizeBytes(),
                    source.selection().getSelectedBy(), source.selection().getSelectedAt());
        }).toList());

        journal.recordGlobal(keycloakSub, AdminActionType.MEDIA_LIBRARY_EXPORT_CREATED, Map.of(
                "exportId", export.getId().toString(),
                "itemCount", planned.size(),
                "totalBytes", totalBytes,
                "partCount", partCount));
        return toDto(export);
    }

    @Transactional(readOnly = true)
    public List<MediaExportDto> list(String keycloakSub, Collection<String> authorities) {
        access.require(keycloakSub, authorities);
        return exports.findAllNewestFirst().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MediaExportDto get(String keycloakSub, Collection<String> authorities, UUID exportId) {
        access.require(keycloakSub, authorities);
        return exports.findById(exportId).map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Export introuvable."));
    }

    /**
     * L'acces, l'export et la partie, en lecture seule et sans rien journaliser.
     *
     * <p>Appele avant de prendre une place : pendant que deux exports tournent, un
     * identifiant faux ou un compte sans acces doivent recevoir 404 ou 403, pas 429.
     * {@link #preparePart} refait ces verifications une fois la place prise, et journalise.</p>
     */
    @Transactional(readOnly = true)
    public void checkPart(String keycloakSub, Collection<String> authorities, UUID exportId, int partNumber) {
        access.require(keycloakSub, authorities);
        if (!exports.existsById(exportId)) {
            throw new NotFoundException("Export introuvable.");
        }
        if (!items.existsByIdExportIdAndPartNumber(exportId, partNumber)) {
            throw new NotFoundException("Partie d'export introuvable.");
        }
    }

    /** Pris apres {@link #checkPart}, et rendu par try-with-resources quoi qu'il arrive. */
    public ExportSlot acquireSlot() {
        if (!slots.tryAcquire()) {
            throw new RateLimitExceededException(RETRY_AFTER_MS);
        }
        return new ExportSlot(slots);
    }

    @Transactional
    public PartPlan preparePart(String keycloakSub, Collection<String> authorities, UUID exportId, int partNumber) {
        access.require(keycloakSub, authorities);
        MediaLibraryExport export = exports.findById(exportId)
                .orElseThrow(() -> new NotFoundException("Export introuvable."));
        List<MediaLibraryExportItem> partItems = items.findPart(exportId, partNumber);
        if (partItems.isEmpty()) {
            throw new NotFoundException("Partie d'export introuvable.");
        }
        journal.recordGlobal(keycloakSub, AdminActionType.MEDIA_LIBRARY_EXPORT_DOWNLOADED, Map.of(
                "exportId", exportId.toString(),
                "partNumber", partNumber));
        List<PartEntry> entries = partItems.stream()
                .map(item -> new PartEntry(item.getSubmissionId(), item.getMediaIndex(),
                        MediaPersonRefDto.nameOf(item.getSelectedBy()), item.getSelectedAt()))
                .toList();
        return new PartPlan(export.getCreatedAt(), partNumber, export.getPartCount(), entries);
    }

    public void writePart(PartPlan plan, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out, BUFFER_BYTES), StandardCharsets.UTF_8);
        zip.setLevel(Deflater.NO_COMPRESSION);
        String root = MediaExportNaming.rootFolder(plan.snapshotAt());
        Set<String> usedNames = new HashSet<>();
        List<MediaExportIndexCsv.Row> rows = new ArrayList<>();

        for (PartEntry entry : plan.entries()) {
            Optional<ChallengeSubmission> found = submissions.findEligibleWithMembers(List.of(entry.submissionId()))
                    .stream().findFirst();
            if (found.isEmpty()) {
                continue;
            }
            ChallengeSubmission submission = found.get();
            List<String> keys = proofStorage.parseProofFiles(submission.getProofData());
            if (entry.mediaIndex() >= keys.size()) {
                continue;
            }
            String key = keys.get(entry.mediaIndex());
            String author = MediaPersonRefDto.nameOf(submission.getUser());
            String path = unique(usedNames, MediaExportNaming.entryPath(root, submission.getChallenge().getTitle(),
                    submission.getCreatedAt(), author, submission.getParticipants().size(), submission.getId(),
                    entry.mediaIndex(), MediaExportNaming.extension(key)));

            long written = 0;
            Optional<InputStream> source = storage.openIfPresent(key);
            if (source.isPresent()) {
                try (InputStream input = source.get()) {
                    try {
                        zip.putNextEntry(new ZipEntry(path));
                        written = input.transferTo(zip);
                        zip.closeEntry();
                    } catch (IOException | RuntimeException exception) {
                        // Client parti ou S3 en panne : le reste de l'objet ne sera pas lu.
                        MediaLibraryStorage.abandon(input);
                        throw exception;
                    }
                }
            }
            rows.add(new MediaExportIndexCsv.Row(path, submission.getChallenge().getTitle(),
                    submission.getChallenge().getCategory(), submission.getStatus(), submission.getCreatedAt(), author,
                    submission.getParticipants().stream().map(MediaPersonRefDto::nameOf)
                            .sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    entry.mediaIndex(), MediaLibraryBrowseService.safeContentType(proofStorage, key), written,
                    entry.selectedByName(), entry.selectedAt(), source.isEmpty()));
        }

        zip.putNextEntry(new ZipEntry(root + "/index.csv"));
        zip.write(MediaExportIndexCsv.render(rows));
        zip.closeEntry();
        // close() n'est atteint qu'en succes : il ecrit le repertoire central et termine
        // le Deflater natif. Une exception plus haut (S3 en panne au milieu d'un fichier)
        // sort sans fermer le zip, qui reste donc visiblement casse plutot que valide et tronque.
        zip.close();
    }

    private MediaExportDto toDto(MediaLibraryExport export) {
        List<MediaExportPartDto> parts = items.summarizeParts(export.getId()).stream()
                .sorted(Comparator.comparing(summary -> summary.partNumber()))
                .map(summary -> new MediaExportPartDto(summary.partNumber(), summary.itemCount(), summary.bytes()))
                .toList();
        return new MediaExportDto(export.getId(), export.getCreatedAt(), MediaPersonRefDto.of(export.getCreatedBy()),
                export.getItemCount(), export.getTotalBytes(), export.getPartCount(), parts);
    }

    /** Deux medias au meme nom : le second prend -2, le troisieme -3, avant l'extension. */
    private static String unique(Set<String> used, String path) {
        if (used.add(path)) {
            return path;
        }
        int dot = path.lastIndexOf('.');
        String base = dot < 0 ? path : path.substring(0, dot);
        String extension = dot < 0 ? "" : path.substring(dot);
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "-" + suffix + extension;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }
}
