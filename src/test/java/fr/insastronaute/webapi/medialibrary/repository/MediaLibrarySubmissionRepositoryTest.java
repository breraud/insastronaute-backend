package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que la Mediatheque a le droit de montrer, condition par condition.
 *
 * <p>Chaque cas viole une seule condition de l'eligibilite : si l'un d'eux passait, une
 * photo qu'un joueur croyait vouee a disparaitre se retrouverait dans l'after movie.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MediaLibrarySubmissionRepositoryTest {

    private static final List<String> ALL = List.of("WAITING_CONFIRMATION", "PENDING", "VALIDATED", "REJECTED");
    private static final String PHOTO = "[\"proofs/u/c/a.jpg\"]";
    private static final String VIDEO = "[\"proofs/u/c/b.mp4\"]";

    @Autowired private MediaLibrarySubmissionRepository repository;
    @Autowired private MediaLibrarySelectionRepository selections;
    @Autowired private EntityManager entityManager;

    @Test
    void anOrdinaryMediaProofIsEligible() {
        ChallengeSubmission ok = submission(challenge("Photo"), user("ok"), Set.of(), "VALIDATED", "MEDIA", PHOTO);
        flush();

        assertThat(repository.findEligible(ok.getId())).isPresent();
    }

    @Test
    void eachExclusionHidesTheProof() {
        Challenge challenge = challenge("Defi");

        ChallengeSubmission text = submission(challenge, user("text"), Set.of(), "VALIDATED", "TEXT", "Bonjour");
        ChallengeSubmission purged = submission(challenge, user("purged"), Set.of(), "VALIDATED", "MEDIA", "[]");
        ChallengeSubmission purgedStamp = submission(challenge, user("stamp"), Set.of(), "VALIDATED", "MEDIA", PHOTO);
        purgedStamp.setProofMediaPurgedAt(Instant.now());

        Challenge deleted = challenge("Supprime");
        deleted.setDeletedAt(Instant.now());
        ChallengeSubmission inDeleted = submission(deleted, user("deleted-challenge"), Set.of(), "VALIDATED", "MEDIA", PHOTO);

        Challenge sensitive = challenge("Sensible");
        sensitive.setSensitive(true);
        ChallengeSubmission inSensitive = submission(sensitive, user("sensitive-challenge"), Set.of(), "PENDING", "MEDIA", PHOTO);

        ChallengeSubmission declaredSensitive = submission(challenge, user("declared"), Set.of(), "PENDING", "MEDIA", PHOTO);
        declaredSensitive.setSensitive(true);

        AppUser authorOptedOut = user("author-opted-out");
        authorOptedOut.setPurgeProofsAfterValidation(true);
        ChallengeSubmission byOptedOut = submission(challenge, authorOptedOut, Set.of(), "PENDING", "MEDIA", PHOTO);

        AppUser participantOptedOut = user("participant-opted-out");
        participantOptedOut.setPurgeProofsAfterValidation(true);
        ChallengeSubmission withOptedOut = submission(challenge, user("group-author"), Set.of(participantOptedOut), "PENDING", "MEDIA", PHOTO);

        AppUser deletedAuthor = user("deleted-author");
        deletedAuthor.setDeletedAt(Instant.now());
        ChallengeSubmission byDeleted = submission(challenge, deletedAuthor, Set.of(), "VALIDATED", "MEDIA", PHOTO);

        AppUser deletedParticipant = user("deleted-participant");
        deletedParticipant.setDeletedAt(Instant.now());
        ChallengeSubmission withDeleted = submission(challenge, user("group-author-2"), Set.of(deletedParticipant), "VALIDATED", "MEDIA", PHOTO);
        flush();

        for (ChallengeSubmission hidden : List.of(text, purged, purgedStamp, inDeleted, inSensitive, declaredSensitive,
                byOptedOut, withOptedOut, byDeleted, withDeleted)) {
            assertThat(repository.findEligible(hidden.getId()))
                    .as("la soumission de %s doit etre exclue", hidden.getUser().getKeycloakSub())
                    .isEmpty();
        }
    }

    @Test
    void searchFiltersByStatusTypeChallengePersonAndSelection() {
        Challenge photo = challenge("Photo");
        Challenge other = challenge("Autre");
        AppUser author = user("author");
        AppUser teammate = user("teammate");
        ChallengeSubmission validatedPhoto = submission(photo, author, Set.of(teammate), "VALIDATED", "MEDIA", PHOTO);
        ChallengeSubmission rejectedVideo = submission(photo, user("solo"), Set.of(), "REJECTED", "MEDIA", VIDEO);
        ChallengeSubmission elsewhere = submission(other, user("elsewhere"), Set.of(), "PENDING", "MEDIA", PHOTO);
        flush();
        selections.insertIfAbsent(rejectedVideo.getId(), 0, author.getId(), Instant.now());
        flush();

        PageRequest page = PageRequest.of(0, 24, Sort.by("createdAt", "id"));
        assertThat(repository.search(List.of("VALIDATED"), null, null, null, false, page).getContent())
                .extracting(ChallengeSubmission::getId).containsExactly(validatedPhoto.getId());
        assertThat(repository.search(ALL, "video", null, null, false, page).getContent())
                .extracting(ChallengeSubmission::getId).containsExactly(rejectedVideo.getId());
        assertThat(repository.search(ALL, "photo", photo.getId(), null, false, page).getContent())
                .extracting(ChallengeSubmission::getId).containsExactly(validatedPhoto.getId());
        assertThat(repository.search(ALL, null, null, teammate.getId(), false, page).getContent())
                .extracting(ChallengeSubmission::getId).containsExactly(validatedPhoto.getId());
        assertThat(repository.search(ALL, null, null, null, true, page).getContent())
                .extracting(ChallengeSubmission::getId).containsExactly(rejectedVideo.getId());
        assertThat(repository.search(ALL, null, null, null, false, page).getTotalElements()).isEqualTo(3);
        assertThat(elsewhere.getId()).isNotNull();
    }

    @Test
    void countsByChallengeAuthorAndParticipant() {
        // V70 (trigger sync_challenge_active_submission_memberships) n'autorise qu'une
        // seule soumission active (WAITING_CONFIRMATION, PENDING, VALIDATED) par defi et
        // par auteur : challenge_active_memberships a pour cle (challenge_id, user_id).
        // La deuxieme soumission de l'auteur est donc REJECTED (inactive pour ce trigger,
        // donc sans conflit), mais elle reste comptee ici puisque REJECTED fait partie de ALL.
        Challenge photo = challenge("Photo");
        AppUser author = user("author");
        AppUser teammate = user("teammate");
        submission(photo, author, Set.of(teammate), "VALIDATED", "MEDIA", PHOTO);
        submission(photo, author, Set.of(), "REJECTED", "MEDIA", VIDEO);
        flush();

        assertThat(repository.countByChallenge(ALL, null))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.title()).isEqualTo("Photo");
                    assertThat(row.submissionCount()).isEqualTo(2L);
                });
        assertThat(repository.countByAuthor(ALL, null))
                .containsExactly(new PersonCountRow(author.getId(), 2L));
        assertThat(repository.countByParticipant(ALL, null))
                .containsExactly(new PersonCountRow(teammate.getId(), 1L));
        assertThat(repository.countByChallenge(ALL, "video"))
                .singleElement()
                .satisfies(row -> assertThat(row.submissionCount()).isEqualTo(1L));
    }

    /**
     * L'ordre des vues par personne et de la selection : defi par defi, puis la
     * chronologie. Le tri traverse une association (challenge.title) : seule une vraie
     * requete dit qu'il est accepte et applique. Insertions volontairement a rebours,
     * sur le titre comme sur la date.
     */
    @Test
    void personAndSelectionViewsSortByChallengeTitleThenTime() {
        Challenge zebra = challenge("Zebre au RU");
        Challenge apero = challenge("Apero");
        AppUser person = user("person");
        ChallengeSubmission zebraLate = at("2026-09-12T10:00:00Z",
                () -> submission(zebra, user("zebra-author"), Set.of(person), "VALIDATED", "MEDIA", PHOTO));
        // REJECTED : V70 n'admet qu'une soumission active par defi et par membre, et la
        // personne participe deja a zebraLate.
        ChallengeSubmission zebraEarly = at("2026-09-11T10:00:00Z",
                () -> submission(zebra, person, Set.of(), "REJECTED", "MEDIA", PHOTO));
        ChallengeSubmission apero1 = at("2026-09-15T10:00:00Z",
                () -> submission(apero, user("apero-author"), Set.of(person), "VALIDATED", "MEDIA", VIDEO));
        flush();
        selections.insertIfAbsent(zebraLate.getId(), 0, person.getId(), Instant.now());
        selections.insertIfAbsent(apero1.getId(), 0, person.getId(), Instant.now());
        flush();

        PageRequest byChallenge = PageRequest.of(0, 24, Sort.by("challenge.title", "createdAt", "id"));
        assertThat(repository.search(ALL, null, null, person.getId(), false, byChallenge).getContent())
                .extracting(ChallengeSubmission::getId)
                .containsExactly(apero1.getId(), zebraEarly.getId(), zebraLate.getId());
        assertThat(repository.search(ALL, null, null, null, true, byChallenge).getContent())
                .extracting(ChallengeSubmission::getId)
                .containsExactly(apero1.getId(), zebraLate.getId());
    }

    private static ChallengeSubmission at(String instant, java.util.function.Supplier<ChallengeSubmission> persist) {
        TimeTravelContext.setForcedTime(Instant.parse(instant));
        try {
            return persist.get();
        } finally {
            TimeTravelContext.clear();
        }
    }

    @Test
    void membersAreLoadedWithTheEligibleSubmissions() {
        AppUser teammate = user("teammate");
        ChallengeSubmission group = submission(challenge("Groupe"), user("author"), Set.of(teammate), "VALIDATED", "MEDIA", PHOTO);
        flush();

        List<ChallengeSubmission> found = repository.findEligibleWithMembers(List.of(group.getId()));
        entityManager.clear();

        assertThat(found).singleElement().satisfies(submission -> {
            assertThat(submission.getChallenge().getTitle()).isEqualTo("Groupe");
            assertThat(submission.getParticipants()).extracting(AppUser::getKeycloakSub).containsExactly("teammate");
        });
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Challenge challenge(String title) {
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setPointsReward(10);
        challenge.setValidationMode("ADMIN");
        entityManager.persist(challenge);
        return challenge;
    }

    private AppUser user(String subject) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName("Joueur " + subject);
        entityManager.persist(user);
        return user;
    }

    private ChallengeSubmission submission(Challenge challenge, AppUser author, Set<AppUser> participants,
                                           String status, String proofType, String proofData) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(new java.util.HashSet<>(participants));
        submission.setStatus(status);
        submission.setProofType(proofType);
        submission.setProofData(proofData);
        entityManager.persist(submission);
        return submission;
    }
}
