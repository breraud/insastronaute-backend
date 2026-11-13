package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que compte « valider X défis du jour ».
 *
 * <p>Un défi du jour l'est autant par sa catégorie principale que par une
 * catégorie secondaire. Un défi supprimé ne compte plus : ses points tombent à
 * la suppression, sa validation aussi.</p>
 */
@DataJpaTest
// La base par defaut du slice ignore le type jsonb que declare
// challenges.auto_challenge_parameters, et la table n'est alors pas creee.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ValidatedDailyChallengesQueryTest {

    @Autowired
    private ChallengeSubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void countsDistinctValidatedDailiesWhateverTheCategorySlotOrTheRole() {
        AppUser player = user("daily-joueur");
        AppUser author = user("daily-auteur");

        Challenge primary = challenge("DAILY", Set.of());
        submission(primary, player, Set.of(), "VALIDATED");
        // Deux preuves validées sur le même défi du jour restent un défi.
        submission(primary, player, Set.of(), "VALIDATED");
        submission(challenge("PHOTO", Set.of("DAILY")), player, Set.of(), "VALIDATED");
        // Porté par un coéquipier : le joueur l'a fait aussi.
        submission(challenge("DAILY", Set.of()), author, Set.of(player), "VALIDATED");

        submission(challenge("PHOTO", Set.of("GROUPE")), player, Set.of(), "VALIDATED");
        submission(challenge("DAILY", Set.of()), player, Set.of(), "PENDING");
        Challenge deleted = challenge("DAILY", Set.of());
        deleted.setDeletedAt(Instant.parse("2026-09-20T10:00:00Z"));
        submission(deleted, player, Set.of(), "VALIDATED");

        entityManager.flush();
        entityManager.clear();

        assertThat(submissionRepository.countValidatedDailyChallengesForUser(player.getId())).isEqualTo(3);
        assertThat(submissionRepository.countValidatedDailyChallengesForUser(author.getId())).isEqualTo(1);
    }

    @Test
    void candidatesReachTheThresholdOnDailiesAloneAndHaveNotAlreadyGotTheChallenge() {
        Challenge target = challenge("DAILY", Set.of());
        Challenge firstDaily = challenge("DAILY", Set.of());
        Challenge secondDaily = challenge("DEFAULT", Set.of("DAILY"));

        AppUser qualified = user("daily-qualifie");
        submission(firstDaily, qualified, Set.of(), "VALIDATED");
        submission(secondDaily, qualified, Set.of(), "VALIDATED");

        AppUser alreadyAwarded = user("daily-deja-servi");
        submission(firstDaily, alreadyAwarded, Set.of(), "VALIDATED");
        submission(secondDaily, alreadyAwarded, Set.of(), "VALIDATED");
        submission(target, alreadyAwarded, Set.of(), "VALIDATED");

        // Beaucoup de défis, mais un seul du jour.
        AppUser busyElsewhere = user("daily-ailleurs");
        submission(firstDaily, busyElsewhere, Set.of(), "VALIDATED");
        submission(challenge("DEFAULT", Set.of()), busyElsewhere, Set.of(), "VALIDATED");
        submission(challenge("DEFAULT", Set.of()), busyElsewhere, Set.of(), "VALIDATED");

        entityManager.flush();
        entityManager.clear();

        assertThat(submissionRepository.findUsersWithValidatedDailyChallenges(
                2, target.getId(), null, PageRequest.of(0, 10)))
                .containsExactly(qualified.getId());
    }

    @Test
    void candidatesAdvanceOnAStrictlyIncreasingCursor() {
        Challenge target = challenge("DAILY", Set.of());
        Challenge daily = challenge("DAILY", Set.of());
        List<AppUser> players = Stream.of("daily-a", "daily-b", "daily-c").map(this::user).toList();
        players.forEach(player -> submission(daily, player, Set.of(), "VALIDATED"));

        entityManager.flush();
        entityManager.clear();

        // L'ordre de la base : octets non signés, soit l'ordre du texte. UUID.compareTo
        // compare des long signés et range ea77… avant 3fe7….
        List<UUID> ordered = players.stream().map(AppUser::getId).sorted(Comparator.comparing(UUID::toString)).toList();
        assertThat(submissionRepository.findUsersWithValidatedDailyChallenges(
                1, target.getId(), null, PageRequest.of(0, 2)))
                .containsExactly(ordered.get(0), ordered.get(1));
        assertThat(submissionRepository.findUsersWithValidatedDailyChallenges(
                1, target.getId(), ordered.get(1), PageRequest.of(0, 2)))
                .containsExactly(ordered.get(2));
    }

    private Challenge challenge(String category, Set<String> extraCategories) {
        Challenge challenge = new Challenge();
        challenge.setTitle("Défi " + category);
        challenge.setPointsReward(10);
        challenge.setValidationMode("ADMIN");
        challenge.setCategory(category);
        challenge.setExtraCategories(new java.util.HashSet<>(extraCategories));
        entityManager.persist(challenge);
        return challenge;
    }

    private AppUser user(String subject) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName("Joueur");
        entityManager.persist(user);
        return user;
    }

    private void submission(Challenge challenge, AppUser author, Set<AppUser> participants, String status) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(participants);
        submission.setStatus(status);
        submission.setProofType("TEXT");
        submission.setProofData("");
        entityManager.persist(submission);
    }
}
