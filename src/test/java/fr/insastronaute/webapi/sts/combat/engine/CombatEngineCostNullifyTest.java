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
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * "Coût nul" makes the next cards its holder plays free, spending one charge per card
 * (CostNullifyStatus.cs).
 */
class CombatEngineCostNullifyTest {

    private static final UUID COMBAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final CardInstance PLAYED = new CardInstance("played", "strike");

    @Test
    void makesTheNextCardFreeAndSpendsACharge() {
        CombatResult result = play(2, 1);

        assertThat(result.accepted()).isTrue();
        assertThat(hero(result).energy())
                .describedAs("the card was free")
                .isEqualTo(2);
        assertThat(hero(result).statuses())
                .describedAs("the single charge is spent")
                .noneMatch(status -> "CostNullify".equals(status.statusType()));
        assertThat(result.events()).filteredOn(EnergySpent.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(EnergySpent.class,
                        event -> assertThat(event.amount()).isZero());
    }

    @Test
    void isPlayableWithNoEnergyAtAll() {
        CombatResult result = play(0, 1);

        assertThat(result.accepted())
                .describedAs("a free card does not check energy")
                .isTrue();
        assertThat(enemy(result).hp()).isEqualTo(25);
    }

    @Test
    void keepsSpareChargesForTheNextCard() {
        CombatResult result = play(2, 2);

        assertThat(hero(result).statuses())
                .anyMatch(status -> "CostNullify".equals(status.statusType())
                        && status.value() == 1);
    }

    /** An authored move costs what the calculator says too: free under "Coût nul", charge spent. */
    @Test
    void anAiMoveIsFreeWhenItsCasterCarriesCostNullify() {
        CardDefinition slam = new CardDefinition(
                "slam", 1, TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, 5)));
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 1, 1, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("CostNullify", 1, -1, "", 0)));
        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(11),
                List.of(hero, enemy),
                List.of(new TimelineEntry("enemy", 0, 0), new TimelineEntry("hero", 1, 1)),
                "enemy");
        CombatResult result = new CombatEngine(
                new CardCatalog("v1", Map.of("slam", slam)), new RelicEngine(List.of()))
                .handle(state,
                        new fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand(
                                COMBAT_ID, state.revision(), "enemy", "slam", List.of("hero"), 1));

        assertThat(result.accepted()).isTrue();
        assertThat(enemy(result).energy())
                .describedAs("the move was free")
                .isEqualTo(1);
        assertThat(enemy(result).statuses())
                .describedAs("the charge is spent")
                .noneMatch(status -> "CostNullify".equals(status.statusType()));
        assertThat(result.events()).filteredOn(EnergySpent.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(EnergySpent.class,
                        event -> assertThat(event.amount()).isZero());
    }

    private static CombatResult play(int energy, int charges) {
        CardDefinition strike = new CardDefinition(
                "strike", "Strike", "", 2, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5)));
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, energy, 3, 10,
                new CardPiles(List.of(), List.of(PLAYED), List.of(), List.of()),
                charges > 0
                        ? List.of(new StatusInstance("CostNullify", charges, -1, "", 0))
                        : List.of());
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 2, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)),
                "hero");
        return new CombatEngine(
                new CardCatalog("v1", Map.of("strike", strike)), new RelicEngine(List.of()))
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
}
