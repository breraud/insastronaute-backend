package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.RateLimitExceededException;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExport;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExportItem;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import fr.insastronaute.webapi.medialibrary.repository.MediaExportPartSummary;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryExportItemRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryExportRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaLibraryExportServiceTest {

    private final MediaLibraryAccess access = mock(MediaLibraryAccess.class);
    private final MediaLibraryRetainedMedia retained = mock(MediaLibraryRetainedMedia.class);
    private final MediaSizeLookup sizes = mock(MediaSizeLookup.class);
    private final MediaLibraryStorage storage = mock(MediaLibraryStorage.class);
    private final ProofStorageService proofStorage = mock(ProofStorageService.class);
    private final MediaLibraryExportRepository exports = mock(MediaLibraryExportRepository.class);
    private final MediaLibraryExportItemRepository items = mock(MediaLibraryExportItemRepository.class);
    private final MediaLibrarySubmissionRepository submissions = mock(MediaLibrarySubmissionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminActionJournal journal = mock(AdminActionJournal.class);
    private final MediaLibraryExportService service = new MediaLibraryExportService(access, retained, sizes, storage,
            proofStorage, exports, items, submissions, users, journal);

    private final AppUser admin = user("Alex");

    @Test
    void anEmptySelectionCannotBeExported() {
        when(users.findByKeycloakSub("admin-sub")).thenReturn(Optional.of(admin));
        when(retained.available()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create("admin-sub", Set.of("ROLE_ADMIN")))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("MEDIA_LIBRARY_SELECTION_EMPTY"));
    }

    @Test
    void creatingFreezesTheItemsAndJournalsTheSnapshot() {
        ChallengeSubmission submission = submission("Photo", user("Camille"), "[\"proofs/u/c/a.jpg\",\"proofs/u/c/b.mp4\"]");
        when(users.findByKeycloakSub("admin-sub")).thenReturn(Optional.of(admin));
        when(retained.available()).thenReturn(List.of(
                retainedOf(submission, 0, "proofs/u/c/a.jpg"), retainedOf(submission, 1, "proofs/u/c/b.mp4")));
        when(sizes.sizesOf(anyCollection())).thenReturn(Map.of("proofs/u/c/a.jpg", 100L));
        when(storage.sizeOrZero("proofs/u/c/b.mp4")).thenReturn(300L);
        when(exports.save(any(MediaLibraryExport.class))).thenAnswer(call -> call.getArgument(0));
        when(items.summarizeParts(any())).thenReturn(List.of(new MediaExportPartSummary(1, 2L, 400L)));

        var dto = service.create("admin-sub", Set.of("ROLE_ADMIN"));

        assertThat(dto.itemCount()).isEqualTo(2);
        assertThat(dto.totalBytes()).isEqualTo(400L);
        assertThat(dto.partCount()).isEqualTo(1);
        verify(items).saveAll(org.mockito.ArgumentMatchers.<Iterable<MediaLibraryExportItem>>argThat(saved -> {
            List<MediaLibraryExportItem> list = new ArrayList<>();
            saved.forEach(list::add);
            return list.size() == 2 && list.get(1).getSizeBytes() == 300L;
        }));
        verify(journal).recordGlobal(eq("admin-sub"), eq(AdminActionType.MEDIA_LIBRARY_EXPORT_CREATED), anyMap());
    }

    @Test
    void aPartIsAValidZipWithItsFilesAndItsIndex() throws Exception {
        ChallengeSubmission submission = submission("Chanter au RU", user("Camille Martin"), "[\"proofs/u/c/a.jpg\",\"proofs/u/c/b.jpg\"]");
        when(submissions.findEligibleWithMembers(List.of(submission.getId()))).thenReturn(List.of(submission));
        when(proofStorage.parseProofFiles(submission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg", "proofs/u/c/b.jpg"));
        when(proofStorage.contentTypeForProofKey(any())).thenReturn("image/jpeg");
        when(storage.openIfPresent("proofs/u/c/a.jpg")).thenReturn(Optional.of(new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(storage.openIfPresent("proofs/u/c/b.jpg")).thenReturn(Optional.empty());

        Map<String, byte[]> zip = unzip(write(plan(entry(submission, 0), entry(submission, 1))));

        String root = "mediatheque-2026-10-02/";
        String first = root + "chanter-au-ru/2026-09-14_camille-martin_" + submission.getId().toString().substring(0, 8) + "_1.jpg";
        assertThat(zip).containsKeys(first, root + "index.csv");
        assertThat(zip.get(first)).containsExactly(1, 2, 3);
        String index = new String(zip.get(root + "index.csv"), StandardCharsets.UTF_8);
        assertThat(index).contains(";ok\r\n").contains(";manquant\r\n");
    }

    @Test
    void aMediaThatBecameIneligibleIsSkipped() throws Exception {
        ChallengeSubmission submission = submission("Photo", user("Camille"), "[\"proofs/u/c/a.jpg\"]");
        when(submissions.findEligibleWithMembers(List.of(submission.getId()))).thenReturn(List.of());

        Map<String, byte[]> zip = unzip(write(plan(entry(submission, 0))));

        assertThat(zip).containsOnlyKeys("mediatheque-2026-10-02/index.csv");
    }

    @Test
    void twoEntriesWithTheSameNameAreBothKept() throws Exception {
        ChallengeSubmission submission = submission("Photo", user("Camille"), "[\"proofs/u/c/a.jpg\"]");
        when(submissions.findEligibleWithMembers(List.of(submission.getId()))).thenReturn(List.of(submission));
        when(proofStorage.parseProofFiles(submission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg"));
        when(proofStorage.contentTypeForProofKey(any())).thenReturn("image/jpeg");
        when(storage.openIfPresent("proofs/u/c/a.jpg"))
                .thenReturn(Optional.of(new ByteArrayInputStream(new byte[]{1})), Optional.of(new ByteArrayInputStream(new byte[]{2})));

        Map<String, byte[]> zip = unzip(write(plan(entry(submission, 0), entry(submission, 0))));

        assertThat(zip.keySet()).filteredOn(name -> name.endsWith(".jpg")).hasSize(2);
    }

    /** Verifier une partie avant de prendre une place : en lecture seule, sans rien journaliser. */
    @Test
    void checkingAPartReadsWithoutJournaling() {
        UUID exportId = UUID.randomUUID();
        when(exports.existsById(exportId)).thenReturn(true);
        when(items.existsByIdExportIdAndPartNumber(exportId, 1)).thenReturn(true);

        service.checkPart("admin-sub", Set.of("ROLE_ADMIN"), exportId, 1);

        verify(access).require("admin-sub", Set.of("ROLE_ADMIN"));
        verify(journal, never()).recordGlobal(any(), any(), anyMap());
    }

    @Test
    void checkingAMissingExportOrAnEmptyPartIsNotFound() {
        UUID missing = UUID.randomUUID();
        UUID exportId = UUID.randomUUID();
        when(exports.existsById(missing)).thenReturn(false);
        when(exports.existsById(exportId)).thenReturn(true);
        when(items.existsByIdExportIdAndPartNumber(exportId, 4)).thenReturn(false);

        assertThatThrownBy(() -> service.checkPart("admin-sub", Set.of("ROLE_ADMIN"), missing, 1))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.checkPart("admin-sub", Set.of("ROLE_ADMIN"), exportId, 4))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void checkingWithoutAccessReadsNothing() {
        doThrow(new ForbiddenException("non")).when(access).require(any(), any());

        assertThatThrownBy(() -> service.checkPart("sub", Set.of(), UUID.randomUUID(), 1))
                .isInstanceOf(ForbiddenException.class);
        verify(exports, never()).existsById(any());
    }

    @Test
    void aThirdSimultaneousExportIsRefusedAndASlotComesBack() throws Exception {
        MediaLibraryExportService.ExportSlot first = service.acquireSlot();
        MediaLibraryExportService.ExportSlot second = service.acquireSlot();

        assertThatThrownBy(service::acquireSlot).isInstanceOf(RateLimitExceededException.class);

        first.close();
        first.close();
        service.acquireSlot().close();
        second.close();
    }

    @Test
    void aSuccessfulPartClosesItsStream() throws Exception {
        ChallengeSubmission submission = submission("Photo", user("Camille"), "[\"proofs/u/c/a.jpg\"]");
        when(submissions.findEligibleWithMembers(List.of(submission.getId()))).thenReturn(List.of(submission));
        when(proofStorage.parseProofFiles(submission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg"));
        when(proofStorage.contentTypeForProofKey(any())).thenReturn("image/jpeg");
        when(storage.openIfPresent("proofs/u/c/a.jpg")).thenReturn(Optional.of(new ByteArrayInputStream(new byte[]{1, 2, 3})));
        TrackingOutputStream out = new TrackingOutputStream();

        service.writePart(plan(entry(submission, 0)), out);

        assertThat(out.closed).isTrue();
        Map<String, byte[]> zip = unzip(out.toByteArray());
        assertThat(zip).containsKey("mediatheque-2026-10-02/index.csv");
    }

    /**
     * Le premier fichier passe en entier, le second casse en route. Le premier pese plus
     * que le tampon de 64 Kio, donc des octets sont vraiment partis ; mais le zip n'a ni
     * index ni repertoire central. Il est visiblement casse, pas faussement complet.
     */
    @Test
    void aStorageFailureMidFileLeavesTheZipBroken(@TempDir Path tempDir) throws Exception {
        ChallengeSubmission submission = submission("Photo", user("Camille"), "[\"proofs/u/c/a.jpg\",\"proofs/u/c/b.jpg\"]");
        when(submissions.findEligibleWithMembers(List.of(submission.getId()))).thenReturn(List.of(submission));
        when(proofStorage.parseProofFiles(submission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg", "proofs/u/c/b.jpg"));
        when(proofStorage.contentTypeForProofKey(any())).thenReturn("image/jpeg");
        byte[] firstFile = new byte[100 * 1024];
        when(storage.openIfPresent("proofs/u/c/a.jpg")).thenReturn(Optional.of(new ByteArrayInputStream(firstFile)));
        when(storage.openIfPresent("proofs/u/c/b.jpg")).thenReturn(Optional.of(failingAfter(new byte[]{1, 2, 3})));
        TrackingOutputStream out = new TrackingOutputStream();

        assertThatThrownBy(() -> service.writePart(plan(entry(submission, 0), entry(submission, 1)), out))
                .isInstanceOf(IOException.class);

        assertThat(out.closed).isFalse();
        byte[] sent = out.toByteArray();
        String first = "mediatheque-2026-10-02/photo/2026-09-14_camille_" + submission.getId().toString().substring(0, 8) + "_1.jpg";
        // Le tampon s'est vide au moins une fois : l'en-tete du premier fichier est bien parti.
        assertThat(new String(sent, StandardCharsets.ISO_8859_1)).startsWith("PK\u0003\u0004").contains(first);
        assertThat(entryNamesTolerant(sent)).contains(first).doesNotContain("mediatheque-2026-10-02/index.csv");
        Path broken = Files.write(tempDir.resolve("partie.zip"), sent);
        assertThatThrownBy(() -> new ZipFile(broken.toFile()).close())
                .as("sans repertoire central, le zip ne s'ouvre pas")
                .isInstanceOf(ZipException.class);
    }

    /**
     * Le client part au milieu d'un fichier : le flux S3 est abandonne avant d'etre ferme.
     * Le fermer seul lirait tout le reste de l'objet, jusqu'a 40 Mio pour rien.
     */
    @Test
    void aClientLeavingMidFileAbandonsTheStorageStream() {
        AbortableTestStream source = AbortableTestStream.of(new byte[200 * 1024]);
        givenOneFile(source);
        OutputStream clientGone = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        assertThatThrownBy(() -> service.writePart(plan(entry(onlySubmission, 0)), clientGone)).isInstanceOf(IOException.class);

        assertThat(source.events()).containsExactly("abort", "close");
    }

    @Test
    void aStorageFailureMidFileAbandonsTheStorageStream() {
        AbortableTestStream source = AbortableTestStream.failingAfter(new byte[]{1, 2, 3});
        givenOneFile(source);

        assertThatThrownBy(() -> write(plan(entry(onlySubmission, 0)))).isInstanceOf(IOException.class);

        assertThat(source.events()).containsExactly("abort", "close");
    }

    @Test
    void aFileReadToTheEndIsClosedWithoutBeingAbandoned() throws Exception {
        AbortableTestStream source = AbortableTestStream.of(new byte[]{1, 2, 3});
        givenOneFile(source);

        write(plan(entry(onlySubmission, 0)));

        assertThat(source.events()).containsExactly("close");
    }

    private final ChallengeSubmission onlySubmission = submission("Photo", user("Camille"), "[\"proofs/u/c/a.jpg\"]");

    private void givenOneFile(InputStream source) {
        when(submissions.findEligibleWithMembers(List.of(onlySubmission.getId()))).thenReturn(List.of(onlySubmission));
        when(proofStorage.parseProofFiles(onlySubmission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg"));
        when(proofStorage.contentTypeForProofKey(any())).thenReturn("image/jpeg");
        when(storage.openIfPresent("proofs/u/c/a.jpg")).thenReturn(Optional.of(source));
    }

    private MediaLibraryExportService.PartPlan plan(MediaLibraryExportService.PartEntry... entries) {
        return new MediaLibraryExportService.PartPlan(Instant.parse("2026-10-02T08:00:00Z"), 1, 1, List.of(entries));
    }

    private static MediaLibraryExportService.PartEntry entry(ChallengeSubmission submission, int index) {
        return new MediaLibraryExportService.PartEntry(submission.getId(), index, "Alex", Instant.parse("2026-10-01T08:00:00Z"));
    }

    private byte[] write(MediaLibraryExportService.PartPlan plan) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writePart(plan, out);
        return out.toByteArray();
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    /** Quelques octets, puis une panne : simule S3 qui coupe au milieu d'un fichier. */
    private static InputStream failingAfter(byte[] prefix) {
        return new InputStream() {
            private int position = 0;

            @Override
            public int read() throws IOException {
                if (position < prefix.length) {
                    return prefix[position++] & 0xFF;
                }
                throw new IOException("Panne de stockage simulee au milieu du fichier.");
            }
        };
    }

    /** Le zip casse n'a pas de repertoire central : la lecture s'arrete sans erreur propre. */
    private static List<String> entryNamesTolerant(byte[] bytes) {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                zip.readAllBytes();
            }
        } catch (IOException ignored) {
            // Attendu : le zip est volontairement laisse casse, sans repertoire central.
        }
        return names;
    }

    private static class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private MediaLibraryRetainedMedia.Retained retainedOf(ChallengeSubmission submission, int index, String key) {
        return new MediaLibraryRetainedMedia.Retained(
                new MediaLibrarySelection(new MediaSelectionKey(submission.getId(), index), admin, Instant.now()), submission, key);
    }

    private static AppUser user(String name) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub(name);
        user.setDisplayName(name);
        return user;
    }

    private static ChallengeSubmission submission(String title, AppUser author, String proofData) {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setTitle(title);
        challenge.setCategory("Soirée");
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setStatus("VALIDATED");
        submission.setProofType("MEDIA");
        submission.setProofData(proofData);
        ReflectionTestUtils.setField(submission, "createdAt", Instant.parse("2026-09-14T10:00:00Z"));
        return submission;
    }
}
