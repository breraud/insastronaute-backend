package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A status that draws cards must draw them from the combat's own random stream.
 *
 * <p>The status tick used to build a throwaway generator seeded on the revision and drop the
 * advanced state on the floor. Two consequences, both breaking the rule that the random state is
 * part of the combat state: the reshuffle a status caused was decided by the revision number
 * rather than the stream, and the stream never moved, so the next shuffle repeated it.
 */
class StatusEngineRandomTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Test
    void carriesTheAdvancedRandomStateBackOutWhenAStatusReshufflesToDraw() {
        RandomState before = new RandomState(1234);

        StatusEngine.StatusTickResult result =
                StatusEngine.processTurnStart(producer(), 9L, COMBAT_ID, before);

        assertThat(result.random()).isNotEqualTo(before);
    }

    @Test
    void leavesTheRandomStateAloneWhenNothingHadToBeShuffled() {
        RandomState before = new RandomState(1234);

        StatusEngine.StatusTickResult result =
                StatusEngine.processTurnStart(plainCombatant(), 9L, COMBAT_ID, before);

        assertThat(result.random()).isEqualTo(before);
    }

    @Test
    void drawsWhatTheGivenStreamSaysRatherThanWhatTheRevisionSays() {
        StatusEngine.StatusTickResult first =
                StatusEngine.processTurnStart(producer(), 9L, COMBAT_ID, new RandomState(1));
        StatusEngine.StatusTickResult second =
                StatusEngine.processTurnStart(producer(), 9L, COMBAT_ID, new RandomState(2));

        assertThat(first.combatant().piles().draw())
                .isNotEqualTo(second.combatant().piles().draw());
    }

    private static CombatantState producer() {
        return combatant(
                new CardPiles(
                        List.of(),
                        List.of(),
                        List.of(
                                new CardInstance("a", "any"),
                                new CardInstance("b", "any"),
                                new CardInstance("c", "any"),
                                new CardInstance("d", "any"),
                                new CardInstance("e", "any"),
                                new CardInstance("f", "any")),
                        List.of()),
                new StatusInstance("Production", 1, 3, null, 0));
    }

    private static CombatantState plainCombatant() {
        return combatant(
                new CardPiles(
                        List.of(new CardInstance("a", "any")), List.of(), List.of(), List.of()));
    }

    private static CombatantState combatant(CardPiles piles, StatusInstance... statuses) {
        return new CombatantState(
                "hero",
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                piles,
                List.of(statuses),
                List.of(),
                null,
                0);
    }
}
