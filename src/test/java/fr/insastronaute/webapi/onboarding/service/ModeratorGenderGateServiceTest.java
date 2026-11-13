package fr.insastronaute.webapi.onboarding.service;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * C'est l'horodatage du choix qui repond, jamais la valeur du genre.
 *
 * <p>NEUTRAL est a la fois un choix legitime et le defaut de la colonne : lire la valeur
 * laisserait passer tous ceux qui n'ont jamais repondu, c'est-a-dire precisement ceux que ce
 * garde existe pour arreter.</p>
 */
@ExtendWith(MockitoExtension.class)
class ModeratorGenderGateServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private ModeratorGenderGateService service;

    private AppUser moderator(Gender gender, Instant chosenAt) {
        AppUser user = new AppUser();
        user.setKeycloakSub("sub");
        user.setGender(gender);
        user.setOnboardingGenderChosenAt(chosenAt);
        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void aChoiceThatWasMadeOpensTheDoor() {
        moderator(Gender.FEMALE, Instant.parse("2026-09-01T10:00:00Z"));

        assertThat(service.hasChosenGender("sub")).isTrue();
    }

    /** Le cas qui motive tout : neutre par defaut, sans que personne l'ait dit. */
    @Test
    void theDefaultNeutralIsNotAChoice() {
        moderator(Gender.NEUTRAL, null);

        assertThat(service.hasChosenGender("sub")).isFalse();
    }

    /** Neutre choisi vaut choix : c'est une reponse, pas une absence. */
    @Test
    void neutralChosenOnPurposeCounts() {
        moderator(Gender.NEUTRAL, Instant.parse("2026-09-01T10:00:00Z"));

        assertThat(service.hasChosenGender("sub")).isTrue();
    }

    /**
     * Un compte encore absent de la base n'a rien choisi.
     *
     * <p>Sa ligne naitra a sa prochaine lecture de session, ou la question lui sera posee :
     * le refuser ici est ce qui l'y amene.</p>
     */
    @Test
    void anAccountWithoutARowHasNotChosen() {
        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.empty());

        assertThat(service.hasChosenGender("sub")).isFalse();
    }

    @Test
    void anAbsentSubjectIsRefusedWithoutQueryingAnything() {
        assertThat(service.hasChosenGender(null)).isFalse();
        assertThat(service.hasChosenGender(" ")).isFalse();
    }
}
