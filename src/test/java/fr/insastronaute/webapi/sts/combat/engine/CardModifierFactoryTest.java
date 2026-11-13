package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What each authored modifier works out to.
 *
 * <p>These are the rules behind cards that read "one more damage per ..." and they are the last
 * thing standing between a card definition and the number a player actually sees.
 */
class CardModifierFactoryTest {

    private static final CardCatalog CATALOG = new CardCatalog("v1", Map.of(
            "frappe", card("frappe", CardType.ATTACK),
            "garde", card("garde", CardType.SKILL)));

    @Test
    void addsAFlatAmount() {
        assertThat(apply(ModifierKind.FLAT, 3, "", CombatCounters.initial())).isEqualTo(13);
    }

    @Test
    void scalesWithTheArmourTheCasterStandsBehind() {
        assertThat(apply(ModifierKind.ARMOR_ON_SELF, 2, "", CombatCounters.initial()))
                .describedAs("the caster holds 5 armour")
                .isEqualTo(20);
    }

    @Test
    void scalesWithHowWoundedTheCasterIs() {
        assertThat(apply(ModifierKind.LOST_HP, 10, "", CombatCounters.initial()))
                .describedAs("the caster is missing 10 of 30 hit points")
                .isEqualTo(20);
    }

    @Test
    void countsTheStatusesOnEitherSide() {
        assertThat(apply(ModifierKind.BUFF_ON_SELF, 4, "", CombatCounters.initial()))
                .describedAs("the caster carries one buff")
                .isEqualTo(14);
        assertThat(apply(ModifierKind.DEBUFF_ON_TARGET, 4, "", CombatCounters.initial()))
                .describedAs("the target carries one debuff")
                .isEqualTo(14);
    }

    @Test
    void scalesWithHowMuchOfANamedStatusIsCarried() {
        assertThat(apply(ModifierKind.SPECIFIC_STATUS_ON_SELF, 2, "Crystallize", CombatCounters.initial()))
                .describedAs("three Crystallize contribute three times over")
                .isEqualTo(16);
    }

    @Test
    void scalesWithHowLongTheTargetStillHasToWait() {
        assertThat(apply(ModifierKind.TIME_UNTIL_NEXT_TURN, 2, "", CombatCounters.initial()))
                .describedAs("the target acts twenty ticks after the combatant on turn")
                .isEqualTo(50);
    }

    @Test
    void settlesTheTallyBasedModifiersFromWhatTheCombatHasDone() {
        CombatCounters counters = CombatCounters.initial()
                .afterPlaying("played-1", "frappe")
                .afterPlaying("played-2", "garde");

        assertThat(apply(ModifierKind.PLAYED, 3, "", counters))
                .describedAs("two cards played this turn")
                .isEqualTo(16);
        assertThat(apply(ModifierKind.PLAYED, 3, "Attaque", counters))
                .describedAs("only one of them was an attack")
                .isEqualTo(13);
    }

    @Test
    void addsTheTallyItselfForTheTwoModifiersThatDoNotMultiplyIt() {
        CombatCounters counters = new CombatCounters(
                0, List.of(), List.of(), List.of(), 0, 0, 0, 0, 0, 7, false, false,
                Map.of("hero", 4));

        assertThat(apply(ModifierKind.DAMAGE_DEALT_WITH_LAST_ACTION, 99, "", counters))
                .isEqualTo(17);
        assertThat(apply(ModifierKind.HP_LOST_SINCE_LAST_TURN, 99, "", counters))
                .isEqualTo(14);
    }

    private static int apply(ModifierKind kind, int value, String info, CombatCounters counters) {
        List<StatModifier> modifiers = CardModifierFactory.create(
                List.of(new CardModifier(StatType.DAMAGE, kind, value, info)),
                counters, "hero", CATALOG);
        EffectContext ctx = new EffectContext(
                null, hero(), enemy(), List.of(enemy()), card("frappe", CardType.ATTACK),
                false, modifiers, timeline(), List.of(hero(), enemy()));
        return BattleCalculator.getModifiedValue(10, StatType.DAMAGE, ctx);
    }

    private static CombatantState hero() {
        return new CombatantState("hero", "players", ControllerType.HUMAN, 30, 20, 5, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                // Statuses the calculator does not itself read, so the fixture measures the
                // modifier under test rather than a status bonus on top of it.
                List.of(new StatusInstance("Regen", 0, 3, "", 0),
                        new StatusInstance("Crystallize", 3, -1, "", 1)));
    }

    private static CombatantState enemy() {
        return new CombatantState("enemy", "enemies", ControllerType.AI, 30, 30, 4, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Poison", 0, 3, "", 0)));
    }

    private static List<TimelineEntry> timeline() {
        return List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 20, 1));
    }

    private static CardDefinition card(String id, CardType type) {
        return new CardDefinition(id, 1, type, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1)));
    }
}
