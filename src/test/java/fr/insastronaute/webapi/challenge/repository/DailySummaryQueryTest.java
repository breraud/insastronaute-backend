package fr.insastronaute.webapi.challenge.repository;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que doit un joueur pour le bilan « tous les défis du jour » d'une période.
 *
 * <p>Sont dus les défis du jour de la période qui le visent : DAILY en principale
 * ou en secondaire, non supprimés, et qui ne sont pas eux-mêmes des bilans — un
 * bilan classé DAILY s'exigerait sinon lui-même, et deux bilans du même jour
 * s'exigeraient l'un l'autre.</p>
 */
@DataJpaTest
// La base par defaut du slice ignore le type jsonb que declare
// challenges.auto_challenge_parameters, et la table n'est alors pas creee.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DailySummaryQueryTest {

    private static final String SUMMARY_TYPE = "ALL_DAILY_CHALLENGES_COMPLETED";
    // Le 27 septembre 2026, heure de Paris (UTC+2).
    private static final Instant DAY_START = Instant.parse("2026-09-26T22:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-09-27T22:00:00Z");
    private static final Instant DAY_LAST_MINUTE = Instant.parse("2026-09-27T21:59:00Z");

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private EntityManager entityManager;

    private Department targetedDepartment;
    private Challenge summary;
    private Challenge primaryDaily;
    private Challenge secondaryDaily;
    private Challenge targetedDaily;

    @BeforeEach
    void dailiesOfTheDay() {
        targetedDepartment = department("GM");

        primaryDaily = challenge("DAILY", Set.of(), DAY_START, DAY_LAST_MINUTE);
        secondaryDaily = challenge("PHOTO", Set.of("DAILY"), DAY_START, DAY_LAST_MINUTE);
        targetedDaily = challenge("DAILY", Set.of(), DAY_START, DAY_LAST_MINUTE);
        targetedDaily.setTargetDepartments(new HashSet<>(Set.of(targetedDepartment)));

        summary = challenge("DAILY", Set.of(), DAY_START, DAY_LAST_MINUTE);
        summary.setValidationMode("AUTO");
        summary.setAutoChallengeType(SUMMARY_TYPE);
        summary.setAutoChallengeParameters(JsonNodeFactory.instance.objectNode()
                .put("startDate", "2026-09-27").put("endDate", "2026-09-27"));

        challenge("DAILY", Set.of(), DAY_START, DAY_LAST_MINUTE)
                .setDeletedAt(Instant.parse("2026-09-27T08:00:00Z"));
        challenge("DAILY", Set.of(), DAY_END, DAY_END.plusSeconds(86_340));
        challenge("PHOTO", Set.of(), DAY_START, DAY_LAST_MINUTE);
    }

    @Test
    void dueDailiesLeaveOutSummariesDeletedOnesOtherDaysAndOtherDepartments() {
        AppUser player = user("bilan-joueur", null);
        AppUser targeted = user("bilan-cible", targetedDepartment);
        flush();

        assertThat(countDue(player)).isEqualTo(2);
        assertThat(countDue(targeted)).isEqualTo(3);
    }

    @Test
    void completedCountsDailiesDoneAloneOrInAGroup() {
        AppUser player = user("bilan-joueur", null);
        AppUser teammate = user("bilan-coequipier", null);
        submission(primaryDaily, player, Set.of(), "VALIDATED");
        submission(secondaryDaily, teammate, Set.of(player), "VALIDATED");
        flush();

        assertThat(countCompleted(player)).isEqualTo(2);
        assertThat(countCompleted(teammate)).isEqualTo(1);
    }

    @Test
    void candidatesHaveDoneEveryDailyDueToThem() {
        AppUser complete = user("bilan-complet", null);
        submission(primaryDaily, complete, Set.of(), "VALIDATED");
        submission(secondaryDaily, complete, Set.of(), "VALIDATED");

        AppUser partial = user("bilan-partiel", null);
        submission(primaryDaily, partial, Set.of(), "VALIDATED");
        submission(secondaryDaily, partial, Set.of(), "PENDING");

        // Il lui en faut un de plus : celui de son département.
        AppUser targeted = user("bilan-cible", targetedDepartment);
        submission(primaryDaily, targeted, Set.of(), "VALIDATED");
        submission(secondaryDaily, targeted, Set.of(), "VALIDATED");

        AppUser alreadyAwarded = user("bilan-deja-servi", null);
        submission(primaryDaily, alreadyAwarded, Set.of(), "VALIDATED");
        submission(secondaryDaily, alreadyAwarded, Set.of(), "VALIDATED");
        submission(summary, alreadyAwarded, Set.of(), "VALIDATED");
        flush();

        assertThat(candidates(DAY_START, DAY_END)).containsExactly(complete.getId());
    }

    @Test
    void aPeriodWithoutDailiesHasNoCandidate() {
        user("bilan-oisif", null);
        flush();

        Instant nextWeek = DAY_START.plusSeconds(7 * 86_400);
        assertThat(candidates(nextWeek, nextWeek.plusSeconds(86_400))).isEmpty();
    }

    private long countDue(AppUser player) {
        return challengeRepository.countDailyChallengesDue(player.getId(), DAY_START, DAY_END, SUMMARY_TYPE);
    }

    private long countCompleted(AppUser player) {
        return challengeRepository.countDailyChallengesCompleted(player.getId(), DAY_START, DAY_END, SUMMARY_TYPE);
    }

    private List<UUID> candidates(Instant startInclusive, Instant endExclusive) {
        return challengeRepository.findUsersCompletingAllDailyChallenges(
                startInclusive, endExclusive, SUMMARY_TYPE, summary.getId(), null, Pageable.ofSize(10));
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Challenge challenge(String category, Set<String> extraCategories, Instant start, Instant end) {
        Challenge challenge = new Challenge();
        challenge.setTitle("Défi " + category);
        challenge.setPointsReward(10);
        challenge.setValidationMode("ADMIN");
        challenge.setCategory(category);
        challenge.setExtraCategories(new HashSet<>(extraCategories));
        challenge.setStartDate(start);
        challenge.setEndDate(end);
        entityManager.persist(challenge);
        return challenge;
    }

    private Department department(String shortName) {
        Department department = new Department();
        department.setName("Département " + shortName);
        department.setShortName(shortName);
        entityManager.persist(department);
        return department;
    }

    private AppUser user(String subject, Department department) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName("Joueur");
        if (department != null) {
            user.setDepartment(department);
        }
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
