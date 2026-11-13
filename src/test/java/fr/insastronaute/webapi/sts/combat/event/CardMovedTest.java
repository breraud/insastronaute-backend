package fr.insastronaute.webapi.sts.combat.event;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardMovedTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000062");

    @Test
    void rejectsNullBlankAndNegativeComponents() {
        assertThatNullPointerException().isThrownBy(
                () -> event(null, 0, "hero", "card", "HAND", "DISCARD", 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> event(COMBAT_ID, -1, "hero", "card", "HAND", "DISCARD", 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> event(COMBAT_ID, 0, " ", "card", "HAND", "DISCARD", 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> event(COMBAT_ID, 0, "hero", " ", "HAND", "DISCARD", 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> event(COMBAT_ID, 0, "hero", "card", " ", "DISCARD", 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> event(COMBAT_ID, 0, "hero", "card", "HAND", " ", 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> event(COMBAT_ID, 0, "hero", "card", "HAND", "DISCARD", -1));
    }

    private static CardMoved event(
            UUID combatId,
            long revision,
            String combatantId,
            String cardInstanceId,
            String fromPile,
            String toPile,
            int destinationIndex
    ) {
        return new CardMoved(
                combatId,
                revision,
                combatantId,
                cardInstanceId,
                "frappe",
                fromPile,
                toPile,
                destinationIndex);
    }
}
