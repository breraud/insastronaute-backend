package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a status that modifies "every effect" is allowed to reach.
 *
 * <p>Freeze and Full Break are declared over every stat at once. Unity guards exactly that case
 * with {@code StatTypeChecker.IsValid} ({@code StatTypeChecker.cs:5}), which keeps them off the
 * cost of a card, the delay of a turn and the replay count. Without the guard, Freeze halves the
 * price of every card: the debuff turns into the strongest buff in the game.
 */
class GenericStatusModifierScopeTest {

    @Test
    void halvesTheDamageOfAFrozenCaster() {
        assertThat(modified(10, StatType.DAMAGE)).isEqualTo(5);
    }

    @Test
    void leavesTheCostOfACardAloneWhenTheCasterIsFrozen() {
        assertThat(modified(2, StatType.COST)).isEqualTo(2);
    }

    @Test
    void leavesTheTurnDelayAloneWhenTheCasterIsFrozen() {
        assertThat(modified(100, StatType.TURN_DELAY)).isEqualTo(100);
    }

    @Test
    void leavesTheReplayCountAloneWhenTheCasterIsFrozen() {
        assertThat(modified(1, StatType.REPLAY_COUNT)).isEqualTo(1);
    }

    private static int modified(int baseValue, StatType type) {
        CombatantState frozen = frozenCaster();
        return BattleCalculator.getModifiedValue(
                baseValue,
                type,
                new EffectContext(null, frozen, null, List.of(), null, false, List.of()));
    }

    private static CombatantState frozenCaster() {
        return new CombatantState(
                "hero",
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Freeze", 0, 2, null, 0)),
                List.of(),
                null,
                0);
    }
}
