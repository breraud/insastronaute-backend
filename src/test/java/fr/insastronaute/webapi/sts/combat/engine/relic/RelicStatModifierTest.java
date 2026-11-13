package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.CounterStanceRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.TwelfthStrikeRelic;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The two relics that change a number rather than doing something.
 *
 * <p>{@code BattleCalculator} walked the caster's and the target's relics and asked a switch
 * statement that had no cases at all, so no relic could ever change a value. Unity has exactly
 * two of them: the twelfth Attack of a combat hits twice as hard, and a Skill played right after
 * an Attack gives two extra Armour.
 */
class RelicStatModifierTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000121");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void doublesTheDamageOfTheTwelfthAttackOfTheCombat() {
        CombatResult result = play(
                new TwelfthStrikeRelic(),
                "TwelfthStrikeRelic",
                attack(),
                elevenAttacksIn());

        assertThat(enemy(result).hp()).isEqualTo(30 - 20);
    }

    @Test
    void leavesTheEleventhAttackAlone() {
        CombatResult result = play(
                new TwelfthStrikeRelic(),
                "TwelfthStrikeRelic",
                attack(),
                tenAttacksIn());

        assertThat(enemy(result).hp()).isEqualTo(30 - 10);
    }

    @Test
    void addsTwoArmourToASkillPlayedRightAfterAnAttack() {
        CombatResult result = play(
                new CounterStanceRelicHandler(),
                "CounterStanceRelic",
                guard(),
                CombatCounters.initial().afterPlaying("a", "some-attack", CardType.ATTACK));

        assertThat(hero(result).armor()).isEqualTo(7);
    }

    @Test
    void leavesASkillPlayedAfterASkillAlone() {
        CombatResult result = play(
                new CounterStanceRelicHandler(),
                "CounterStanceRelic",
                guard(),
                CombatCounters.initial().afterPlaying("a", "some-skill", CardType.SKILL));

        assertThat(hero(result).armor()).isEqualTo(5);
    }

    private static CombatCounters elevenAttacksIn() {
        CombatCounters counters = CombatCounters.initial();
        for (int index = 0; index < 11; index++) {
            counters = counters.afterPlaying("a" + index, "some-attack", CardType.ATTACK);
        }
        return counters;
    }

    private static CombatCounters tenAttacksIn() {
        CombatCounters counters = CombatCounters.initial();
        for (int index = 0; index < 10; index++) {
            counters = counters.afterPlaying("a" + index, "some-attack", CardType.ATTACK);
        }
        return counters;
    }

    private static CombatantState hero(CombatResult result) {
        return combatant(result, HERO);
    }

    private static CombatantState enemy(CombatResult result) {
        return combatant(result, ENEMY);
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CardDefinition attack() {
        return new CardDefinition(
                "played-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 10, false, null, 0, "", null)));
    }

    private static CardDefinition guard() {
        return new CardDefinition(
                "played-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 5, true, null, 0, "", null)));
    }

    private static CombatResult play(
            RelicHandler relic,
            String relicId,
            CardDefinition definition,
            CombatCounters counters
    ) {
        CombatState state = state(relicId, counters);
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of(relic)));
        CombatResult result = engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        HERO,
                        "played",
                        definition.targetingMode() == TargetingMode.SELF
                                ? List.of(HERO)
                                : List.of(ENEMY)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatState state(String relicId, CombatCounters counters) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(
                        List.of(),
                        List.of(new CardInstance("played", "played-card")),
                        List.of(),
                        List.of()),
                List.of(),
                List.of(relicId),
                null,
                0);
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                30,
                30,
                0,
                2,
                2,
                200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 100, 1)),
                HERO,
                counters);
    }
}
