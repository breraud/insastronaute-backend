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
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
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

class CombatEngineInfaillibleTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f4");

    @Test
    void turnsAFailedConditionIntoAResolvedEffectAndSpendsOneCharge() {
        CombatResult result = play(2, false);

        assertThat(enemy(result).hp()).isEqualTo(25);
        assertThat(status(result, "Infaillible").value()).isEqualTo(1);
    }

    @Test
    void leavesInfaillibleUntouchedWhenTheConditionWasAlreadyTrue() {
        CombatResult result = play(2, true);

                assertThat(enemy(result).hp()).isEqualTo(26);
        assertThat(status(result, "Infaillible").value()).isEqualTo(2);
    }

    private static CombatResult play(int charges, boolean targetHasArmor) {
        CardDefinition strike = new CardDefinition(
                "certain-strike", "Certain strike", "", 0, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(new EffectDefinition(
                        EffectType.DAMAGE, 5, false, null, 0, null, null, null, null, List.of(),
                        false, false, new EffectCondition(
                                ConditionType.TARGET_ARMOR_THRESHOLD, "1"), 0)));
        CombatantState hero = new CombatantState(
                "hero", "players", ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("held", "certain-strike")), List.of(), List.of()),
                List.of(new StatusInstance("Infaillible", charges, -1, "", 0)));
        CombatantState enemy = new CombatantState(
                "enemy", "enemies", ControllerType.AI, 30, 30, targetHasArmor ? 1 : 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatState state = new CombatState(
                COMBAT_ID, 2, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy", 1, 1)), "hero");
        return new CombatEngine(new CardCatalog("v1", Map.of("certain-strike", strike)),
                new RelicEngine(List.of())).handle(state,
                new PlayCardCommand(COMBAT_ID, state.revision(), "hero", "held", List.of("enemy")));
    }

    private static CombatantState enemy(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("enemy"))
                .findFirst().orElseThrow();
    }

    private static StatusInstance status(CombatResult result, String statusType) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst().orElseThrow().statuses().stream()
                .filter(status -> status.statusType().equals(statusType))
                .findFirst().orElseThrow();
    }
}
