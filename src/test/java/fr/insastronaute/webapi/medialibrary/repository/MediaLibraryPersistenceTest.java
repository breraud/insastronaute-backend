package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.medialibrary.domain.MediaExportItemKey;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExport;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryExportItem;
import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryGrant;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que la Mediatheque range en base, relu tel qu'elle le lira.
 */
@DataJpaTest
// challenges.auto_challenge_parameters declare jsonb : sans la base du profil de test,
// la table des defis n'est pas creee et le contexte ne demarre pas.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MediaLibraryPersistenceTest {

    @Autowired private MediaLibraryGrantRepository grants;
    @Autowired private MediaLibrarySelectionRepository selections;
    @Autowired private MediaLibraryExportRepository exports;
    @Autowired private MediaLibraryExportItemRepository items;
    @Autowired private EntityManager entityManager;

    @Test
    void aGrantIsFoundByTheKeycloakSubjectOfItsHolder() {
        AppUser staff = user("staff-sub", "Camille Martin");
        AppUser admin = user("admin-sub", "Alex Admin");
        grants.save(new MediaLibraryGrant(staff, admin, Instant.parse("2026-10-02T10:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        assertThat(grants.existsByUser_KeycloakSub("staff-sub")).isTrue();
        assertThat(grants.existsByUser_KeycloakSub("admin-sub")).isFalse();
        assertThat(grants.findAllWithUsers())
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.getUser().getDisplayName()).isEqualTo("Camille Martin");
                    assertThat(grant.getGrantedBy().getDisplayName()).isEqualTo("Alex Admin");
                });
    }

    @Test
    void retainingTwiceKeepsTheFirstLineAndRemovingIsIdempotent() {
        AppUser first = user("first-sub", "Premier");
        AppUser second = user("second-sub", "Second");
        // Une vraie soumission persistee : la cle etrangere posee par V187 sur
        // media_library_selection l'exige (Postgres la verifie, H2 laissait passer un UUID au hasard).
        UUID submissionId = submission(first).getId();
        entityManager.flush();
        Instant early = Instant.parse("2026-10-01T08:00:00Z");

        assertThat(selections.insertIfAbsent(submissionId, 2, first.getId(), early)).isEqualTo(1);
        assertThat(selections.insertIfAbsent(submissionId, 2, second.getId(), Instant.now())).isZero();
        entityManager.clear();

        assertThat(selections.findBySubmissionIds(List.of(submissionId)))
                .singleElement()
                .satisfies(selection -> {
                    assertThat(selection.getId().getMediaIndex()).isEqualTo(2);
                    assertThat(selection.getSelectedBy().getDisplayName()).isEqualTo("Premier");
                    assertThat(selection.getSelectedAt()).isEqualTo(early);
                });
        assertThat(selections.deleteOne(submissionId, 2)).isEqualTo(1);
        assertThat(selections.deleteOne(submissionId, 2)).isZero();
    }

    @Test
    void anExportPartIsReadInItsFrozenOrder() {
        AppUser admin = user("export-sub", "Alex");
        AppUser author = user("export-author-sub", "Morgan");
        UUID exportId = UUID.randomUUID();
        exports.save(new MediaLibraryExport(exportId, admin, Instant.now(), 3, 600L, 2));
        // Une vraie soumission persistee : media_library_export_items porte elle aussi la
        // cle etrangere de V187 vers challenge_submissions.
        UUID submissionId = submission(author).getId();
        items.save(new MediaLibraryExportItem(new MediaExportItemKey(exportId, 1), 1, submissionId, 1, 200L, admin, Instant.now()));
        items.save(new MediaLibraryExportItem(new MediaExportItemKey(exportId, 0), 1, submissionId, 0, 100L, admin, Instant.now()));
        items.save(new MediaLibraryExportItem(new MediaExportItemKey(exportId, 2), 2, submissionId, 2, 300L, admin, Instant.now()));
        entityManager.flush();
        entityManager.clear();

        assertThat(items.findPart(exportId, 1))
                .extracting(MediaLibraryExportItem::getMediaIndex)
                .containsExactly(0, 1);
        assertThat(items.summarizeParts(exportId))
                .containsExactly(new MediaExportPartSummary(1, 2L, 300L), new MediaExportPartSummary(2, 1L, 300L));
        assertThat(exports.findAllNewestFirst()).extracting(MediaLibraryExport::getId).contains(exportId);
        assertThat(items.existsByIdExportIdAndPartNumber(exportId, 2)).isTrue();
        assertThat(items.existsByIdExportIdAndPartNumber(exportId, 3)).isFalse();
        assertThat(items.existsByIdExportIdAndPartNumber(UUID.randomUUID(), 1)).isFalse();
    }

    private AppUser user(String subject, String name) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName(name);
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
