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
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.DreadBellRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.ImmutableSpineRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.StatusNullificationRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.TemporalAnchorRelic;
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
 * The relics that say no.
 *
 * <p>Three of Unity's relics answer a question rather than do something: may this status land, may
 * this turn be moved, and one answers a blow landing. The Java interface had no way to ask, so all
 * four were empty stubs.
 */
class RelicGatesTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000141");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void statusNullificationKeepsEveryStatusOffItsOwner() {
        CombatResult result = play(
                new StatusNullificationRelic(),
                "StatusNullificationRelic",
                selfStatus("Strength", 2));

        assertThat(hero(result).statuses()).isEmpty();
    }

    @Test
    void immutableSpineEatsTheFirstDebuffOfTheCombatAndHealsTwo() {
        CombatResult result = play(
                new ImmutableSpineRelic(),
                "ImmutableSpineRelic",
                selfStatus("Vuln", 2));

        assertThat(hero(result).statuses()).isEmpty();
        assertThat(hero(result).hp()).isEqualTo(22);
    }

    @Test
    void immutableSpineLetsTheSecondDebuffThrough() {
        CombatResult result = play(
                new ImmutableSpineRelic(), stateWithSpentSpine(), selfStatus("Vuln", 2));

        assertThat(hero(result).statuses())
                .anySatisfy(status -> assertThat(status.statusType()).isEqualTo("Vuln"));
    }

    @Test
    void dreadBellGainsStrengthForEveryBlowItsOwnerLands() {
        CombatResult result = play(
                new DreadBellRelic(),
                "DreadBellRelic",
                attack());

        assertThat(hero(result).statuses())
                .anySatisfy(status -> {
                    assertThat(status.statusType()).isEqualTo("Strength");
                    assertThat(status.value()).isEqualTo(1);
                });
    }

    @Test
    void temporalAnchorRefusesToHaveItsOwnersTurnMoved() {
        CombatResult result = play(
                new TemporalAnchorRelic(),
                "TemporalAnchorRelic",
                advanceSelf());

        assertThat(result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(HERO))
                .mapToLong(TimelineEntry::readyAtTick)
                .findFirst()
                .orElseThrow()).isEqualTo(50);
    }

    private static CombatState stateWithSpentSpine() {
        CombatState fresh = state("ImmutableSpineRelic", 20);
        List<CombatantState> combatants = fresh.combatants().stream()
                .map(combatant -> combatant.combatantId().equals(HERO)
                        ? combatant.remembering("ImmutableSpineRelic", "used", 1)
                        : combatant)
                .toList();
        return new CombatState(
                fresh.combatId(),
                fresh.revision(),
                fresh.dataVersion(),
                fresh.status(),
                fresh.winnerTeamId(),
                fresh.random(),
                combatants,
                fresh.timeline(),
                fresh.activeCombatantId(),
                fresh.counters());
    }

    private static CombatantState hero(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CardDefinition selfStatus(String statusType, int amount) {
        return new CardDefinition(
                "played-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.STATUS, amount, true, statusType, 2, "", null)));
    }

    private static CardDefinition attack() {
        return new CardDefinition(
                "played-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 4, false, null, 0, "", null)));
    }

    private static CardDefinition advanceSelf() {
        return new CardDefinition(
                "played-card", 1, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(
                        EffectType.ADVANCE_TURN, 1, true, null, 0, "", null)));
    }

    private static CombatResult play(
            RelicHandler relic, String relicId, CardDefinition definition) {
        return play(relic, state(relicId, 20), definition);
    }

    private static CombatResult play(
            RelicHandler relic, CombatState state, CardDefinition definition) {
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

    private static CombatState state(String relicId, int heroHp) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                heroHp,
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
                List.of(new TimelineEntry(HERO, 50, 0), new TimelineEntry(ENEMY, 100, 1)),
                HERO);
    }
}
