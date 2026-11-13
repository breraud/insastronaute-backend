package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.ConditionType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectCondition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
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
 * The running tally the scaling cards and the conditional effects read.
 *
 * <p>It is part of the state rather than derived afterwards, so a combat reloaded from storage
 * scales exactly like the one that was saved.
 */
class CombatCountersTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final CardInstance PLAYED = new CardInstance("played", "tested-card");
    private static final CardInstance SPARE = new CardInstance("spare", "frappe");

    @Test
    void remembersWhatWasSpentAndDealtWhileResolving() {
        CombatResult result = play(attack(6));

        assertThat(result.state().counters().energySpentThisTurn()).isEqualTo(1);
        assertThat(result.state().counters().cardsPlayedThisTurn()).containsExactly("played");
        assertThat(result.state().counters().cardsPlayedThisCombat()).containsExactly("played");
    }

    @Test
    void closesTheActionSoTheNextOneStartsFromNothing() {
        CombatResult result = play(attack(6));

        assertThat(result.state().counters().damageDealtThisAction())
                .describedAs("what only described the action that just finished is cleared")
                .isZero();
        assertThat(result.state().counters().cardsExhaustedThisAction()).isZero();
    }

    @Test
    void tracksTheHitPointsACombatantLostSinceItsLastTurn() {
        CombatResult result = play(attack(6));

        assertThat(result.state().counters().hpLostSinceLastTurn("enemy")).isEqualTo(6);
    }

    @Test
    void clearsTheTurnTallyWhenTheTurnChanges() {
        CombatState afterCard = play(attack(6)).state();

        CombatResult ended = engine(attack(6)).handle(afterCard,
                new EndTurnCommand(COMBAT_ID, afterCard.revision(), "hero"));

        assertThat(ended.state().counters().cardsPlayedThisTurn()).isEmpty();
        assertThat(ended.state().counters().cardsPlayedThisCombat()).containsExactly("played");
        assertThat(ended.state().counters().energySpentThisTurn()).isZero();
        assertThat(ended.state().counters().turnCount()).isEqualTo(1);
    }

    @Test
    void skipsAnEffectWhoseConditionDoesNotHold() {
        CardDefinition gated = new CardDefinition(
                "tested-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(conditionalDamage(20, ConditionType.TARGET_HAS_STATUS, "Vuln")));

        CombatResult result = play(gated);

        assertThat(combatant(result.state(), "enemy").hp())
                .describedAs("the enemy carries no Vuln, so the effect stays silent")
                .isEqualTo(30);
    }

    @Test
    void resolvesAnEffectWhoseConditionHolds() {
        CardDefinition gated = new CardDefinition(
                "tested-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(conditionalDamage(20, ConditionType.TARGET_HAS_NO_STATUS, "Vuln")));

        CombatResult result = play(gated);

        assertThat(combatant(result.state(), "enemy").hp()).isEqualTo(10);
    }

    @Test
    void readsTheTallyFromAConditionThatComparesAgainstIt() {
        CardDefinition gated = new CardDefinition(
                "tested-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(
                        new EffectDefinition(EffectType.GAIN_ENERGY, 3, true),
                        conditionalDamage(9, ConditionType.ENERGY_GAINED_THRESHOLD, "3")));

        CombatResult result = play(gated);

        assertThat(combatant(result.state(), "enemy").hp())
                .describedAs("the energy gained by the first effect satisfies the second")
                .isEqualTo(21);
    }

    @Test
    void knowsWhenACardIsBeingPlayedForTheFirstTimeThisTurn() {
        CardDefinition gated = new CardDefinition(
                "tested-card", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(conditionalDamage(5, ConditionType.FIRST_TIME_PLAYING_THIS_CARD_THIS_TURN, "")));
        CombatState afterFirst = play(gated).state();
        CombatState replayable = withHand(afterFirst, List.of(PLAYED, SPARE));

        CombatResult second = engine(gated).handle(replayable, new PlayCardCommand(
                COMBAT_ID, replayable.revision(), "hero", "played", List.of("enemy")));

        assertThat(combatant(afterFirst, "enemy").hp()).isEqualTo(25);
        assertThat(combatant(second.state(), "enemy").hp())
                .describedAs("the second play of the same instance no longer qualifies")
                .isEqualTo(25);
    }

    private static EffectDefinition conditionalDamage(int value, ConditionType type, String argument) {
        return new EffectDefinition(EffectType.DAMAGE, value, false, null, 0, null, null,
                null, null, List.of(), false, false, new EffectCondition(type, argument));
    }

    private static CardDefinition attack(int damage) {
        return new CardDefinition("tested-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(new EffectDefinition(EffectType.DAMAGE, damage)));
    }

    private static CombatEngine engine(CardDefinition definition) {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(
                        definition.id(), definition,
                        "frappe", new CardDefinition("frappe", 1, CardType.ATTACK,
                                TargetingMode.SINGLE_ENEMY, Set.of(),
                                List.of(new EffectDefinition(EffectType.DAMAGE, 1))))),
                new RelicEngine(List.of()));
    }

    private static CombatResult play(CardDefinition definition) {
        CombatState state = state();
        return engine(definition).handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), "hero", "played", List.of("enemy")));
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState withHand(CombatState state, List<CardInstance> hand) {
        CombatantState hero = combatant(state, "hero");
        CombatantState updated = new CombatantState(
                hero.combatantId(), hero.teamId(), hero.controllerType(), hero.maxHp(), hero.hp(),
                hero.armor(), 3, hero.baseEnergy(), hero.turnDelayTicks(),
                new CardPiles(hero.piles().draw(), hand, List.of(), hero.piles().exhaust()),
                hero.statuses(), hero.relics(), hero.intentCardId(), hero.patternIndex());
        return new CombatState(state.combatId(), state.revision(), state.dataVersion(),
                state.status(), state.winnerTeamId(), state.random(),
                state.combatants().stream()
                        .map(candidate -> candidate.combatantId().equals("hero") ? updated : candidate)
                        .toList(),
                state.timeline(), state.activeCombatantId(), state.counters());
    }

    private static CombatState state() {
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(PLAYED, SPARE), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID, 4, "v1", CombatStatus.ACTIVE, null, new RandomState(5),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}
