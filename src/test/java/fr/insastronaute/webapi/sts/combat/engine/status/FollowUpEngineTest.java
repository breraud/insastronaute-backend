package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowUpEngineTest {

    @Test
    void removesATimedCardFollowUpAfterItsFinalProc() {
        StatusInstance followUp = new StatusInstance("CardFollowUp", 1, 1, "follow-up", 0);

        FollowUpEngine.Advance first = FollowUpEngine.advance(
                "hero", List.of(followUp), FollowUpEngine.CARD_FOLLOW_UP,
                CardType.ATTACK, false);
        FollowUpEngine.Advance second = FollowUpEngine.advance(
                "hero", first.statuses(), FollowUpEngine.CARD_FOLLOW_UP,
                CardType.ATTACK, false);

        assertThat(first.triggers()).singleElement();
        assertThat(first.statuses()).isEmpty();
        assertThat(second.triggers()).isEmpty();
        assertThat(second.statuses()).isEmpty();
    }

    @Test
    void keepsATimedFollowUpVisibleUntilItProcs() {
        StatusInstance followUp = new StatusInstance("FieldTurnFollowUp", 2, 1, "follow-up", 0);

        FollowUpEngine.Advance first = FollowUpEngine.advanceFieldTurnFollowUp(
                "hero", List.of(followUp), FollowUpEngine.ANY_FIELD_TURN_END);

        assertThat(first.triggers()).isEmpty();
        assertThat(first.statuses()).singleElement().satisfies(status -> {
            assertThat(status.duration()).isEqualTo(1);
            assertThat(status.progress()).isEqualTo(1);
        });
    }
}