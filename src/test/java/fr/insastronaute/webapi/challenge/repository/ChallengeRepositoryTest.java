package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.department.domain.Department;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La liste d'un joueur ne filtre plus sur les dates : un défi terminé ou pas encore
 * ouvert doit rester visible. C'est la possibilité de soumettre qui reste bornée,
 * et elle est vérifiée ailleurs, dans ChallengeService.validateChallengeActive.
 */
@DataJpaTest
// La base par défaut du slice ignore le type jsonb que déclare
// challenges.auto_challenge_parameters, et la table n'est alors pas créée.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ChallengeRepositoryTest {

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsChallengesWhateverTheirWindow() {
        Department department = department("Informatique", "ITI");

        Challenge closed = challenge("Terminé", null, Instant.now().minusSeconds(3600));
        Challenge notOpenedYet = challenge("À venir", Instant.now().plusSeconds(3600), null);
        Challenge open = challenge("Ouvert", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        Challenge undated = challenge("Sans dates", null, null);

        List<Challenge> found = challengeRepository.findChallengesForDepartment(department.getId());

        assertThat(found).extracting(Challenge::getId)
                .containsExactlyInAnyOrder(closed.getId(), notOpenedYet.getId(), open.getId(), undated.getId());

        // Contre-épreuve : la requête bornée, sur les mêmes données, écarte bien ce que
        // la précédente laisse passer. C'est elle qui sert au pourcentage d'avancement.
        assertThat(challengeRepository.findActiveChallengesForDepartment(department.getId()))
                .extracting(Challenge::getId)
                .containsExactlyInAnyOrder(open.getId(), undated.getId());
    }

    @Test
    void stillExcludesChallengesTargetingAnotherDepartment() {
        Department mine = department("Informatique", "ITI");
        Department other = department("Mécanique", "MECA");

        Challenge targeted = challenge("Pour les autres", null, null);
        targeted.setTargetDepartments(Set.of(other));
        entityManager.persist(targeted);
        entityManager.flush();

        Challenge global = challenge("Pour tout le monde", null, null);

        List<Challenge> found = challengeRepository.findChallengesForDepartment(mine.getId());

        assertThat(found).extracting(Challenge::getId).containsExactly(global.getId());
    }

    private Department department(String name, String shortName) {
        Department department = new Department();
        department.setName(name);
        department.setShortName(shortName);
        entityManager.persist(department);
        entityManager.flush();

        return department;
    }

    private Challenge challenge(String title, Instant startDate, Instant endDate) {
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setPointsReward(10);
        challenge.setValidationMode("ADMIN");
        challenge.setStartDate(startDate);
        challenge.setEndDate(endDate);
        entityManager.persist(challenge);
        entityManager.flush();

        return challenge;
    }
}
