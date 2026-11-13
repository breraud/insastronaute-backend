package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import fr.insastronaute.webapi.medialibrary.dto.MediaRefDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionPatchDto;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaLibrarySelectionServiceTest {

    private final MediaLibraryAccess access = mock(MediaLibraryAccess.class);
    private final MediaLibrarySelectionRepository selections = mock(MediaLibrarySelectionRepository.class);
    private final MediaLibrarySubmissionRepository submissions = mock(MediaLibrarySubmissionRepository.class);
    private final ProofStorageService proofStorage = mock(ProofStorageService.class);
    private final MediaLibraryRetainedMedia retained = mock(MediaLibraryRetainedMedia.class);
    private final MediaSizeLookup sizes = mock(MediaSizeLookup.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MediaLibrarySelectionService service = new MediaLibrarySelectionService(
            access, selections, submissions, proofStorage, retained, sizes, users);

    private final AppUser actor = user();

    @Test
    void removesThenAddsEachMedia() {
        ChallengeSubmission submission = submission("[\"proofs/u/c/a.jpg\",\"proofs/u/c/b.jpg\"]");
        when(users.findByKeycloakSub("staff-sub")).thenReturn(Optional.of(actor));
        when(submissions.findEligibleWithMembers(anyCollection())).thenReturn(List.of(submission));
        when(proofStorage.parseProofFiles(submission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg", "proofs/u/c/b.jpg"));

        service.patch("staff-sub", Set.of("ROLE_ADMIN"), new MediaSelectionPatchDto(
                List.of(new MediaRefDto(submission.getId(), 1)),
                List.of(new MediaRefDto(submission.getId(), 0))));

        var order = inOrder(selections);
        order.verify(selections).deleteOne(submission.getId(), 0);
        order.verify(selections).insertIfAbsent(eq(submission.getId()), eq(1), eq(actor.getId()), any(Instant.class));
    }

    @Test
    void oneUnavailableMediaRefusesTheWholeRequest() {
        ChallengeSubmission submission = submission("[\"proofs/u/c/a.jpg\"]");
        UUID gone = UUID.randomUUID();
        when(users.findByKeycloakSub("staff-sub")).thenReturn(Optional.of(actor));
        when(submissions.findEligibleWithMembers(anyCollection())).thenReturn(List.of(submission));
        when(proofStorage.parseProofFiles(submission.getProofData())).thenReturn(List.of("proofs/u/c/a.jpg"));

        assertThatThrownBy(() -> service.patch("staff-sub", Set.of("ROLE_ADMIN"), new MediaSelectionPatchDto(
                List.of(new MediaRefDto(submission.getId(), 0), new MediaRefDto(gone, 0), new MediaRefDto(submission.getId(), 5)),
                List.of(new MediaRefDto(submission.getId(), 0)))))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("MEDIA_LIBRARY_MEDIA_UNAVAILABLE"));

        verify(selections, never()).insertIfAbsent(any(), anyInt(), any(), any());
        verify(selections, never()).deleteOne(any(), anyInt());
    }

    @Test
    void aSelectionBeyondTheProofCountsAsUnavailable() {
        ChallengeSubmission submission = submission("[\"proofs/u/c/a.jpg\"]");
        MediaLibrarySelection kept = new MediaLibrarySelection(new MediaSelectionKey(submission.getId(), 0), actor, Instant.now());
        when(retained.available()).thenReturn(List.of(new MediaLibraryRetainedMedia.Retained(kept, submission, "proofs/u/c/a.jpg")));
        when(selections.count()).thenReturn(3L);
        when(sizes.sizesOf(anyCollection())).thenReturn(Map.of("proofs/u/c/a.jpg", 2048L));

        var summary = service.summary("staff-sub", Set.of("ROLE_ADMIN"));

        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalBytes()).isEqualTo(2048L);
        assertThat(summary.unavailableCount()).isEqualTo(2);
    }

    @Test
    void retainedMediaAreCountedForTheChallengesAboutToBePurged() {
        ChallengeSubmission inPurged = submission("[\"proofs/u/c/a.jpg\"]", "VALIDATED");
        ChallengeSubmission elsewhere = submission("[\"proofs/u/c/b.jpg\"]", "VALIDATED");
        when(retained.available()).thenReturn(List.of(
                new MediaLibraryRetainedMedia.Retained(new MediaLibrarySelection(new MediaSelectionKey(inPurged.getId(), 0), actor, Instant.now()), inPurged, "k1"),
                new MediaLibraryRetainedMedia.Retained(new MediaLibrarySelection(new MediaSelectionKey(elsewhere.getId(), 0), actor, Instant.now()), elsewhere, "k2")));

        assertThat(service.retainedInChallenges(List.of(inPurged.getChallenge().getId()))).isEqualTo(1);
    }

    @Test
    void retainedMediaOnlyCountsDecidedSubmissions() {
        ChallengeSubmission validated = submission("[\"proofs/u/c/a.jpg\"]", "VALIDATED");
        ChallengeSubmission rejected = submission("[\"proofs/u/c/b.jpg\"]", "REJECTED");
        ChallengeSubmission pending = submission("[\"proofs/u/c/c.jpg\"]", "PENDING");
        UUID challengeId = validated.getChallenge().getId();
        rejected.getChallenge().setId(challengeId);
        pending.getChallenge().setId(challengeId);
        when(retained.available()).thenReturn(List.of(
                new MediaLibraryRetainedMedia.Retained(new MediaLibrarySelection(new MediaSelectionKey(validated.getId(), 0), actor, Instant.now()), validated, "k1"),
                new MediaLibraryRetainedMedia.Retained(new MediaLibrarySelection(new MediaSelectionKey(rejected.getId(), 0), actor, Instant.now()), rejected, "k2"),
                new MediaLibraryRetainedMedia.Retained(new MediaLibrarySelection(new MediaSelectionKey(pending.getId(), 0), actor, Instant.now()), pending, "k3")));

        assertThat(service.retainedInChallenges(List.of(challengeId))).isEqualTo(2);
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("staff-sub");
        return user;
    }

    private static ChallengeSubmission submission(String proofData) {
        return submission(proofData, "VALIDATED");
    }

    private static ChallengeSubmission submission(String proofData, String status) {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setProofData(proofData);
        submission.setStatus(status);
        return submission;
    }
}
