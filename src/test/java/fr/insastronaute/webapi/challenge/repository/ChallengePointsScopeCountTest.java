package fr.insastronaute.webapi.challenge.repository;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que compte la portee d'un changement de points de defi.
 *
 * <p>recalculateChallengePoints recredite l'auteur de chaque soumission
 * validee et ses participants. Le nombre porte au registre doit etre celui de
 * ces joueurs, chacun une fois - un joueur credite par deux soumissions du
 * meme defi reste un joueur touche.</p>
 */
@DataJpaTest
// La base par defaut du slice ignore le type jsonb que declare
// challenges.auto_challenge_parameters, et la table n'est alors pas creee.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ChallengePointsScopeCountTest {

    @Autowired
    private ChallengeSubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void submittersAndParticipantsOfValidatedSubmissionsCountOnceEach() {
        Challenge challenge = challenge("Photo equipe");
        Challenge otherChallenge = challenge("Autre defi");

        AppUser author = user("sub-auteur");
        AppUser teammate = user("sub-coequipier");
        AppUser secondAuthor = user("sub-second-auteur");
        AppUser pending = user("sub-en-attente");
        AppUser elsewhere = user("sub-ailleurs");

        submission(challenge, author, Set.of(teammate), "VALIDATED");
        // Le meme joueur credite deux fois reste un joueur touche.
        submission(challenge, author, Set.of(), "VALIDATED");
        submission(challenge, secondAuthor, Set.of(teammate), "VALIDATED");
        // Une soumission non validee ne credite personne.
        submission(challenge, pending, Set.of(), "PENDING");
        // Un autre defi n'est pas la portee de ce geste.
        submission(otherChallenge, elsewhere, Set.of(), "VALIDATED");

        entityManager.flush();
        entityManager.clear();

        assertThat(submissionRepository.countPlayersCreditedByChallenge(challenge.getId())).isEqualTo(3);
        assertThat(submissionRepository.countPlayersCreditedByChallenge(otherChallenge.getId())).isEqualTo(1);
    }

    @Test
    void aChallengeNobodyValidatedTouchesNobody() {
        Challenge challenge = challenge("Defi desert");
        submission(challenge, user("sub-en-attente-seul"), Set.of(), "PENDING");

        entityManager.flush();
        entityManager.clear();

        assertThat(submissionRepository.countPlayersCreditedByChallenge(challenge.getId())).isZero();
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
