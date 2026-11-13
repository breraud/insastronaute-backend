package fr.insastronaute.webapi.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingFieldsTest {

    @Test
    void aFreshUserHasNotStartedTheirOnboarding() {
        AppUser user = new AppUser();

        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
        assertThat(user.getOnboardingGenderChosenAt()).isNull();
        assertThat(user.getOnboardingSubmittedAt()).isNull();
        assertThat(user.getOnboardingDecidedAt()).isNull();
        assertThat(user.getOnboardingDecidedBy()).isNull();
        assertThat(user.getOnboardingRejectionReason()).isNull();
    }

    @Test
    void theStatusNeverBecomesNull() {
        AppUser user = new AppUser();

        user.setOnboardingStatus(null);

        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
    }

    @Test
    void aDecisionRecordsWhoAndWhen() {
        AppUser user = new AppUser();
        AppUser decider = new AppUser();
        Instant when = Instant.parse("2026-08-28T10:00:00Z");

        user.setOnboardingStatus(OnboardingStatus.REJECTED);
        user.setOnboardingRejectionReason("Compte de test");
        user.setOnboardingDecidedAt(when);
        user.setOnboardingDecidedBy(decider);

        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.REJECTED);
        assertThat(user.getOnboardingRejectionReason()).isEqualTo("Compte de test");
        assertThat(user.getOnboardingDecidedAt()).isEqualTo(when);
        assertThat(user.getOnboardingDecidedBy()).isSameAs(decider);
    }
}
