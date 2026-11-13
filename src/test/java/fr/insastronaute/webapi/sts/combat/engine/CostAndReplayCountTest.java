package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Two numbers a card carries that nothing was reading: what it costs, and how many times it runs.
 *
 * <p>"Coût nul" was declared in the status catalog and implemented nowhere, and the replay count
 * was worked out with no target in hand, so the one authored card that scales its replays on the
 * target could never see anything to scale on.
 */
class CostAndReplayCountTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000844");
    private static final String HERO = "hero";
    private static final String FOE = "foe";
    private static final int FOE_HP = 9999;

    // ---- Coût nul --------------------------------------------------------------------

    @Test
    void costNullifyMakesTheNextCardFree() {
        CombatResult result = play(costingTwo(), List.of(nullify(2)), List.of());

        assertThat(hero(result).energy()).as("three energy, nothing spent").isEqualTo(3);
    }

    @Test
    void costNullifySpendsOneChargePerCardPlayed() {
        CombatResult result = play(costingTwo(), List.of(nullify(2)), List.of());

        assertThat(statusOn(hero(result), "CostNullify").value()).isEqualTo(1);
    }

    @Test
    void costNullifyIsGoneOnceItsLastChargeIsSpent() {
        CombatResult result = play(costingTwo(), List.of(nullify(1)), List.of());

        assertThat(hero(result).statuses())
                .extracting(StatusInstance::statusType)
                .doesNotContain("CostNullify");
    }

    /** Without it the card costs what it says, so the status is doing the work. */
    @Test
    void theSameCardCostsItsPriceWithoutTheStatus() {
        CombatResult result = play(costingTwo(), List.of(), List.of());

        assertThat(hero(result).energy()).isEqualTo(1);
    }

    /** A follow-up eats no charge, as it does not in Unity. */
    @Test
    void aFollowUpDoesNotEatACharge() {
        CardDefinition followUp = new CardDefinition(
                "suite", "Suite", "", 2, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(CardTag.FOLLOW_UP), List.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1, false)));

        CombatResult result = play(followUp, List.of(nullify(2)), List.of());

        assertThat(statusOn(hero(result), "CostNullify").value()).isEqualTo(2);
    }

    // ---- Replay count ----------------------------------------------------------------

    /**
     * "Frappe directrice" replays once per debuff on whoever it is aimed at.
     *
     * <p>The replay count was asked for with a context carrying no target at all, so a modifier
     * reading the target counted zero every time and the card resolved exactly once however
     * loaded the target was.
     */
    @Test
    void aReplayCountScalingOnTheTargetSeesTheTarget() {
        CardDefinition card = new CardDefinition(
                "directrice", "Frappe directrice", "", 0, false, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new CardModifier(StatType.REPLAY_COUNT, ModifierKind.DEBUFF_ON_TARGET, 1, "")),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5, false)));

        // Poison and Burn, deliberately: both are debuffs and neither touches the damage number,
        // so what the total measures is how many times the card ran and nothing else. Vuln would
        // have muddied it by scaling each strike as well as adding a replay.
        CombatResult result = play(card, List.of(),
                List.of(new StatusInstance("Poison", 0, 2, "", 0),
                        new StatusInstance("Burn", 0, 2, "", 0)));

        assertThat(FOE_HP - foe(result).hp())
                .as("two debuffs, so 1 + 2 resolutions of a five-damage strike")
                .isEqualTo(15);
    }

    @Test
    void andStillResolvesOnceAgainstACleanTarget() {
        CardDefinition card = new CardDefinition(
                "directrice", "Frappe directrice", "", 0, false, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new CardModifier(StatType.REPLAY_COUNT, ModifierKind.DEBUFF_ON_TARGET, 1, "")),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5, false)));

        CombatResult result = play(card, List.of(), List.of());

        assertThat(FOE_HP - foe(result).hp()).isEqualTo(5);
    }

        @Test
        void echoStatusReplaysAnEligibleCardAndConsumesItsDuration() {
                CardDefinition card = new CardDefinition(
                                "echo", "Echo", "", 0, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                                Set.of(), List.of(), List.of(new EffectDefinition(EffectType.DAMAGE, 5, false)));

                CombatResult result = play(card, List.of(new StatusInstance("Echo", 1, 1, "", 1)), List.of());

                assertThat(FOE_HP - foe(result).hp()).isEqualTo(10);
                assertThat(hero(result).statuses()).isEmpty();
        }

    @Test
    void echoReplaysTheCardWithoutReducingItsOwnReplayCount() {
        CardDefinition card = new CardDefinition(
                "echo", "Echo", "", 0, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(), List.of(new EffectDefinition(EffectType.DAMAGE, 5, false)));

        CombatResult result = play(card, List.of(), List.of(),
                List.of(new EnchantmentInstance("Écho", 1)));

        assertThat(FOE_HP - foe(result).hp())
                .as("Echo level one reduces each strike to three damage and resolves it twice")
                .isEqualTo(6);
    }

    // ---- harness ---------------------------------------------------------------------

    private static CardDefinition costingTwo() {
        return new CardDefinition(
                "lourde", "Carte lourde", "", 2, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(new EffectDefinition(EffectType.DAMAGE, 1, false)));
    }

    private static StatusInstance nullify(int charges) {
        return new StatusInstance("CostNullify", charges, -1, "", 0);
    }

    private static StatusInstance statusOn(CombatantState combatant, String statusType) {
        return combatant.statuses().stream()
                .filter(status -> status.statusType().equals(statusType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + statusType + " left"));
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

    private static CombatResult play(
            CardDefinition card,
            List<StatusInstance> onHero,
            List<StatusInstance> onFoe
    ) {
        return play(card, onHero, onFoe, List.of());
    }

    private static CombatResult play(
            CardDefinition card,
            List<StatusInstance> onHero,
            List<StatusInstance> onFoe,
            List<EnchantmentInstance> enchantments
    ) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(new CardInstance("played", card.id(), enchantments)),
                        List.of(), List.of()),
                onHero, List.of(), null, 0);
        CombatantState foe = new CombatantState(
                FOE, "enemies", ControllerType.AI, FOE_HP, FOE_HP, 0, 3, 3, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                onFoe, List.of(), null, 0);
        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, foe),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(FOE, 100, 1)),
                HERO, CombatCounters.initial());

        CombatResult result = new CombatEngine(
                new CardCatalog("v1", Map.of(card.id(), card)), new RelicEngine(List.of()),
                new fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine(
                        List.of(new fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers.ReplayEnchantmentHandler())))
                .handle(state,
                        new PlayCardCommand(COMBAT_ID, state.revision(), HERO, "played",
                                List.of(FOE)));
        assertThat(result.accepted()).isTrue();
        return result;
    }
}
