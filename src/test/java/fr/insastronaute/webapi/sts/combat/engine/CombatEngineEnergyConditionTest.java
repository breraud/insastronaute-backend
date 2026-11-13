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
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
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
 * "Si vous avez dépensé/gagné X énergie ce tour" : Unity lit le compteur du combat au moment où
 * l'effet se résout, et l'énergie de la carte y est déjà dépensée (CombatManager.cs:3544 ->
 * Character.SpendEnergy, avant la résolution des effets).
 */
class CombatEngineEnergyConditionTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void aCardsOwnCostCountsTowardItsEnergySpentCondition() {
        CombatResult result = play("strike", 2,
                List.of(conditionalDamage(5, ConditionType.ENERGY_SPENT_THRESHOLD, "2")),
                3, CombatCounters.initial());

        assertThat(result.accepted()).isTrue();
        assertThat(damageDealt(result))
                .describedAs("la carte à 2 a déjà dépensé 2 quand sa condition est lue")
                .isEqualTo(5);
    }

    @Test
    void energyGainedEarlierInTheSameCardUnlocksItsConditionalEffect() {
        CombatResult result = play("surge", 0,
                List.of(
                        new EffectDefinition(EffectType.GAIN_ENERGY, 2, true, null, 0, null, null,
                                null, null, List.of(), false, false, null, 0),
                        conditionalDamage(5, ConditionType.ENERGY_GAINED_THRESHOLD, "2")),
                1, CombatCounters.initial());

        assertThat(damageDealt(result)).isEqualTo(5);
    }

    @Test
    void aCardPlayedAfterwardsSeesWhatAnEarlierCardSpent() {
        CombatState state = state(3,
                List.of(new CardInstance("held-1", "strike"), new CardInstance("held-2", "finisher")),
                List.of(), HERO, CombatCounters.initial());
        CombatState afterFirst = playIn(state, "held-1", "strike", 2, List.of()).state();

        CombatResult second = playIn(afterFirst, "held-2", "finisher", 0,
                List.of(conditionalDamage(5, ConditionType.ENERGY_SPENT_THRESHOLD, "2")));

        assertThat(damageDealt(second))
                .describedAs("la dépense de la carte précédente est encore comptée ce tour")
                .isEqualTo(5);
    }

    @Test
    void anEnemyMoveSeesTheCombatsEnergyTallyRatherThanAFreshOne() {
        CombatCounters spentByHero = CombatCounters.initial().observing(List.of(
                new EnergySpent(COMBAT_ID, 7, HERO, 3, 0)));

        CombatResult result = playPattern("pounce", 0,
                List.of(conditionalDamage(5, ConditionType.ENERGY_SPENT_THRESHOLD, "3")),
                spentByHero);

        assertThat(damageDealt(result))
                .describedAs("le coup ennemi lit le compteur du combat, pas un compteur vide")
                .isEqualTo(5);
        assertThat(result.state().counters().energySpentThisTurn())
                .describedAs("le compte accumulé survit au coup ennemi")
                .isEqualTo(3);
    }

    @Test
    void anEnemyMovesOwnCostCountsTowardItsCondition() {
        CombatResult result = playPattern("pounce", 2,
                List.of(conditionalDamage(5, ConditionType.ENERGY_SPENT_THRESHOLD, "2")),
                CombatCounters.initial());

        assertThat(damageDealt(result)).isEqualTo(5);
    }

    // --- montage -------------------------------------------------------------------------------

    private static EffectDefinition conditionalDamage(int damage, ConditionType type, String threshold) {
        return new EffectDefinition(EffectType.DAMAGE, damage, false, null, 0, null, null,
                null, null, List.of(), false, false, new EffectCondition(type, threshold), 0);
    }

    private static int damageDealt(CombatResult result) {
        return result.events().stream()
                .filter(DamageApplied.class::isInstance)
                .map(DamageApplied.class::cast)
                .mapToInt(DamageApplied::hpLost)
                .sum();
    }

    private static CombatResult play(
            String cardId, int cost, List<EffectDefinition> effects, int energy,
            CombatCounters counters) {
        return playIn(state(energy, List.of(new CardInstance("held", cardId)),
                List.of(), HERO, counters), "held", cardId, cost, effects);
    }

    private static CombatResult playIn(
            CombatState state, String instanceId, String cardId, int cost,
            List<EffectDefinition> effects) {
        CardDefinition definition = new CardDefinition(
                cardId, cardId, "", cost, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                effects);
        Map<String, CardDefinition> cards = new java.util.HashMap<>();
        cards.put(cardId, definition);
        // Une main peut mélanger des cartes d'un montage précédent : le catalogue les connaît
        // toutes tant qu'elles partagent le combat.
        for (CardInstance held : heroOf(state).piles().hand()) {
            cards.putIfAbsent(held.definitionId(), new CardDefinition(
                    held.definitionId(), held.definitionId(), "", 2, CardType.ATTACK,
                    TargetingMode.SINGLE_ENEMY, Set.of(), List.of()));
        }
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", cards), new RelicEngine(List.of()));
        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, instanceId, List.of(ENEMY)));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatantState heroOf(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult playPattern(
            String moveId, int cost, List<EffectDefinition> effects, CombatCounters counters) {
        CardDefinition move = new CardDefinition(
                moveId, cost, TargetingMode.SINGLE_ENEMY, effects);
        CombatState state = state(2, List.of(), List.of(), ENEMY, counters);
        CombatResult result = new CombatEngine(
                new CardCatalog("v1", Map.of(moveId, move)), new RelicEngine(List.of()))
                .handle(state, new AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), ENEMY, moveId, List.of(HERO), 1));
        assertThat(result.rejection()).as("rejet inattendu").isNull();
        return result;
    }

    private static CombatState state(
            int heroEnergy, List<CardInstance> heroHand,
            List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> heroStatuses,
            String active, CombatCounters counters) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, heroEnergy, 3, 10,
                new CardPiles(List.of(), heroHand, List.of(), List.of()), heroStatuses);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        List<TimelineEntry> timeline = HERO.equals(active)
                ? List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1))
                : List.of(new TimelineEntry(ENEMY, 0, 0), new TimelineEntry(HERO, 1, 1));
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy), timeline, active, counters);
    }
}
