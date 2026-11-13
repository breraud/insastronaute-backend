package fr.insastronaute.webapi.user;

import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.service.CollectionPointsCalculator;
import fr.insastronaute.webapi.card.repository.CardCollectionSettingsRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.user.controller.UserController;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.dto.UserProfileDto;
import fr.insastronaute.webapi.user.dto.UserProfileUpdateDto;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void updateProfilePersistsGenderAndReturnsUpdatedProfile() {
        UserSynchronizationService synchronizationService = mock(UserSynchronizationService.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OidcUser oidcUser = mock(OidcUser.class);

        UUID departmentId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setDisplayName("Test User");
        user.setDepartment(departmentId);

        Department department = new Department();
        department.setShortName("ITI");

        when(synchronizationService.syncWithKeycloak(oidcUser)).thenReturn(user);
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        UserController controller = new UserController(synchronizationService, departmentRepository, userRepository,
                mock(CardCollectionRepository.class), new CollectionPointsCalculator(mock(CardCollectionSettingsRepository.class)),
                mock(GoofyCardService.class));

        UserProfileDto response = controller.updateProfile(oidcUser, new UserProfileUpdateDto(Gender.FEMALE));

        assertThat(response.gender()).isEqualTo(Gender.FEMALE);
        assertThat(response.department()).isEqualTo("ITI");
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        verify(userRepository).save(user);
    }

    /**
     * Le premier choix pose l'horodatage : c'est lui, et non la valeur du genre, qui
     * ferme la porte. Sans cette pose, la regle ne se declencherait jamais pour les
     * comptes anterieurs a l'accueil.
     */
    @Test
    void theFirstChoiceStampsTheMomentItWasMade() {
        Fixture fixture = new Fixture();

        fixture.controller.updateProfile(fixture.oidcUser, new UserProfileUpdateDto(Gender.MALE));

        assertThat(fixture.user.getOnboardingGenderChosenAt()).isNotNull();
        assertThat(fixture.user.getGender()).isEqualTo(Gender.MALE);
    }

    /**
     * Le coeur de la correction : un moderateur ne doit pas pouvoir basculer son
     * genre pour s'ouvrir les preuves sensibles d'une cible.
     */
    @Test
    void aSecondChoiceIsRefusedOnceTheGenderHasBeenStamped() {
        Fixture fixture = new Fixture();
        fixture.user.setGender(Gender.MALE);
        fixture.user.setOnboardingGenderChosenAt(Instant.parse("2026-08-01T10:00:00Z"));

        assertThatThrownBy(() -> fixture.controller.updateProfile(
                fixture.oidcUser, new UserProfileUpdateDto(Gender.FEMALE)))
                .isInstanceOf(CodedBadRequestException.class)
                .extracting(thrown -> ((CodedBadRequestException) thrown).getCode())
                .isEqualTo("GENDER_ALREADY_CHOSEN");

        assertThat(fixture.user.getGender()).isEqualTo(Gender.MALE);
        verify(fixture.userRepository, never()).save(any(AppUser.class));
    }

    /**
     * Un front qui repousse le profil entier renvoie le genre inchange : ce cas doit
     * passer, sinon la regle casse une requete qui ne touche pas au champ.
     */
    @Test
    void resendingTheSameGenderIsAcceptedRatherThanRefused() {
        Fixture fixture = new Fixture();
        Instant chosenAt = Instant.parse("2026-08-01T10:00:00Z");
        fixture.user.setGender(Gender.FEMALE);
        fixture.user.setOnboardingGenderChosenAt(chosenAt);

        UserProfileDto response = fixture.controller.updateProfile(
                fixture.oidcUser, new UserProfileUpdateDto(Gender.FEMALE));

        assertThat(response.gender()).isEqualTo(Gender.FEMALE);
        assertThat(fixture.user.getOnboardingGenderChosenAt())
                .as("un renvoi a l'identique ne doit pas repousser la date du choix")
                .isEqualTo(chosenAt);
    }

    /**
     * Le montage partage des trois cas ci-dessus. Le departement est laisse nul :
     * ces tests portent sur la regle du genre, pas sur le rendu du profil.
     */
    private static final class Fixture {
        private final UserSynchronizationService synchronizationService = mock(UserSynchronizationService.class);
        private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        private final UserRepository userRepository = mock(UserRepository.class);
        private final OidcUser oidcUser = mock(OidcUser.class);
        private final AppUser user = new AppUser();
        private final UserController controller;

        private Fixture() {
            user.setDisplayName("Test User");
            when(synchronizationService.syncWithKeycloak(oidcUser)).thenReturn(user);
            when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
            controller = new UserController(synchronizationService, departmentRepository, userRepository,
                    mock(CardCollectionRepository.class),
                    new CollectionPointsCalculator(mock(CardCollectionSettingsRepository.class)),
                    mock(GoofyCardService.class));
        }
    }
}
