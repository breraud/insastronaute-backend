package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.HealApplied;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A card modifier scaling on energy spent or gained this turn, as opposed to a gating condition. */
class CardModifierEnergyScalingTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e9");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void energySpentModifierScalesDamage() {
        CardModifier scalesOnSpend = new CardModifier(
                StatType.DAMAGE, ModifierKind.ENERGY_SPENT, 3, "");
        CardDefinition attack = new CardDefinition(
                "strike", "strike", "", 2, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(scalesOnSpend), List.of(new EffectDefinition(EffectType.DAMAGE, 1)));

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(attack.id(), attack)), new RelicEngine(List.of()));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, 2, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("held", "strike")), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1)),
                HERO);

        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, "held", List.of(ENEMY)));

        assertThat(result.rejection()).as("rejet inattendu").isNull();
        int damageDealt = result.events().stream()
                .filter(DamageApplied.class::isInstance)
                .map(DamageApplied.class::cast)
                .mapToInt(DamageApplied::hpLost)
                .sum();

        // base 1 + 3 per energy spent (2) = 7 if the modifier applies at all.
        assertThat(damageDealt).isEqualTo(7);
    }

    @Test
    void damageDealtByAnEarlierMultihitScalesAFollowingStatusDuration() {
        CardModifier scalesDurationWithDamage = new CardModifier(
                StatType.STATUS_DURATION, ModifierKind.DAMAGE_DEALT_WITH_LAST_ACTION, 2, "");
        CardDefinition attackThenPoison = new CardDefinition(
                "iti", "iti", "", 0, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(scalesDurationWithDamage), List.of(
                        new EffectDefinition(EffectType.MULTIHIT, 4, false, null, 2, null, null),
                        new EffectDefinition(EffectType.STATUS, 0, false, "Poison", 1, null, null)));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(attackThenPoison.id(), attackThenPoison)),
                new RelicEngine(List.of()));
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("held", "iti")), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1)), HERO);

        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, "held", List.of(ENEMY)));

        // Two 4-damage hits leave 8 in the action tally. The Unity modifier adds the tally once,
        // irrespective of its authored value, so Poison's authored one turn becomes nine.
        assertThat(result.events()).filteredOn(StatusApplied.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(StatusApplied.class,
                        status -> assertThat(status.duration()).isEqualTo(9));
    }

    @Test
    void damageDealtByAnEarlierEffectScalesAFollowingSelfHeal() {
        CardModifier scalesHealWithDamage = new CardModifier(
                StatType.HEAL, ModifierKind.DAMAGE_DEALT_WITH_LAST_ACTION, 1, "");
        CardDefinition eau = new CardDefinition(
                "eau", "eau", "", 1, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(scalesHealWithDamage), List.of(
                        new EffectDefinition(EffectType.DAMAGE, 1, false, null, 0, null, null),
                        new EffectDefinition(EffectType.HEAL, 0, true, null, 4, null, null)));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(eau.id(), eau)), new RelicEngine(List.of()));
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 20, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("held", "eau")), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1)), HERO);

        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, "held", List.of(ENEMY)));

        assertThat(result.events()).filteredOn(HealApplied.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(HealApplied.class,
                        heal -> assertThat(heal.actualHeal()).isEqualTo(1));
    }
}
