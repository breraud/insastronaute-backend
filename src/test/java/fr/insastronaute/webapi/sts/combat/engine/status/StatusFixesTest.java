package fr.insastronaute.webapi.sts.combat.engine.status;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
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
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Four things statuses were getting wrong, each of which Unity gets right.
 *
 * <p>They are grouped because they share one cause between them: the engine reimplements Unity's
 * statuses, and where it reimplemented the number but not the route the number takes -- through
 * armour, through a die roll, through a signed value -- the difference only shows in play.
 */
class StatusFixesTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final String HERO = "hero";
    private static final String FOE = "foe";

    // ---- Damage over time and armour -------------------------------------------------

    /**
     * Burn is a bite, and armour soaks a bite.
     *
     * <p>{@code StatusEngine} took it straight off HP, so a player who had spent a whole turn
     * building armour still burned for full. Unity has always run it through
     * {@code Character.TakeDamage}, where armour answers first.
     */
    @Test
    void burnIsSoakedByArmourBeforeItReachesHp() {
        CombatState state = state(
                heroWith(30, 10, List.of(new StatusInstance("Burn", 0, 4, "", 0))));

        CombatantState hero = hero(endTurn(state));

        assertThat(hero.hp()).as("armour took all four").isEqualTo(30);
        assertThat(hero.armor()).isEqualTo(6);
    }

    @Test
    void burnSpillsOntoHpOnlyOnceArmourIsGone() {
        CombatState state = state(
                heroWith(30, 1, List.of(new StatusInstance("Burn", 0, 4, "", 0))));

        CombatantState hero = hero(endTurn(state));

        assertThat(hero.armor()).isZero();
        assertThat(hero.hp()).as("one soaked, three through").isEqualTo(27);
    }

    /**
     * Sap no longer bites as a turn opens: it bites whenever anybody plays a card
     * (ReportedCombatFixesTest pins that side).
     */
    @Test
    void sapStaysQuietAsTheTurnOpensAndCloses() {
        StatusInstance sap = new StatusInstance("Sap", 0, -1, "", 0);

        assertThat(StatusBehaviours.atTurnStart(sap).hpDelta()).isZero();
        assertThat(StatusBehaviours.atTurnEnd(sap).hpDelta()).isZero();
    }

    /** Everything else that bites is soaked, which is the half that was wrong. */
    @Test
    void everyOtherBiteIsSoakedByArmour() {
        assertThat(StatusBehaviours.atTurnEnd(new StatusInstance("Burn", 0, 3, "", 0))
                .piercesArmor()).isFalse();
        assertThat(StatusBehaviours.atTurnStart(new StatusInstance("Poison", 0, 3, "", 0))
                .piercesArmor()).isFalse();
        assertThat(StatusBehaviours.atTurnEnd(new StatusInstance("After", 3, 0, "", 0))
                .piercesArmor()).isFalse();
        assertThat(StatusBehaviours.atTurnEnd(new StatusInstance("Trap", 3, 0, "", 0))
                .piercesArmor()).isFalse();
    }

    // ---- The status that never fired -------------------------------------------------

    /**
     * "Statuts" inflicts one of three afflictions as the turn closes.
     *
     * <p>It was declared in the catalog and implemented nowhere: {@code atTurnEnd} had no case for
     * it, so it sat on its holder counting down and doing nothing for its whole duration.
     */
    @Test
    void statusStatusInflictsOneOfItsThreeAfflictionsAtTurnEnd() {
        CombatState state = state(
                heroWith(30, 0, List.of(new StatusInstance("Status", 0, 3, "", 0))));

        CombatantState hero = hero(endTurn(state));

        assertThat(hero.statuses())
                .extracting(StatusInstance::statusType)
                .containsAnyOf("Stun", "Blind", "FullBreak");
    }

    /** The roll comes off the combat's own stream, so a replayed combat inflicts the same one. */
    @Test
    void theAfflictionItRollsIsReproducible() {
        CombatState state = state(
                heroWith(30, 0, List.of(new StatusInstance("Status", 0, 3, "", 0))));

        assertThat(afflictionOn(hero(endTurn(state)))).isEqualTo(afflictionOn(hero(endTurn(state))));
    }

    /**
     * And it counts down while it does, so it ends.
     *
     * <p>Built from {@code StatusTurnEffect.applying} alone it did not tick: that factory is for
     * the statuses spent by expiring rather than by counting, so "Statuts" inflicted an affliction
     * every turn for the rest of the combat.
     */
    @Test
    void statusStatusCountsDownAsItFires() {
        CombatState state = state(
                heroWith(30, 0, List.of(new StatusInstance("Status", 0, 3, "", 0))));

        CombatantState hero = hero(endTurn(state));

        assertThat(hero.statuses())
                .filteredOn(status -> status.statusType().equals("Status"))
                .singleElement()
                .satisfies(status -> assertThat(status.duration()).isEqualTo(2));
    }

    // ---- Transferring a debuff whose strength is a negative number -------------------

    /**
     * A negative-valued debuff is transferred at its magnitude, not rounded away to nothing.
     *
     * <p>The share was taken with {@code Math.ceil} of the signed value, and ceiling a negative
     * fraction gives zero: at the 20% the real cards author, Strength -2 came away as "0
     * Strength", which is neither a buff nor a debuff, and the caster kept the whole of it.
     */
    @Test
    void transfersTheMagnitudeOfANegativeDebuff() {
        CombatResult result = playTransfer(
                new StatusInstance("Strength", -2, 0, "", 0), List.of());

        assertThat(statusOn(foe(result), "Strength").value())
                .as("a fifth of -2, rounded up in magnitude")
                .isEqualTo(-1);
        assertThat(statusOn(hero(result), "Strength").value())
                .as("and the caster is left with the rest")
                .isEqualTo(-1);
    }

    /** What arrives merges with what the target already carries, instead of sitting beside it. */
    @Test
    void theTransferredDebuffMergesWithTheTargetsOwn() {
        CombatResult result = playTransfer(
                new StatusInstance("Strength", -2, 0, "", 0),
                List.of(new StatusInstance("Strength", 3, 0, "", 0)));

        assertThat(foe(result).statuses())
                .filteredOn(status -> status.statusType().equals("Strength"))
                .as("one entry, not two")
                .hasSize(1);
        assertThat(statusOn(foe(result), "Strength").value()).isEqualTo(2);
    }

    // ---- Doubling -------------------------------------------------------------------

    /** Doubling a timed debuff doubles the turns it has left. */
    @Test
    void doublingADebuffDoublesIt() {
        CombatResult result = playDouble(new StatusInstance("Poison", 0, 3, "", 0));

        assertThat(statusOn(foe(result), "Poison").duration()).isEqualTo(6);
    }

    /** And doubling one whose strength is a value doubles the value. */
    @Test
    void doublingAValuedDebuffDoublesItsValue() {
        CombatResult result = playDouble(new StatusInstance("Strength", -2, 0, "", 0));

        assertThat(statusOn(foe(result), "Strength").value()).isEqualTo(-4);
    }

    // ---- harness ---------------------------------------------------------------------

    private static String afflictionOn(CombatantState combatant) {
        return combatant.statuses().stream()
                .map(StatusInstance::statusType)
                .filter(type -> List.of("Stun", "Blind", "FullBreak").contains(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no affliction was inflicted"));
    }

    private static StatusInstance statusOn(CombatantState combatant, String statusType) {
        return combatant.statuses().stream()
                .filter(status -> status.statusType().equals(statusType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + statusType + " on " + combatant.combatantId()));
    }

    private static CombatantState hero(CombatResult result) {
        return combatant(result, HERO);
    }

    private static CombatantState foe(CombatResult result) {
        return combatant(result, FOE);
    }

    private static CombatantState combatant(CombatResult result, String id) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static CombatResult endTurn(CombatState state) {
        CombatResult result = engine(Map.of()).handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatResult playTransfer(
            StatusInstance onCaster, List<StatusInstance> onTarget) {
        CardDefinition card = new CardDefinition(
                "transfer", "Transfert", "", 0, CardType.SKILL, TargetingMode.SINGLE_ENEMY,
                Set.of(),
                // The 20% the authored cards use, carried on the effect's duration.
                List.of(new EffectDefinition(
                        EffectType.TRANSFER_DEBUFF, 1, false, null, 20, null, null)));
        return play(card, List.of(onCaster), onTarget);
    }

    private static CombatResult playDouble(StatusInstance onTarget) {
        CardDefinition card = new CardDefinition(
                "double", "Doublement", "", 0, CardType.SKILL, TargetingMode.SINGLE_ENEMY,
                Set.of(),
                List.of(new EffectDefinition(EffectType.DOUBLE_DEBUFFS, 0, false)));
        return play(card, List.of(), List.of(onTarget));
    }

    private static CombatResult play(
            CardDefinition card,
            List<StatusInstance> onCaster,
            List<StatusInstance> onTarget
    ) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(new CardInstance("played", card.id())),
                        List.of(), List.of()),
                onCaster, List.of(), null, 0);
        CombatantState foe = new CombatantState(
                FOE, "enemies", ControllerType.AI, 30, 30, 0, 3, 3, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                onTarget, List.of(), null, 0);
        CombatState state = combat(hero, foe, HERO);

        CombatResult result = engine(Map.of(card.id(), card)).handle(
                state,
                new PlayCardCommand(COMBAT_ID, state.revision(), HERO, "played", List.of(FOE)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatEngine engine(Map<String, CardDefinition> cards) {
        return new CombatEngine(new CardCatalog("v1", cards), new RelicEngine(List.of()));
    }

    private static CombatantState heroWith(int hp, int armor, List<StatusInstance> statuses) {
        return new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, hp, armor, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                statuses, List.of(), null, 0);
    }

    private static CombatState state(CombatantState hero) {
        CombatantState foe = new CombatantState(
                FOE, "enemies", ControllerType.AI, 30, 30, 0, 3, 3, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return combat(hero, foe, HERO);
    }

    private static CombatState combat(CombatantState hero, CombatantState foe, String active) {
        return new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, foe),
                List.of(new TimelineEntry(active, 0, 0),
                        new TimelineEntry(active.equals(HERO) ? FOE : HERO, 100, 1)),
                active,
                CombatCounters.initial());
    }

    private static CombatState withActiveActor(CombatState state, String actorId) {
        return new CombatState(
                state.combatId(), state.revision(), state.dataVersion(), state.status(),
                state.winnerTeamId(), state.random(), state.combatants(),
                List.of(new TimelineEntry(actorId, 0, 0),
                        new TimelineEntry(actorId.equals(HERO) ? FOE : HERO, 100, 1)),
                actorId, state.counters());
    }
}
