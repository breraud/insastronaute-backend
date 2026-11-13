package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.event.StatusRemoved;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What dispelling, stealing and transferring actually select and move.
 *
 * <p>These effects previously matched a handful of hardcoded names, most of which this game never
 * applies, so they were close to no-ops on real data.
 */
class DispelFamilyTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final CardDefinition CARD = new CardDefinition(
            "purge", 1, TargetingMode.SINGLE_ENEMY,
            List.of(new EffectDefinition(EffectType.DISPEL_BUFF, 1)));

    @Test
    void dispellingBuffsLeavesTheDebuffsAlone() {
        CombatantState target = combatant("enemy", "enemies", List.of(
                status("Strength", 3, -1), status("Poison", 0, 3), status("Regen", 0, 2)));

        ResolutionStep step = new DispelBuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_BUFF, -1, 0, false),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());

        assertThat(statusTypes(step)).containsExactly("Poison");
    }

    @Test
    void dispellingDebuffsAlsoClearsTheStatusesThatAreNeitherGoodNorBad() {
        CombatantState target = combatant("hero", "players", List.of(
                status("Poison", 0, 3), status("Link", 1, 2), status("Regen", 0, 2)));

        ResolutionStep step = new DispelDebuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_DEBUFF, -1, 0, false),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());

        assertThat(statusTypes(step))
                .describedAs("Unity selects everything that is not a buff")
                .containsExactly("Regen");
    }

    @Test
    void onlyAnEffectIgnoringResistanceStripsAStatusThatResists() {
        CombatantState target = combatant("enemy", "enemies", List.of(status("Clock", 2, -1)));

        ResolutionStep ordinary = new DispelBuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_BUFF, -1, 0, false),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());
        ResolutionStep ignoring = new DispelBuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_BUFF, -1, 0, true),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());

        assertThat(statusTypes(ordinary)).containsExactly("Clock");
        assertThat(statusTypes(ignoring)).isEmpty();
    }

    @Test
    void neverStripsAStatusWithTrueResistance() {
        CombatantState target = combatant("hero", "players", List.of(status("Stun", 1, 1)));

        ResolutionStep step = new DispelDebuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_DEBUFF, -1, 0, true),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());

        assertThat(statusTypes(step)).containsExactly("Stun");
    }

    @Test
    void takesOnlyAsManyStatusesAsTheEffectAsksFor() {
        CombatantState target = combatant("enemy", "enemies", List.of(
                status("Strength", 3, -1), status("Regen", 0, 2), status("Vigor", 2, -1)));

        ResolutionStep step = new DispelBuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_BUFF, 2, 0, false),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());

        assertThat(statusTypes(step)).containsExactly("Vigor");
    }

    @Test
    void reportsEveryStatusItTookOff() {
        CombatantState target = combatant("enemy", "enemies", List.of(status("Strength", 3, -1)));

        ResolutionStep step = new DispelBuffHandler().handle(
                COMBAT_ID, 1, "hero", target, dispel(EffectType.DISPEL_BUFF, -1, 0, false),
                new RandomState(1), List.of(target), List.of(), CARD, List.of());

        assertThat(step.events()).singleElement()
                .isInstanceOfSatisfying(StatusRemoved.class, event -> {
                    assertThat(event.statusType()).isEqualTo("Strength");
                    assertThat(event.removedValue()).isEqualTo(3);
                    assertThat(event.remainingValue()).isZero();
                    assertThat(event.reason()).isEqualTo("dispel");
                });
    }

    @Test
    void stealingMovesTheBuffOntoTheCaster() {
        CombatantState hero = combatant("hero", "players", List.of());
        CombatantState enemy = combatant("enemy", "enemies", List.of(status("Strength", 4, -1)));

        ResolutionStep step = new StealBuffHandler().handle(
                COMBAT_ID, 1, "hero", enemy, dispel(EffectType.STEAL_BUFF, -1, 0, false),
                new RandomState(1), List.of(hero, enemy), List.of(), CARD, List.of());

        assertThat(statusesOf(step, "enemy")).isEmpty();
        assertThat(statusesOf(step, "hero"))
                .extracting(StatusInstance::statusType, StatusInstance::value)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Strength", 4));
        assertThat(step.events()).anyMatch(StatusApplied.class::isInstance);
    }

    @Test
    void stealingHalfABuffLeavesTheRestBehind() {
        CombatantState hero = combatant("hero", "players", List.of());
        CombatantState enemy = combatant("enemy", "enemies", List.of(status("Strength", 5, -1)));

        ResolutionStep step = new StealBuffHandler().handle(
                COMBAT_ID, 1, "hero", enemy, dispel(EffectType.STEAL_BUFF, -1, 50, false),
                new RandomState(1), List.of(hero, enemy), List.of(), CARD, List.of());

        assertThat(statusesOf(step, "hero"))
                .extracting(StatusInstance::value).containsExactly(3);
        assertThat(statusesOf(step, "enemy"))
                .extracting(StatusInstance::value).containsExactly(2);
    }

    @Test
    void transferringHandsTheCasterDebuffsToTheTarget() {
        CombatantState hero = combatant("hero", "players", List.of(status("Poison", 0, 4)));
        CombatantState enemy = combatant("enemy", "enemies", List.of());

        ResolutionStep step = new TransferDebuffHandler().handle(
                COMBAT_ID, 1, "hero", enemy, dispel(EffectType.TRANSFER_DEBUFF, -1, 0, false),
                new RandomState(1), List.of(hero, enemy), List.of(), CARD, List.of());

        assertThat(statusesOf(step, "hero")).isEmpty();
        assertThat(statusesOf(step, "enemy"))
                .extracting(StatusInstance::statusType).containsExactly("Poison");
    }

    private static List<String> statusTypes(ResolutionStep step) {
        return step.combatants().getFirst().statuses().stream()
                .map(StatusInstance::statusType)
                .toList();
    }

    private static List<StatusInstance> statusesOf(ResolutionStep step, String combatantId) {
        return step.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .statuses();
    }

    private static EffectDefinition dispel(EffectType type, int value, int duration, boolean trueEffect) {
        return new EffectDefinition(type, value, false, null, duration, null, null,
                null, null, List.of(), false, trueEffect, null);
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, "", 0);
    }

    private static CombatantState combatant(String id, String team, List<StatusInstance> statuses) {
        return new CombatantState(id, team, ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), statuses);
    }
}
