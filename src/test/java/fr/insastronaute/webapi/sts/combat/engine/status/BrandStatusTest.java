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
 * Brand raises the damage its holder takes by a percentage per debuff on them.
 *
 * <p>Unity leaves Brand itself out of that count ({@code BrandStatus.cs:22}: {@code se != this}).
 * Counting it made every branded target take one debuff's worth of extra damage, and a target
 * carrying nothing but Brand took more for carrying nothing.
 */
class BrandStatusTest {

    @Test
    void countsTheOtherDebuffsButNotItself() {
        int damage = damageAgainst(
                new StatusInstance("Brand", 50, 2, null, 0),
                new StatusInstance("Burn", 0, 2, null, 1));

        assertThat(damage).isEqualTo(15);
    }

    @Test
    void addsNothingToATargetCarryingBrandAlone() {
        int damage = damageAgainst(new StatusInstance("Brand", 50, 2, null, 0));

        assertThat(damage).isEqualTo(10);
    }

    private static int damageAgainst(StatusInstance... statuses) {
        CombatantState attacker = combatant("attacker", "players");
        CombatantState target = new CombatantState(
                "target",
                "enemies",
                ControllerType.AI,
                30,
                30,
                0,
                0,
                0,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(statuses),
                List.of(),
                null,
                0);
        return BattleCalculator.getModifiedValue(
                10,
                StatType.DAMAGE,
                new EffectContext(
                        null, attacker, target, List.of(target), null, false, List.of()));
    }

    private static CombatantState combatant(String id, String team) {
        return new CombatantState(
                id,
                team,
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}
