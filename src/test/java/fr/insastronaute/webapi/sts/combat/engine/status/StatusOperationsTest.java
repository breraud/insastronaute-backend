package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusOperationsTest {

    @Test
    void stacksATimedStatusByAddingItsTurnsAndKeepingTheStrongerValue() {
        List<StatusInstance> statuses = new ArrayList<>(List.of(status("Poison", 3, 2)));

        StatusOperations.insert(statuses, status("Poison", 5, 3));

        assertThat(statuses).singleElement().satisfies(status -> {
            assertThat(status.duration()).isEqualTo(5);
            assertThat(status.value()).isEqualTo(5);
        });
    }

    @Test
    void stacksAPermanentStatusByAddingItsValue() {
        List<StatusInstance> statuses = new ArrayList<>(List.of(status("Strength", 2, -1)));

        StatusOperations.insert(statuses, status("Strength", 3, -1));

        assertThat(statuses).singleElement()
                .extracting(StatusInstance::value)
                .isEqualTo(5);
    }

    @Test
    void keepsATimedStatusApartFromAPermanentOneOfTheSameKind() {
        List<StatusInstance> statuses = new ArrayList<>(List.of(status("Thorns", 2, -1)));

        StatusOperations.insert(statuses, status("Thorns", 1, 3));

        assertThat(statuses).hasSize(2);
    }

    @Test
    void neverLetsAStatusPassItsCeiling() {
        List<StatusInstance> statuses = new ArrayList<>(List.of(status("Crystallize", 2, -1)));

        StatusOperations.insert(statuses, status("Crystallize", 9, -1));

        assertThat(statuses).singleElement()
                .extracting(StatusInstance::value)
                .describedAs("Crystallize holds at most 3")
                .isEqualTo(3);
    }

    @Test
    void artifactEatsAnIncomingDebuffAndSpendsACharge() {
        StatusOperations.Application application = StatusOperations.apply(
                List.of(status("Artifact", 2, -1)), status("Poison", 0, 3), List.of());

        assertThat(application.applied()).isFalse();
        assertThat(application.statuses()).singleElement()
                .extracting(StatusInstance::value)
                .isEqualTo(1);
    }

    @Test
    void aResistantDebuffCostsArtifactTwoCharges() {
        StatusOperations.Application application = StatusOperations.apply(
                List.of(status("Artifact", 3, -1)), status("Brand", 1, 2), List.of());

        assertThat(application.applied()).isFalse();
        assertThat(application.statuses()).singleElement()
                .extracting(StatusInstance::value)
                .isEqualTo(1);
    }

    @Test
    void aSingleArtifactChargeCannotStopADebuffThatCostsTwo() {
        StatusOperations.Application application = StatusOperations.apply(
                List.of(status("Artifact", 1, -1)), status("Brand", 1, 2), List.of());

        assertThat(application.applied()).isTrue();
        assertThat(application.statuses())
                .extracting(StatusInstance::statusType)
                .containsExactly("Brand");
    }

    @Test
    void filterEatsAnIncomingBuffInsteadOfADebuff() {
        StatusOperations.Application blocked = StatusOperations.apply(
                List.of(status("Filter", 2, -1)), status("Regen", 0, 3), List.of());
        StatusOperations.Application through = StatusOperations.apply(
                List.of(status("Filter", 2, -1)), status("Poison", 0, 3), List.of());

        assertThat(blocked.applied()).isFalse();
        assertThat(through.applied()).isTrue();
    }

    @Test
    void unbiasedOnTheSourceIgnoresTheTargetResistance() {
        StatusOperations.Application application = StatusOperations.apply(
                List.of(status("Artifact", 2, -1)),
                status("Poison", 0, 3),
                List.of(status("Unbiased", 1, 3)));

        assertThat(application.applied()).isTrue();
        assertThat(application.statuses())
                .extracting(StatusInstance::statusType)
                .containsExactly("Artifact", "Poison");
    }

    @Test
    void removesAStatusWholeWhenNothingIsCarriedAway() {
        StatusOperations.Removal removal = StatusOperations.remove(status("Strength", 4, -1), 0);

        assertThat(removal.remaining()).isNull();
        assertThat(removal.removed().value()).isEqualTo(4);
    }

    @Test
    void splitsAStatusWhenOnlyPartOfItIsCarriedAway() {
        StatusOperations.Removal removal = StatusOperations.remove(status("Poison", 5, 4), 50);

        assertThat(removal.removed().value()).isEqualTo(3);
        assertThat(removal.removed().duration()).isEqualTo(2);
        assertThat(removal.remaining().value()).isEqualTo(2);
        assertThat(removal.remaining().duration()).isEqualTo(2);
    }

    @Test
    void carriesAPermanentStatusAwayWithoutGivingItADuration() {
        StatusOperations.Removal removal = StatusOperations.remove(status("Strength", 4, -1), 50);

        assertThat(removal.removed().duration()).isEqualTo(-1);
        assertThat(removal.remaining().duration()).isEqualTo(-1);
        assertThat(removal.remaining().value()).isEqualTo(2);
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, "", 0);
    }
}
