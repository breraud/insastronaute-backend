package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardRarity;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.ConditionType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectCondition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Three things a card can be gated on, and one thing it must never be handed.
 *
 * <p>Grouped because all four are the same kind of failure: something the engine computes
 * correctly but never tells anybody about, so whatever reads it downstream sees nothing.
 */
class ConditionsPoolAndTimelineTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000001066");
    private static final String HERO = "hero";
    private static final String FOE = "foe";

    // ---- ArmorBreak ------------------------------------------------------------------

    /**
     * A blow that destroys armour satisfies ArmorBreak.
     *
     * <p>Only the explicit Break effect ever announced an armour break, so the counters never saw
     * one and a card that strips five armour with its first effect could not gate its second on
     * having done so.
     */
    @Test
    void aBlowThatDestroysArmourSatisfiesArmorBreak() {
        CombatResult result = play(
                gated(damage(10), ConditionType.ARMOR_BREAK, damage(5)),
                5, 100);

        assertThat(damageTaken(result))
                .as("ten through five armour is five, then the gated five lands")
                .isEqualTo(10);
    }

    @Test
    void andDoesNotSatisfyItWhenTheArmourHolds() {
        CombatResult result = play(
                gated(damage(3), ConditionType.ARMOR_BREAK, damage(5)),
                20, 100);

        assertThat(damageTaken(result)).as("armour absorbed it all, nothing broke").isZero();
    }

    // ---- KillingBlow -----------------------------------------------------------------

    @Test
    void aBlowThatKillsSatisfiesKillingBlow() {
        CombatResult result = play(
                gated(damage(10), ConditionType.KILLING_BLOW, selfArmor(7)),
                0, 4);

        assertThat(hero(result).armor()).as("the kill paid out").isEqualTo(7);
    }

    @Test
    void andDoesNotSatisfyItWhenTheTargetSurvives() {
        CombatResult result = play(
                gated(damage(10), ConditionType.KILLING_BLOW, selfArmor(7)),
                0, 100);

        assertThat(hero(result).armor()).isZero();
    }

    // ---- CutInTurn -------------------------------------------------------------------

    /**
     * A self-aimed cut-in puts the caster's <em>next</em> turn right after whoever acts next.
     *
     * <p>Every authored CutInTurn card is self-aimed, and self-aimed was the broken half: the
     * handler rewrote the caster's current timeline entry, and {@code endTurn} then rescheduled
     * that same entry from the tick it started at plus the wait it owed, discarding the edit.
     * The effect did nothing at all in play.
     */
    @Test
    void aSelfAimedCutInSurvivesTheEndOfTurnReschedule() {
        CardDefinition card = new CardDefinition(
                "disto", "Distorsion", "", 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.CUT_IN_TURN, 1, true)));

        CombatState after = endTurnAfterPlaying(card);

        // The hero's wait is 100 ticks, so without the cut-in its next turn lands at 100 --
        // level with the foe, and behind it on sequence. Cutting in puts it at the foe's tick
        // plus one, so the hero acts again immediately after the foe rather than alongside it.
        assertThat(tickOf(after, HERO)).isEqualTo(tickOf(after, FOE) + 1);
    }

    /** Without the cut-in the same turn lands where the plain wait puts it. */
    @Test
    void andWithoutItTheTurnLandsWhereTheWaitPutsIt() {
        CardDefinition card = new CardDefinition(
                "rien", "Rien", "", 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));

        CombatState after = endTurnAfterPlaying(card);

        assertThat(tickOf(after, HERO)).isEqualTo(100);
    }

    // ---- The random pool -------------------------------------------------------------

    /**
     * A random draw never offers a card the game only ever creates on purpose.
     *
     * <p>The pool falls back to the whole catalog when the character has no created cards of its
     * own, and the whole catalog holds follow-ups, unobtainables and Specials -- 79 of the 396
     * authored cards between them.
     */
    @Test
    void aRandomDrawOnlyEverOffersCardsMeantToBeHeld() {
        CardDefinition draw = new CardDefinition(
                "tirage", "Tirage", "", 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.ADD_RANDOM_CARD, 8, true, null, 0, null, null,
                        CardSelectionSource.HAND, null, List.of())));

        Map<String, CardDefinition> cards = new LinkedHashMap<>();
        cards.put(draw.id(), draw);
        cards.put("ordinaire", plain("ordinaire", CardRarity.COMMON, Set.of()));
        cards.put("speciale", plain("speciale", CardRarity.SPECIAL, Set.of()));
        cards.put("cachee", plain("cachee", CardRarity.COMMON, Set.of(CardTag.UNOBTAINABLE)));
        cards.put("suite", plain("suite", CardRarity.COMMON, Set.of(CardTag.FOLLOW_UP)));

        CombatResult result = playWith(draw, List.of(HERO), 0, 100,
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(FOE, 100, 1)),
                new CardCatalog("v1", cards));

        Set<String> drawn = new LinkedHashSet<>();
        hero(result).piles().hand().forEach(card -> drawn.add(card.definitionId()));
        drawn.remove("tirage");

        assertThat(drawn)
                .as("eight draws, and every one of them the only card fit to be held")
                .containsExactly("ordinaire");
    }

    // ---- harness ---------------------------------------------------------------------

    private static CardDefinition plain(String id, CardRarity rarity, Set<CardTag> tags) {
        return new CardDefinition(
                id, id, "", 0, false, CardType.SKILL, TargetingMode.SELF, tags, List.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)), rarity);
    }

    private static EffectDefinition damage(int amount) {
        return new EffectDefinition(EffectType.DAMAGE, amount, false);
    }

    private static EffectDefinition selfArmor(int amount) {
        return new EffectDefinition(EffectType.ARMOR, amount, true);
    }

    /** A card whose second effect only resolves once the first has done what the gate asks. */
    private static CardDefinition gated(
            EffectDefinition opener, ConditionType gate, EffectDefinition gatedEffect) {
        EffectDefinition conditional = new EffectDefinition(
                gatedEffect.type(), gatedEffect.value(), gatedEffect.targetSelf(), null, 0, null,
                null, null, null, List.of(), false, false, new EffectCondition(gate, ""));
        return new CardDefinition(
                "gate", "Carte à condition", "", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(opener, conditional));
    }

    private static int damageTaken(CombatResult result) {
        return 100 - foe(result).hp();
    }

    /** Plays the card on the hero's turn, then closes that turn so the reschedule happens. */
    private static CombatState endTurnAfterPlaying(CardDefinition card) {
        CombatResult played = playWith(card, List.of(HERO), 0, 100,
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(FOE, 100, 1)));

        CombatResult ended = new CombatEngine(
                new CardCatalog("v1", Map.of(card.id(), card)), new RelicEngine(List.of()))
                .handle(played.state(),
                        new fr.insastronaute.webapi.sts.combat.command.EndTurnCommand(
                                COMBAT_ID, played.state().revision(), HERO));
        assertThat(ended.accepted()).isTrue();
        return ended.state();
    }

    private static long tickOf(CombatState state, String combatantId) {
        return state.timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .mapToLong(TimelineEntry::readyAtTick)
                .findFirst()
                .orElseThrow();
    }

    private static long tickOf(CombatResult result, String combatantId) {
        return result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .mapToLong(TimelineEntry::readyAtTick)
                .findFirst()
                .orElseThrow();
    }

    private static CombatantState hero(CombatResult result) {
        return of(result, HERO);
    }

    private static CombatantState foe(CombatResult result) {
        return of(result, FOE);
    }

    private static CombatantState of(CombatResult result, String id) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult play(CardDefinition card, int foeArmor, int foeHp) {
        return playWith(card, List.of(FOE), foeArmor, foeHp,
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(FOE, 100, 1)));
    }

    private static CombatResult playWith(
            CardDefinition card, List<String> targets, int foeArmor, int foeHp,
            List<TimelineEntry> timeline) {
        return playWith(card, targets, foeArmor, foeHp, timeline,
                new CardCatalog("v1", Map.of(card.id(), card)));
    }

    private static CombatResult playWith(
            CardDefinition card, List<String> targets, int foeArmor, int foeHp,
            List<TimelineEntry> timeline, CardCatalog catalog) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(new CardInstance("played", card.id())),
                        List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState foe = new CombatantState(
                FOE, "enemies", ControllerType.AI, 100, foeHp, foeArmor, 3, 3, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, foe), timeline, HERO, CombatCounters.initial());

        CombatResult result = new CombatEngine(catalog, new RelicEngine(List.of())).handle(
                state,
                new PlayCardCommand(COMBAT_ID, state.revision(), HERO, "played", targets));
        assertThat(result.accepted()).isTrue();
        return result;
    }
}
