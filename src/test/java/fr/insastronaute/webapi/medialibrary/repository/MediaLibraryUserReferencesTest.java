package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.medialibrary.domain.MediaExportItemKey;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExport;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExportItem;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryGrant;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryUserReferences;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les gestes de la Mediatheque qui nomment un compte sans ON DELETE : accorder l'acces,
 * retenir un media, figer un export. Un compte ainsi nomme ne peut pas disparaitre de
 * la table users ; la suppression d'un joueur doit le savoir avant d'essayer.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(MediaLibraryUserReferences.class)
class MediaLibraryUserReferencesTest {

    @Autowired private MediaLibraryUserReferences references;
    @Autowired private MediaLibraryGrantRepository grants;
    @Autowired private MediaLibrarySelectionRepository selections;
    @Autowired private MediaLibraryExportRepository exports;
    @Autowired private MediaLibraryExportItemRepository items;
    @Autowired private EntityManager entityManager;

    @Test
    void whoGrantedAccessIsNamedButNotWhoReceivedIt() {
        AppUser admin = user("granting-admin");
        AppUser staff = user("granted-staff");
        grants.save(new MediaLibraryGrant(staff, admin, Instant.now()));
        flush();

        assertThat(references.isReferenced(admin.getId())).isTrue();
        // Sa propre ligne d'acces part avec lui (ON DELETE CASCADE) : elle ne le retient pas.
        assertThat(references.isReferenced(staff.getId())).isFalse();
    }

    @Test
    void whoRetainedAMediaIsNamed() {
        AppUser selector = user("selector");
        UUID submissionId = submission(user("author")).getId();
        flush();
        selections.insertIfAbsent(submissionId, 0, selector.getId(), Instant.now());

        assertThat(references.isReferenced(selector.getId())).isTrue();
    }

    @Test
    void whoFrozeAnExportIsNamed() {
        AppUser creator = user("export-creator");
        exports.save(new MediaLibraryExport(UUID.randomUUID(), creator, Instant.now(), 0, 0L, 0));
        flush();

        assertThat(references.isReferenced(creator.getId())).isTrue();
    }

    /** La selection a pu bouger depuis : l'export garde le nom de qui avait retenu chaque media. */
    @Test
    void whoRetainedAnExportedMediaIsNamedEvenOnceTheSelectionMoved() {
        AppUser creator = user("export-creator-2");
        AppUser formerSelector = user("former-selector");
        UUID submissionId = submission(user("author-2")).getId();
        UUID exportId = UUID.randomUUID();
        exports.save(new MediaLibraryExport(exportId, creator, Instant.now(), 1, 100L, 1));
        items.save(new MediaLibraryExportItem(new MediaExportItemKey(exportId, 0), 1, submissionId, 0, 100L,
                formerSelector, Instant.now()));
        flush();

        assertThat(references.isReferenced(formerSelector.getId())).isTrue();
    }

    @Test
    void anOrdinaryPlayerIsNotNamed() {
        AppUser player = user("player");
        submission(player);
        flush();

        assertThat(references.isReferenced(player.getId())).isFalse();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private AppUser user(String subject) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName("Joueur " + subject);
        entityManager.persist(user);
        return user;
    }

    private ChallengeSubmission submission(AppUser author) {
        Challenge challenge = new Challenge();
        challenge.setTitle("Defi");
        challenge.setPointsReward(10);
        challenge.setValidationMode("ADMIN");
        entityManager.persist(challenge);

        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setStatus("VALIDATED");
        submission.setProofType("MEDIA");
        submission.setProofData("[\"proofs/u/c/a.jpg\"]");
        entityManager.persist(submission);
        return submission;
    }
}
