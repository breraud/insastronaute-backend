package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.StatusRemoved;
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
 * Each status keeps its own timing across a turn.
 *
 * <p>Only Poison did anything before, at the wrong moment, and every status counted down on the
 * same schedule whether or not that was its own, which quietly changed how long every status in
 * the game lasted.
 */
class StatusEngineTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a9");

    @Test
    void poisonBitesAsTheTurnOpens() {
        CombatantState after = turnStart(combatant(30, 0, status("Poison", 0, 4)));

        assertThat(after.hp()).isEqualTo(26);
        assertThat(after.statuses()).singleElement()
                .extracting(StatusInstance::duration)
                .describedAs("and counts down as it bites")
                .isEqualTo(3);
    }

    @Test
    void burnBitesAsTheTurnCloses() {
        CombatantState opened = turnStart(combatant(30, 0, status("Burn", 0, 3)));
        assertThat(opened.hp())
                .describedAs("burn does nothing when the turn opens")
                .isEqualTo(30);

        CombatantState closed = turnEnd(opened);

        assertThat(closed.hp()).isEqualTo(27);
        assertThat(closed.statuses()).singleElement()
                .extracting(StatusInstance::duration)
                .isEqualTo(2);
    }

    @Test
    void damageReductionExpiresAsTheTurnOpens() {
        CombatantState opened = turnStart(combatant(30, 0,
                status("DamageReduction", 50, 1), status("AreaDmgReduction", 50, 1)));

        assertThat(opened.statuses()).isEmpty();
    }

    @Test
    void damageReductionDoesNotTickAgainWhenTheTurnCloses() {
        CombatantState opened = turnStart(combatant(30, 0,
                status("DamageReduction", 50, 2), status("AreaDmgReduction", 50, 2)));

        assertThat(turnEnd(opened).statuses())
                .extracting(StatusInstance::duration)
                .containsExactly(1, 1);
    }

    @Test
    void regenerationHealsAsTheTurnOpensWithoutPassingTheMaximum() {
        CombatantState after = turnStart(wounded(30, 28, status("Regen", 0, 5)));

        assertThat(after.hp()).isEqualTo(30);
    }

    @Test
    void shieldingArmoursBeforeTheTurnAndPlatingAfterIt() {
        CombatantState opened = turnStart(combatant(30, 0, status("Shielding", 0, 6)));
        assertThat(opened.armor()).isEqualTo(6);

        CombatantState closed = turnEnd(combatant(30, 0, status("Plating", 0, 4)));
        assertThat(closed.armor()).isEqualTo(4);
    }

    @Test
    void armourIsGoneWhenTheTurnOpensUnlessSomethingKeepsIt() {
        assertThat(turnStart(combatant(30, 9)).armor()).isZero();
        assertThat(turnStart(combatant(30, 9, status("ConserveArmor", 0, 2))).armor())
                .isEqualTo(9);
    }

    @Test
    void aStatusThatTurnsIntoAnotherLeavesTheOneItBecame() {
        CombatantState after = turnEnd(combatant(30, 0, status("Strengthen", 2, -1)));

        assertThat(after.statuses())
                .extracting(StatusInstance::statusType)
                .contains("Strength");
    }

    @Test
    void aPermanentStatusNeverRunsOut() {
        CombatantState after = turnEnd(turnStart(combatant(30, 0, status("Strength", 3, -1))));

        assertThat(after.statuses()).singleElement()
                .extracting(StatusInstance::value)
                .isEqualTo(3);
    }

    @Test
    void aSignedStatusWithNothingLeftOnItIsGone() {
        CombatantState after = turnEnd(combatant(30, 0, status("Strength", 0, -1)));

        assertThat(after.statuses()).isEmpty();
    }

    @Test
    void haltingDebuffsStopsThemCountingDown() {
        CombatantState after = turnStart(combatant(30, 0,
                status("Poison", 0, 3), status("DebuffHalt", 0, 2)));

        assertThat(after.statuses())
                .filteredOn(status -> status.statusType().equals("Poison"))
                .singleElement()
                .extracting(StatusInstance::duration)
                .describedAs("it still bites, it just does not run down")
                .isEqualTo(3);
        assertThat(after.hp()).isEqualTo(27);
    }

    @Test
    void reportsTheStatusesThatRanOut() {
        StatusEngine.StatusTickResult result = StatusEngine.processTurnStart(
                combatant(30, 0, status("Vuln", 0, 1)), 3, COMBAT_ID, new RandomState(5));

        assertThat(result.combatant().statuses()).isEmpty();
        assertThat(result.events()).filteredOn(StatusRemoved.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(StatusRemoved.class, event -> {
                    assertThat(event.statusType()).isEqualTo("Vuln");
                    assertThat(event.reason()).isEqualTo("expiry");
                });
    }

    @Test
    void aDelayedDeathTakesItsHolderDownWhenItRunsOut() {
        CombatantState after = turnStart(combatant(30, 0, status("DelayedDeath", 0, 1)));

        assertThat(after.hp()).isZero();
        assertThat(after.statuses()).isEmpty();
    }

    @Test
    void productionDrawsItsValueAsTheTurnOpens() {
        CombatantState holder = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(
                        List.of(new CardInstance("draw-1", "frappe"),
                                new CardInstance("draw-2", "frappe")),
                        List.of(), List.of(), List.of()),
                List.of(status("Production", 2, -1)));

        CombatantState after = turnStart(holder);

        assertThat(after.piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactly("draw-1", "draw-2");
    }

    /** Production se lit comme Énergie : elle ne s'écoule pas, elle dure jusqu'à ce qu'on la retire. */
    @Test
    void productionOutlastsTheTurnItActedOn() {
        CombatantState holder = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(new CardInstance("draw-1", "frappe")), List.of(),
                        List.of(), List.of()),
                List.of(status("Production", 1, -1)));

        CombatantState after = turnEnd(turnStart(holder));

        assertThat(after.statuses())
                .extracting(StatusInstance::statusType)
                .containsExactly("Production");
    }

    /** Une valeur négative retire ce qu'une valeur positive donne, comme une Énergie négative. */
    @Test
    void negativeProductionDiscardsInstead() {
        CombatantState holder = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(),
                        List.of(new CardInstance("hand-1", "frappe"),
                                new CardInstance("hand-2", "frappe")),
                        List.of(), List.of()),
                List.of(status("Production", -1, -1)));

        CombatantState after = turnStart(holder);

        assertThat(after.piles().hand())
                .extracting(CardInstance::instanceId)
                .containsExactly("hand-1");
        assertThat(after.piles().discard())
                .extracting(CardInstance::instanceId)
                .containsExactly("hand-2");
    }

    @Test
    void negativeEnergySpendsEnergyAtTurnStart() {
        StatusEngine.StatusTickResult result = StatusEngine.processTurnStart(
                combatant(30, 0, status("Energy", -1, -1)),
                3, COMBAT_ID, new RandomState(5));

        assertThat(result.combatant().energy()).isEqualTo(2);
        assertThat(result.events()).filteredOn(EnergySpent.class::isInstance)
                .singleElement()
                .isEqualTo(new EnergySpent(COMBAT_ID, 3, "hero", 1, 2));
    }

    private static CombatantState turnStart(CombatantState combatant) {
        return StatusEngine.processTurnStart(combatant, 3, COMBAT_ID, new RandomState(5)).combatant();
    }

    private static CombatantState turnEnd(CombatantState combatant) {
        return StatusEngine.processTurnEnd(combatant, 3, COMBAT_ID, new RandomState(5)).combatant();
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, "", 0);
    }

    private static CombatantState combatant(int maxHp, int armor, StatusInstance... statuses) {
        return new CombatantState("hero", "players", ControllerType.HUMAN, maxHp, maxHp, armor,
                3, 3, 10, new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(statuses));
    }

    private static CombatantState wounded(int maxHp, int hp, StatusInstance... statuses) {
        return new CombatantState("hero", "players", ControllerType.HUMAN, maxHp, hp, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), List.of(statuses));
    }
}
