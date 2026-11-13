package fr.insastronaute.webapi.sts.combat.ai;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
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
 * An enemy plays what its pattern says, whatever the card costs.
 *
 * <p>Energy is the player's economy. Every enemy is built with none of it, so gating a pattern
 * move on affording it could only ever answer the same way for a given enemy -- and which way
 * depended on how the move happened to be written. A move declared inline becomes a definition of
 * its own priced at zero, so it always passed. A move naming a card gets that card, player price
 * and all, so it always failed.
 *
 * <p>The failure was permanent rather than occasional: ending a turn leaves {@code patternIndex}
 * untouched, so the same unaffordable move came up again on the next turn, and the next, for the
 * rest of the fight. Eight of the fifty-four authored enemies write their moves that way,
 * Ophiucus, Regent, Nemesis and all three Elites among them.
 */
class PatternEnemyEnergyTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000001177");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy-0";

    @Test
    void playsAMoveItCouldNeverAffordOutOfItsOwnEnergy() {
        CombatCommand command = decide(costingTwo());

        assertThat(command)
                .as("an enemy with no energy still throws its two-cost move")
                .isInstanceOf(AiPlayPatternCommand.class);
    }

    @Test
    void namesTheCardItsPatternPointsAt() {
        AiPlayPatternCommand command = (AiPlayPatternCommand) decide(costingTwo());

        assertThat(command.cardId()).isEqualTo("katana");
    }

    /** And it steps the pattern on, so the next turn is a different move. */
    @Test
    void stepsThePatternForward() {
        AiPlayPatternCommand command = (AiPlayPatternCommand) decide(costingTwo());

        assertThat(command.nextPatternIndex())
                .as("a pattern of one wraps back to itself; the point is that it was chosen")
                .isNotNegative();
    }

    /** A free move behaves exactly as it always did, which is what must not regress. */
    @Test
    void stillPlaysAFreeMove() {
        assertThat(decide(free())).isInstanceOf(AiPlayPatternCommand.class);
    }

    private static CardDefinition costingTwo() {
        return new CardDefinition(
                "katana", "Frappe au katana", "", 2, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 6, false)));
    }

    private static CardDefinition free() {
        return new CardDefinition(
                "katana", "Coup libre", "", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(new EffectDefinition(EffectType.DAMAGE, 6, false)));
    }

    private static CombatCommand decide(CardDefinition move) {
        CardCatalog catalog = new CardCatalog("v1", Map.of(move.id(), move));
        PatternEnemyPolicy policy = new PatternEnemyPolicy(
                catalog,
                Map.of(ENEMY, List.of(new PatternEnemyPolicy.EnemyMoveNode(
                        move.id(), List.of(), 1))));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        // Built the way the run builds one: no energy at all.
        CombatantState enemy = new CombatantState(
                ENEMY, "enemy", ControllerType.AI, 30, 30, 0, 0, 0, 200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(enemy, hero),
                List.of(new TimelineEntry(ENEMY, 0, 0), new TimelineEntry(HERO, 100, 1)),
                ENEMY, CombatCounters.initial());

        return policy.nextCommand(state);
    }
}
