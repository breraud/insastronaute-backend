package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.modifier.PercentModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The order two modifiers of the same kind are applied in.
 *
 * <p>Both engines sort by kind — additive, then multiplicative, then override — and both sorts
 * are stable, so what a card's own modifier and a status do to the same blow is settled by the
 * order they were collected in. Unity collects the card first ({@code BattleCalculator.cs:9-31});
 * the engine collected it last. A card that hits 50% harder, thrown by a weakened attacker, came
 * out one point light.
 */
class BattleCalculatorOrderTest {

    @Test
    void raisesTheCardsOwnBonusBeforeTheAttackersWeaknessCutsIt() {
        StatModifier harder = new PercentModifier(StatType.DAMAGE, 50);

        int damage = BattleCalculator.getModifiedValue(
                10,
                StatType.DAMAGE,
                new EffectContext(
                        null, weakened(), null, List.of(), null, false, List.of(harder)));

        assertThat(damage).isEqualTo(11);
    }

    private static CombatantState weakened() {
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
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Weakness", 0, 2, null, 0)),
                List.of(),
                null,
                0);
    }
}
