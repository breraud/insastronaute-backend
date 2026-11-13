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
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
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
 * A card whose cost is X spends everything its caster holds and resolves once per point.
 */
class CombatEngineXCostTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final CardInstance PLAYED = new CardInstance("played", "combustion");

    @Test
    void spendsEveryPointOfEnergyAndStrikesOncePerPoint() {
        CombatResult result = play(3);

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result).energy()).isZero();
        assertThat(enemy(result).hp())
                .describedAs("5 damage, three times over")
                .isEqualTo(15);
        assertThat(result.events()).filteredOn(EnergySpent.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(EnergySpent.class,
                        event -> assertThat(event.amount()).isEqualTo(3));
    }

    @Test
    void isStillPlayableWithNoEnergyAndThenDoesNothing() {
        CombatResult result = play(0);

        assertThat(result.accepted())
                .describedAs("X of zero is a legal play, not a rejection")
                .isTrue();
        assertThat(enemy(result).hp()).isEqualTo(30);
    }

    @Test
    void countsTheWholeSpendTowardsTheTurnTally() {
        assertThat(play(3).state().counters().energySpentThisTurn()).isEqualTo(3);
    }

    private static CombatResult play(int energy) {
        CardDefinition combustion = new CardDefinition(
                "combustion", "Combustion", "", 0, true, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5)));
        CombatState state = state(energy);
        return new CombatEngine(
                new CardCatalog("v1", Map.of("combustion", combustion)), new RelicEngine(List.of()))
                .handle(state, new PlayCardCommand(
                        COMBAT_ID, state.revision(), "hero", "played", List.of("enemy")));
    }

    private static CombatantState hero(CombatResult result) {
        return combatant(result, "hero");
    }

    private static CombatantState enemy(CombatResult result) {
        return combatant(result, "enemy");
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState state(int energy) {
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, energy, 3, 10,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID, 2, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
    }
}
