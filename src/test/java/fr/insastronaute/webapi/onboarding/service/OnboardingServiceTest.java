package fr.insastronaute.webapi.onboarding.service;

import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.onboarding.dto.OnboardingStateDto;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;

    private OnboardingService service;

    private static AppUser player() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setDisplayName("Alice Martin");
        user.setEmail("alice@example.test");
        return user;
    }

    @BeforeEach
    void setUp() {
        service = new OnboardingService(userRepository, departmentRepository);
    }

    @Test
    void choosingAGenderStampsTheChoiceWithoutAdvancingTheState() {
        AppUser user = player();

        service.chooseGender(user, Gender.FEMALE);

        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getOnboardingGenderChosenAt()).isNotNull();
        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
        verify(userRepository).save(user);
    }

    /**
     * L'etat n'avance pas au choix du genre precisement pour que le joueur puisse
     * se reprendre avant de soumettre. C'est la raison affichee de ce choix de
     * conception, et rien ne la verifiait.
     */
    @Test
    void theGenderCanStillBeChangedUntilTheOnboardingIsSubmitted() {
        AppUser user = player();

        service.chooseGender(user, Gender.MALE);
        service.chooseGender(user, Gender.FEMALE);

        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);

        service.submit(user);
        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.PENDING);
    }

    /**
     * Le pendant du cas vrai : sans lui, genderChosen pourrait renvoyer true en
     * dur sans qu'aucun test ne proteste, et le front laisserait soumettre une
     * inscription que le serveur refuserait.
     */
    @Test
    void theStateReportsAnUnchosenGenderAsUnchosen() {
        AppUser user = player();

        OnboardingStateDto state = service.state(user);

        assertThat(state.genderChosen()).isFalse();
        assertThat(state.rejectionReason()).isNull();
    }

    @Test
    void aDeliberateNeutralCountsAsAChoice() {
        AppUser user = player();

        service.chooseGender(user, Gender.NEUTRAL);
        service.submit(user);

        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.PENDING);
    }

    @Test
    void submittingWithoutChoosingIsRefused() {
        AppUser user = player();

        assertThatThrownBy(() -> service.submit(user))
                .isInstanceOf(CodedBadRequestException.class)
                .satisfies(thrown -> assertThat(((CodedBadRequestException) thrown).getCode())
                        .isEqualTo("ONBOARDING_GENDER_REQUIRED"));

        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
    }

    @Test
    void submittingTwiceIsRefused() {
        AppUser user = player();
        service.chooseGender(user, Gender.MALE);
        service.submit(user);

        assertThatThrownBy(() -> service.submit(user))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode())
                        .isEqualTo("ONBOARDING_ALREADY_SUBMITTED"));
    }

    @Test
    void aValidatedPlayerCannotReopenTheirOnboardingByChangingGender() {
        AppUser user = player();
        user.setOnboardingStatus(OnboardingStatus.VALIDATED);

        assertThatThrownBy(() -> service.chooseGender(user, Gender.MALE))
                .isInstanceOf(CodedConflictException.class);

        verify(userRepository, never()).save(any());
    }

    /**
     * Un compte refuse est bien refuse, mais pas avec le message d'un compte en
     * attente : lui dire que son inscription a deja ete envoyee l'enverrait attendre
     * une decision qui est deja tombee, alors que la suite pour lui est humaine.
     */
    @Test
    void aRejectedPlayerIsToldTheyWereRefusedRatherThanThatTheyAreWaiting() {
        AppUser user = player();
        user.setOnboardingStatus(OnboardingStatus.REJECTED);

        assertThatThrownBy(() -> service.chooseGender(user, Gender.MALE))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode())
                        .isEqualTo("ONBOARDING_REJECTED"));

        verify(userRepository, never()).save(any());
    }

    /**
     * Le message d'attente reste celui des comptes qui attendent vraiment. Sans ce
     * second cas, on pourrait remplacer les deux branches par la nouvelle sans que
     * rien ne proteste.
     */
    @Test
    void aPendingPlayerKeepsTheWaitingMessage() {
        AppUser user = player();
        user.setOnboardingStatus(OnboardingStatus.PENDING);

        assertThatThrownBy(() -> service.chooseGender(user, Gender.MALE))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode())
                        .isEqualTo("ONBOARDING_ALREADY_SUBMITTED"));
    }

    @Test
    void theStateCarriesTheRecapAndTheRejectionReason() {
        AppUser user = player();
        // L'etat est pose a la main : chooseGender refuserait sur un compte REJECTED,
        // et ce cas verifie la lecture, pas la transition.
        user.setGender(Gender.FEMALE);
        user.setOnboardingGenderChosenAt(java.time.Instant.parse("2026-08-28T09:00:00Z"));
        user.setOnboardingStatus(OnboardingStatus.REJECTED);
        user.setOnboardingRejectionReason("Compte de test");

        OnboardingStateDto state = service.state(user);

        assertThat(state.fullName()).isEqualTo("Alice Martin");
        assertThat(state.email()).isEqualTo("alice@example.test");
        assertThat(state.gender()).isEqualTo(Gender.FEMALE);
        assertThat(state.genderChosen()).isTrue();
        assertThat(state.rejectionReason()).isEqualTo("Compte de test");
    }
}
