package fr.insastronaute.webapi.medialibrary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import fr.insastronaute.webapi.medialibrary.dto.MediaSubmissionDto;
import fr.insastronaute.webapi.medialibrary.repository.ChallengeCountRow;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.medialibrary.repository.PersonCountRow;
import fr.insastronaute.webapi.proof.domain.ProofUpload;
import fr.insastronaute.webapi.proof.repository.ProofUploadRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaLibraryBrowseServiceTest {

    private final MediaLibraryAccess access = mock(MediaLibraryAccess.class);
    private final MediaLibrarySubmissionRepository submissions = mock(MediaLibrarySubmissionRepository.class);
    private final MediaLibrarySelectionRepository selections = mock(MediaLibrarySelectionRepository.class);
    private final ProofUploadRepository uploads = mock(ProofUploadRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProofStorageService proofStorage = proofStorage();
    private final MediaSizeLookup sizes = new MediaSizeLookup(uploads);
    private final MediaLibraryRetainedMedia retained = new MediaLibraryRetainedMedia(selections, submissions, proofStorage);
    private final MediaLibraryBrowseService service = new MediaLibraryBrowseService(
            access, submissions, selections, sizes, retained, proofStorage, users);

    /** ProofStorageService ne sert ici qu'a lire proof_data et a deduire un type. */
    private static ProofStorageService proofStorage() {
        ProofStorageService storage = mock(ProofStorageService.class);
        when(storage.parseProofFiles(any())).thenAnswer(call -> parse(call.getArgument(0)));
        when(storage.contentTypeForProofKey(any())).thenAnswer(call -> {
            String key = call.getArgument(0);
            return key.endsWith(".mp4") ? "video/mp4" : "image/jpeg";
        });
        return storage;
    }

    /** Le meme algorithme que ProofStorageService.parseProofFiles : JSON, ou une cle par ligne. */
    private static List<String> parse(String proofData) throws Exception {
        if (proofData == null || proofData.isBlank()) return List.of();
        String trimmed = proofData.trim();
        if (trimmed.startsWith("[")) {
            return new ObjectMapper().readValue(trimmed, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        }
        return proofData.lines().filter(line -> !line.isBlank()).toList();
    }

    @Test
    void aSubmissionPageCarriesMediaSizesAndWhoRetainedThem() {
        AppUser author = user("Camille");
        AppUser teammate = user("Léo");
        AppUser selector = user("Alex");
        ChallengeSubmission submission = submission(author, Set.of(teammate),
                "[\"proofs/u/c/a.jpg\",\"proofs/u/c/b.mp4\"]");
        when(submissions.search(eq(MediaLibraryFilter.ALL_STATUSES), isNull(), isNull(), isNull(), eq(false), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(submission), call.getArgument(5), 1));
        when(submissions.findEligibleWithMembers(anyCollection())).thenReturn(List.of(submission));
        when(selections.findBySubmissionIds(any())).thenReturn(List.of(
                new MediaLibrarySelection(new MediaSelectionKey(submission.getId(), 1), selector, Instant.parse("2026-10-01T08:00:00Z"))));
        ProofUpload uploadA = upload("proofs/u/c/a.jpg", 2048L);
        when(uploads.findByObjectKeyIn(anyCollection())).thenReturn(List.of(uploadA));

        var page = service.submissions("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null), null, null, false, 0);

        MediaSubmissionDto dto = page.content().getFirst();
        assertThat(dto.author().displayName()).isEqualTo("Camille");
        assertThat(dto.participants()).extracting(p -> p.displayName()).containsExactly("Léo");
        assertThat(dto.media()).hasSize(2);
        assertThat(dto.media().get(0).sizeBytes()).isEqualTo(2048L);
        assertThat(dto.media().get(0).selected()).isNull();
        assertThat(dto.media().get(1).contentType()).isEqualTo("video/mp4");
        assertThat(dto.media().get(1).selected().by().displayName()).isEqualTo("Alex");
        assertThat(page.size()).isEqualTo(24);
    }

    @Test
    void legacyNewlineProofsAreListedLikeJsonOnes() {
        ChallengeSubmission legacy = submission(user("Ancien"), Set.of(), "proofs/u/c/a.jpg\nproofs/u/c/b.jpg\n");
        when(submissions.search(any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(legacy), call.getArgument(5), 1));
        when(submissions.findEligibleWithMembers(anyCollection())).thenReturn(List.of(legacy));
        when(selections.findBySubmissionIds(any())).thenReturn(List.of());
        when(uploads.findByObjectKeyIn(anyCollection())).thenReturn(List.of());

        var page = service.submissions("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null), null, null, false, 0);

        assertThat(page.content().getFirst().media()).extracting(m -> m.index()).containsExactly(0, 1);
    }

    @Test
    void membersOfAWholePageAreLoadedInOneQuery() {
        ChallengeSubmission first = submission(user("Dédé"), Set.of(), "proofs/u/c/a.jpg");
        ChallengeSubmission second = submission(user("Zoé"), Set.of(), "proofs/u/c/b.jpg");
        when(submissions.search(any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(first, second), call.getArgument(5), 2));
        when(submissions.findEligibleWithMembers(anyCollection())).thenReturn(List.of(first, second));
        when(selections.findBySubmissionIds(any())).thenReturn(List.of());
        when(uploads.findByObjectKeyIn(anyCollection())).thenReturn(List.of());

        var page = service.submissions("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null), null, null, false, 0);

        assertThat(page.content()).extracting(dto -> dto.author().displayName()).containsExactly("Dédé", "Zoé");
        verify(submissions, times(1)).findEligibleWithMembers(argThat(ids ->
                ids.size() == 2 && ids.contains(first.getId()) && ids.contains(second.getId())));
    }

    @Test
    void challengesCarryTheirRetainedCounts() {
        UUID challengeId = UUID.randomUUID();
        when(submissions.countByChallenge(MediaLibraryFilter.ALL_STATUSES, null))
                .thenReturn(List.of(new ChallengeCountRow(challengeId, "Photo", "Soirée", 4L)));
        when(selections.findAllWithSelector()).thenReturn(List.of());

        assertThat(service.challenges("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null)))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.submissionCount()).isEqualTo(4L);
                    assertThat(dto.retainedCount()).isZero();
                });
    }

    @Test
    void peopleMergeAuthorAndParticipantCountsAndFilterByName() {
        AppUser camille = user("Camille Martin");
        AppUser leo = user("Léo Durand");
        when(submissions.countByAuthor(any(), any())).thenReturn(List.of(new PersonCountRow(camille.getId(), 2L)));
        when(submissions.countByParticipant(any(), any())).thenReturn(List.of(
                new PersonCountRow(camille.getId(), 1L), new PersonCountRow(leo.getId(), 1L)));
        when(users.findAllById(any())).thenReturn(List.of(camille, leo));
        when(selections.findAllWithSelector()).thenReturn(List.of());

        var all = service.people("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null), null, 0);
        var leoOnly = service.people("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null), "leo", 0);

        assertThat(all.content()).extracting(p -> p.displayName() + ":" + p.submissionCount())
                .containsExactly("Camille Martin:3", "Léo Durand:1");
        assertThat(leoOnly.content()).extracting(p -> p.displayName()).containsExactly("Léo Durand");
    }

    /** Une page absurde est une page vide, pas un 500 : page * 50 debordait d'un int. */
    @Test
    void aPageFarBeyondTheLastIsEmpty() {
        AppUser camille = user("Camille Martin");
        when(submissions.countByAuthor(any(), any())).thenReturn(List.of(new PersonCountRow(camille.getId(), 2L)));
        when(submissions.countByParticipant(any(), any())).thenReturn(List.of());
        when(users.findAllById(any())).thenReturn(List.of(camille));
        when(selections.findAllWithSelector()).thenReturn(List.of());

        var page = service.people("sub", Set.of("ROLE_ADMIN"), MediaLibraryFilter.of(null, null), null, Integer.MAX_VALUE);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.number()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void anUnknownStatusOrTypeIsRefused() {
        assertThatThrownBy(() -> MediaLibraryFilter.of(List.of("DELETED"), null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> MediaLibraryFilter.of(null, "audio")).isInstanceOf(BadRequestException.class);
        assertThat(MediaLibraryFilter.of(List.of("validated"), "VIDEO").statuses()).containsExactly("VALIDATED");
    }

    @Test
    void nothingIsReadWithoutAccess() {
        doThrow(new ForbiddenException("non")).when(access).require(any(), any());

        assertThatThrownBy(() -> service.challenges("sub", Set.of(), MediaLibraryFilter.of(null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    private static AppUser user(String name) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub(name);
        user.setDisplayName(name);
        return user;
    }

    private static ChallengeSubmission submission(AppUser author, Set<AppUser> participants, String proofData) {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setTitle("Photo");
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(new java.util.HashSet<>(participants));
        submission.setStatus("VALIDATED");
        submission.setProofType("MEDIA");
        submission.setProofData(proofData);
        ReflectionTestUtils.setField(submission, "createdAt", Instant.parse("2026-09-14T10:00:00Z"));
        return submission;
    }

    private static ProofUpload upload(String key, Long size) {
        ProofUpload upload = mock(ProofUpload.class);
        when(upload.getObjectKey()).thenReturn(key);
        when(upload.getSizeBytes()).thenReturn(size);
        return upload;
    }
}
