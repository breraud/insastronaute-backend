package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.AnimationType;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.ITIRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ITI, "Processeur transcendant": everything thrown at its bearer comes back once.
 *
 * <p>The relic is answered from the far side of a card play, which no relic hook could reach
 * before: {@code onCardPlayed} is asked of the caster's own relics, and the caster is precisely
 * who does not carry this one. Its copies are card instances over the played card's definition,
 * wearing a name built from the effects, a family read off them, a price of their own, and the
 * two markers that make them a one-shot.
 */
class ITIRelicTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000917");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String ITI = "ITIRelic";

    /** The enemy move every test here has thrown at the hero: a slash, a dispel and a hurry. */
    private static CardDefinition enemyMove() {
        return new CardDefinition(
                "enemy-move:brute:0",
                "Charge brutale",
                "",
                0,
                CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY,
                Set.of(),
                List.of(
                        damage(6, AnimationType.SLASH),
                        new EffectDefinition(EffectType.DISPEL_BUFF, 1, false),
                        new EffectDefinition(EffectType.ADVANCE_TURN, 1, true)));
    }

    @Test
    void leavesACopyOfTheEnemyMoveInTheBearersHand() {
        CombatResult result = enemyPlays(enemyMove(), List.of(ITI));

        assertThat(hero(result).piles().hand())
                .extracting(CardInstance::definitionId)
                .contains("enemy-move:brute:0");
    }

    @Test
    void namesTheCopyAfterWhatItDoes() {
        CombatResult result = enemyPlays(enemyMove(), List.of(ITI));

        assertThat(copy(result).displayName()).isEqualTo("Annulame T");
    }

    @Test
    void givesTheCopyTheFamilyAndPriceItsEffectsEarn() {
        CombatResult result = enemyPlays(enemyMove(), List.of(ITI));

        assertThat(copy(result).cardType()).isEqualTo(CardType.ATTACK);
        assertThat(copy(result).cost()).isEqualTo(1);
    }

    /** A copy is had once: it burns when played and evaporates when it is not. */
    @Test
    void marksTheCopyEtherealAndExhausting() {
        CombatResult result = enemyPlays(enemyMove(), List.of(ITI));

        assertThat(copy(result).tags()).contains(CardTag.ETHEREAL, CardTag.EXHAUST);
    }

    /** A bearer of some other relic is handed nothing at all. */
    @Test
    void staysQuietForACombatantWithoutTheRelic() {
        CombatResult result = enemyPlays(enemyMove(), List.of("MECARelic"));

        assertThat(hero(result).piles().hand())
                .extracting(CardInstance::definitionId)
                .doesNotContain("enemy-move:brute:0");
    }

    /**
     * The copy is gone at the end of the bearer's next turn rather than joining the discard pile,
     * which is the whole of what Ethereal means -- and the only thing keeping a stolen move from
     * being drawn again for the rest of the fight.
     */
    @Test
    void theCopyEvaporatesAtTheEndOfTheBearersTurnRatherThanBeingDiscarded() {
        CombatResult stolen = enemyPlays(enemyMove(), List.of(ITI));
        CombatState afterTheft = stolen.state();

        CombatResult ended = engine(enemyMove()).handle(
                withActiveActor(afterTheft, HERO),
                new EndTurnCommand(COMBAT_ID, afterTheft.revision(), HERO));
        assertThat(ended.accepted()).isTrue();

        assertThat(hero(ended).piles().exhaust())
                .as("the copy burned")
                .extracting(CardInstance::displayName)
                .contains("Annulame T");
        assertThat(hero(ended).piles().discard())
                .as("and never reached the discard pile, where it could be drawn again")
                .extracting(CardInstance::displayName)
                .doesNotContain("Annulame T");
    }

    /** An ordinary card in the same hand still discards, so Ethereal is not eating the hand. */
    @Test
    void anOrdinaryCardInTheSameHandStillDiscards() {
        CombatResult stolen = enemyPlays(enemyMove(), List.of(ITI));

        CombatResult ended = engine(enemyMove()).handle(
                withActiveActor(stolen.state(), HERO),
                new EndTurnCommand(COMBAT_ID, stolen.state().revision(), HERO));

        assertThat(hero(ended).piles().discard())
                .extracting(CardInstance::instanceId)
                .contains("hero-card");
    }

    /** A copy of a move that only ever armours is a Compétence, and costs the same one point. */
    @Test
    void readsTheFamilyOffTheEffectsRatherThanOffTheSourceCard() {
        CardDefinition guard = new CardDefinition(
                "enemy-move:brute:1",
                "Garde",
                "",
                0,
                // The source calls itself an Attack; its effects say otherwise, and they win.
                CardType.ATTACK,
                TargetingMode.SELF,
                Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 5, true)));

        CombatResult result = enemyPlays(guard, List.of(ITI));

        assertThat(copy(result).cardType()).isEqualTo(CardType.SKILL);
        assertThat(copy(result).displayName()).isEqualTo("Bouclier");
    }

    /**
     * A run, not a duel: the enemy spends a move off its pattern instead of playing a card out of
     * a hand.
     *
     * <p>That is a wholly separate path through the engine, and it asks no relic anything -- which
     * is how a relic written against card plays could pass every duel test and still do nothing
     * for the entire mode it was meant for.
     */
    @Test
    void alsoAnswersAMoveAnEnemySpendsOffItsPattern() {
        CardDefinition move = enemyMove();
        CombatState state = state(move, List.of(ITI));

        CombatResult result = engine(move).handle(
                state,
                new AiPlayPatternCommand(
                        COMBAT_ID, state.revision(), ENEMY, move.id(), List.of(HERO)));
        assertThat(result.accepted()).isTrue();

        assertThat(copy(result).displayName()).isEqualTo("Annulame T");
    }

    private static CardInstance copy(CombatResult result) {
        return hero(result).piles().hand().stream()
                .filter(card -> card.definitionId().startsWith("enemy-move:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no copy reached the hand"));
    }

    private static CombatantState hero(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static EffectDefinition damage(int value, AnimationType animation) {
        return new EffectDefinition(
                EffectType.DAMAGE, value, false, null, 0, null, null, null, null, List.of(),
                false, false, null, 0, animation);
    }

    private static CombatEngine engine(CardDefinition move) {
        return new CombatEngine(
                new CardCatalog("v1", Map.of(move.id(), move, "hero-move", heroCard())),
                new RelicEngine(List.of(new ITIRelicHandler())));
    }

    private static CardDefinition heroCard() {
        return new CardDefinition(
                "hero-move", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
    }

    private static CombatResult enemyPlays(CardDefinition move, List<String> heroRelics) {
        CombatState state = state(move, heroRelics);
        CombatResult result = engine(move).handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        ENEMY,
                        "enemy-card",
                        move.targetingMode() == TargetingMode.SELF
                                ? List.of(ENEMY)
                                : List.of(HERO)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    /** The same state with the turn handed to somebody else, so a turn can be ended on it. */
    private static CombatState withActiveActor(CombatState state, String actorId) {
        return new CombatState(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.random(),
                state.combatants(),
                List.of(new TimelineEntry(actorId, 0, 0), new TimelineEntry(ENEMY, 100, 1)),
                actorId,
                state.counters());
    }

    private static CombatState state(CardDefinition move, List<String> heroRelics) {
        List<CardInstance> heroHand = new ArrayList<>();
        heroHand.add(new CardInstance("hero-card", "hero-move"));

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
                new CardPiles(List.of(), heroHand, List.of(), List.of()),
                List.<StatusInstance>of(),
                heroRelics,
                null,
                0);
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                3,
                3,
                200,
                new CardPiles(
                        List.of(),
                        List.of(new CardInstance("enemy-card", move.id())),
                        List.of(),
                        List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(ENEMY, 0, 0), new TimelineEntry(HERO, 100, 1)),
                ENEMY,
                CombatCounters.initial());
    }
}
