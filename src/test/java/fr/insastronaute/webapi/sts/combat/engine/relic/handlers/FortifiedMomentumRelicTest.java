package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "Élan victorieux — La première fois du combat que vous brisez l'Armure d'un ennemi, gagnez 1 de
 * Force et 1 de Dextérité."
 *
 * <p>This test used to assert {@code shouldClearArmor(...) == false} and nothing else, which is
 * what the relic had been ported as: armour that never falls, for ever, unconditionally. That is a
 * different relic. It passed for as long as it did because the gate it asserted was dead code --
 * the status tick cleared the armour again whatever it answered -- so the wrong port had no
 * visible effect to give it away.
 */
class FortifiedMomentumRelicTest {

    private final FortifiedMomentumRelic relic = new FortifiedMomentumRelic();

    @Test
    void grantsStrengthAndDexterityTheFirstTimeItBreaksAnEnemysArmour() {
        CombatantState after = relic.onTargetArmorBroken(combatant("hero"), combatant("enemy"));

        assertThat(after.statuses()).containsExactlyInAnyOrder(
                new StatusInstance("Strength", 1, -1, "", 0),
                new StatusInstance("Dexterity", 1, -1, "", 0));
    }

    @Test
    void grantsNothingTheSecondTime() {
        CombatantState once = relic.onTargetArmorBroken(combatant("hero"), combatant("enemy"));
        CombatantState twice = relic.onTargetArmorBroken(once, combatant("enemy"));

        assertThat(twice.statuses())
                .as("\"la première fois du combat\", and the count lives in the relic's own memory")
                .isEqualTo(once.statuses());
    }

    /// The memory is the combatant's, not the handler's: one handler instance serves every combat
    /// on the server, so a field would have fired once per process rather than once per combat.
    @Test
    void remembersOnTheCombatantRatherThanOnItself()
    {
        CombatantState first = relic.onTargetArmorBroken(combatant("hero"), combatant("enemy"));
        CombatantState somebodyElsesCombat =
                relic.onTargetArmorBroken(combatant("hero"), combatant("enemy"));

        assertThat(first.recall("FortifiedMomentumRelic", "triggered")).isEqualTo(1);
        assertThat(somebodyElsesCombat.statuses())
                .as("a fresh combatant has never triggered it, whatever another combat did")
                .hasSize(2);
    }

    @Test
    void doesNotFireOnItsOwnArmour() {
        CombatantState after = relic.onTargetArmorBroken(combatant("hero"), combatant("hero"));

        assertThat(after.statuses()).isEmpty();
    }

    /// And it no longer keeps armour, which was never its rule.
    @Test
    void doesNotKeepArmourAcrossTurns() {
        assertThat(relic.shouldClearArmor(null, combatant("hero"), null)).isTrue();
    }

    private static CombatantState combatant(String id) {
        return new CombatantState(
                id, id + "-team", ControllerType.HUMAN, 50, 50, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}
