package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ReplayCard: a card doing again what it has already done.
 *
 * <p>Expanded before anything resolves, so the rest of the engine only ever sees a longer list of
 * ordinary effects. What it copies is the effects standing in front of it, which is what makes it
 * usable as a closing flourish rather than a whole-card multiplier.
 */
class ReplayCardEffectTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000722");
    private static final String HERO = "hero";
    private static final String FOE = "foe";

    @Test
    void repeatsWhatCameBeforeIt() {
        // 6 damage, then do that again: 12 in all.
        assertThat(damageDealtBy(damage(6), replay(1))).isEqualTo(12);
    }

    @Test
    void repeatsAsManyTimesAsItsValue() {
        assertThat(damageDealtBy(damage(4), replay(3))).isEqualTo(16);
    }

    /** Only what is in front of it: an effect after the replay happens once. */
    @Test
    void leavesWhatComesAfterItAlone() {
        assertThat(damageDealtBy(damage(5), replay(1), damage(3))).isEqualTo(13);
    }

    /** A replay with nothing in front of it has nothing to replay, and does nothing. */
    @Test
    void doesNothingWhenItOpensTheCard() {
        assertThat(damageDealtBy(replay(2), damage(7))).isEqualTo(7);
    }

    /** Two of them compound, because the second copies the first's expansion along with the rest. */
    @Test
    void twoReplaysCompound() {
        assertThat(damageDealtBy(damage(2), replay(1), replay(1))).isEqualTo(8);
    }

    /** A card authored into an explosion is capped rather than allowed to hang the combat. */
    @Test
    void aRunawayChainIsCapped() {
        List<EffectDefinition> effects = new ArrayList<>();
        effects.add(damage(1));
        for (int index = 0; index < 12; index++) {
            effects.add(replay(3));
        }

        int dealt = damageDealtBy(effects.toArray(new EffectDefinition[0]));

        assertThat(dealt).as("bounded, and the combat still answers").isLessThanOrEqualTo(512);
    }

    private static EffectDefinition damage(int amount) {
        return new EffectDefinition(EffectType.DAMAGE, amount, false);
    }

    private static EffectDefinition replay(int times) {
        return new EffectDefinition(EffectType.REPLAY_CARD, times, true);
    }

    private static int damageDealtBy(EffectDefinition... effects) {
        CardDefinition card = new CardDefinition(
                "rejeu", "Rejeu", "", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(effects));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(new CardInstance("played", card.id())),
                        List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState foe = new CombatantState(
                FOE, "enemies", ControllerType.AI, 9999, 9999, 0, 3, 3, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, foe),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(FOE, 100, 1)),
                HERO, CombatCounters.initial());

        CombatResult result = new CombatEngine(
                new CardCatalog("v1", Map.of(card.id(), card)), new RelicEngine(List.of()))
                .handle(state,
                        new PlayCardCommand(COMBAT_ID, state.revision(), HERO, "played",
                                List.of(FOE)));
        assertThat(result.accepted()).isTrue();

        CombatantState struck = result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(FOE))
                .findFirst()
                .orElseThrow();
        return 9999 - struck.hp();
    }
}
