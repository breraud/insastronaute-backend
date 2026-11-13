package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibrarySelection;
import fr.insastronaute.webapi.medialibrary.domain.MediaSelectionKey;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySelectionRepository;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibrarySubmissionRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les medias retenus qui restent montrables : la seule porte entre la selection et
 * l'export, les compteurs et la grille. Une ligne de trop, et une photo qu'un joueur a
 * fait retirer part dans l'after movie.
 */
class MediaLibraryRetainedMediaTest {

    private final MediaLibrarySelectionRepository selections = mock(MediaLibrarySelectionRepository.class);
    private final MediaLibrarySubmissionRepository submissions = mock(MediaLibrarySubmissionRepository.class);
    private final ProofStorageService proofStorage = mock(ProofStorageService.class);
    private final MediaLibraryRetainedMedia retained = new MediaLibraryRetainedMedia(selections, submissions, proofStorage);

    private final AppUser selector = user("Alex");

    @Test
    void aSubmissionThatIsNoLongerEligibleIsDropped() {
        ChallengeSubmission eligible = submission(challenge("Photo"), user("Camille"), Set.of(), "proofs/u/c/a.jpg");
        ChallengeSubmission withdrawn = submission(challenge("Photo"), user("Léo"), Set.of(), "proofs/u/c/b.jpg");
        when(selections.findAllWithSelector()).thenReturn(List.of(row(eligible, 0), row(withdrawn, 0)));
        // L'eligibilite relue ne rend plus la seconde : son auteur a demande l'effacement.
        when(submissions.findEligibleWithMembers(any())).thenReturn(List.of(eligible));

        assertThat(retained.available())
                .extracting(item -> item.submission().getId())
                .containsExactly(eligible.getId());
        verify(submissions).findEligibleWithMembers(argThat(ids ->
                ids.size() == 2 && ids.contains(eligible.getId()) && ids.contains(withdrawn.getId())));
    }

    @Test
    void anIndexBeyondTheProofFilesIsDropped() {
        ChallengeSubmission submission = submission(challenge("Photo"), user("Camille"), Set.of(), "proofs/u/c/a.jpg");
        when(selections.findAllWithSelector()).thenReturn(List.of(row(submission, 0), row(submission, 1), row(submission, 7)));
        when(submissions.findEligibleWithMembers(any())).thenReturn(List.of(submission));

        assertThat(retained.available())
                .extracting(item -> item.selection().getId().getMediaIndex())
                .containsExactly(0);
    }

    @Test
    void twoMediaOfTheSameSubmissionAreBothKeptWithTheirOwnKeys() {
        ChallengeSubmission submission = submission(challenge("Photo"), user("Camille"), Set.of(),
                "proofs/u/c/a.jpg", "proofs/u/c/b.mp4");
        when(selections.findAllWithSelector()).thenReturn(List.of(row(submission, 1), row(submission, 0)));
        when(submissions.findEligibleWithMembers(any())).thenReturn(List.of(submission));

        assertThat(retained.available())
                .extracting(item -> item.selection().getId().getMediaIndex() + "=" + item.objectKey())
                .containsExactly("1=proofs/u/c/b.mp4", "0=proofs/u/c/a.jpg");
        // Une seule soumission a relire, meme pour deux lignes.
        verify(submissions).findEligibleWithMembers(argThat(ids -> ids.size() == 1 && ids.contains(submission.getId())));
    }

    @Test
    void anEmptySelectionReadsNoSubmission() {
        when(selections.findAllWithSelector()).thenReturn(List.of());

        assertThat(retained.available()).isEmpty();
        assertThat(retained.countByChallenge()).isEmpty();
        assertThat(retained.countByMember()).isEmpty();
        verify(submissions, never()).findEligibleWithMembers(any());
    }

    @Test
    void countsGoToTheChallengeAndToEveryMember() {
        Challenge photo = challenge("Photo");
        Challenge song = challenge("Chanson");
        AppUser camille = user("Camille");
        AppUser leo = user("Léo");
        AppUser zoe = user("Zoé");
        ChallengeSubmission group = submission(photo, camille, Set.of(leo, zoe), "proofs/u/c/a.jpg", "proofs/u/c/b.jpg");
        ChallengeSubmission solo = submission(song, leo, Set.of(), "proofs/u/c/c.mp4");
        when(selections.findAllWithSelector()).thenReturn(List.of(row(group, 0), row(group, 1), row(solo, 0)));
        when(submissions.findEligibleWithMembers(any())).thenReturn(List.of(group, solo));

        assertThat(retained.countByChallenge()).isEqualTo(Map.of(photo.getId(), 2L, song.getId(), 1L));
        // L'auteur et chaque participant comptent chaque media retenu de la soumission.
        assertThat(retained.countByMember()).isEqualTo(Map.of(camille.getId(), 2L, leo.getId(), 3L, zoe.getId(), 2L));
    }

    private MediaLibrarySelection row(ChallengeSubmission submission, int index) {
        return new MediaLibrarySelection(new MediaSelectionKey(submission.getId(), index), selector,
                Instant.parse("2026-10-01T08:00:00Z"));
    }

    private ChallengeSubmission submission(Challenge challenge, AppUser author, Set<AppUser> participants, String... keys) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(new HashSet<>(participants));
        submission.setStatus("VALIDATED");
        submission.setProofType("MEDIA");
        String proofData = "proof-data-" + submission.getId();
        submission.setProofData(proofData);
        when(proofStorage.parseProofFiles(proofData)).thenReturn(List.of(keys));
        return submission;
    }

    private static Challenge challenge(String title) {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setTitle(title);
        return challenge;
    }

    private static AppUser user(String name) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub(name);
        user.setDisplayName(name);
        return user;
    }
}
