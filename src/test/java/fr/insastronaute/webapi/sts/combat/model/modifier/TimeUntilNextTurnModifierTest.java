package fr.insastronaute.webapi.sts.combat.model.modifier;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeUntilNextTurnModifierTest {

    /// A corpse at the head of the timeline has been a legal state since the engine
    /// stopped handing turns to the dead. It sits at a tick no living entry can undercut,
    /// so counting from it made the wait look LONGER than it is, and the card hit for more
    /// than it should. The numbers below say it plainly: 300 before, 200 after. Correcting
    /// this lowers the damage of every card scaled this way -- it is a nerf, not a buff.
    ///
    /// combat() is left null here on purpose: it mirrors DamageHandler, ArmorHandler and
    /// DispelDebuffsIntoDamageHandler, which is where this modifier is actually authored
    /// against (damage, armor) and which never hold a CombatState, only the bare
    /// combatants() list they were handed -- proving the fix works off that list, not off
    /// combat().
    @Test
    void countsTheWaitFromTheNextLivingEntryRatherThanTheFirstOne() {
        // timeline : mort au tic 0, vivant au tic 100, cible au tic 300.
        // Attendu : l'attente vaut 300 - 100 = 200, et non 300 - 0 = 300.
        CombatantState corpse = combatant("corpse", "players", 0);
        CombatantState hero = combatant("hero", "players", 10);
        CombatantState target = combatant("enemy", "enemies", 10);
        List<CombatantState> combatants = List.of(corpse, hero, target);

        List<TimelineEntry> timeline = List.of(
                new TimelineEntry("corpse", 0, 0),
                new TimelineEntry("hero", 100, 1),
                new TimelineEntry("enemy", 300, 2));

        EffectContext ctx = new EffectContext(
                null, hero, target, List.of(target), null, false, List.of(), timeline, combatants);

        StatModifier modifier = new TimeUntilNextTurnModifier(StatType.DAMAGE, 2);

        // Old value (measured from the corpse at tick 0): 10 + 2 * (300 - 0) = 610.
        // New value (measured from the next living entry, hero at tick 100):
        // 10 + 2 * (300 - 100) = 410.
        assertThat(modifier.modify(10, ctx)).isEqualTo(410);
    }

    private static CombatantState combatant(String id, String teamId, int hp) {
        return new CombatantState(
                id, teamId, ControllerType.HUMAN, 10, hp, 0, 3, 3, 1, emptyPiles());
    }

    private static CardPiles emptyPiles() {
        return new CardPiles(List.of(), List.of(), List.of(), List.of());
    }
}
