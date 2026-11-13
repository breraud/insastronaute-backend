package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CFIRelicHandlerTest {

    private static final String RELIC = "CFIRelic";

    @Test
    void testId() {
        CFIRelicHandler handler = new CFIRelicHandler();
        assertThat(handler.id()).isEqualTo(RELIC);
    }

    @Test
    void grantsVigorToTheOwnerWhenAnEnemyGainsArmor() {
        CFIRelicHandler handler = new CFIRelicHandler();
        CombatantState owner = human("hero", "hero-team", List.of(RELIC));
        CombatantState enemy = enemy("slime", "enemy-team");

        List<CombatantState> result =
                handler.onArmorGain(owner, enemy, 7, List.of(owner, enemy), new ArrayList<>());

        assertThat(vigor(result, "hero")).isEqualTo(3);
        assertThat(vigor(result, "slime")).isZero();
    }

    @Test
    void doesNothingWhenTheOwnerGainsTheirOwnArmor() {
        CFIRelicHandler handler = new CFIRelicHandler();
        CombatantState owner = human("hero", "hero-team", List.of(RELIC));
        CombatantState enemy = enemy("slime", "enemy-team");

        List<CombatantState> result =
                handler.onArmorGain(owner, owner, 10, List.of(owner, enemy), new ArrayList<>());

        assertThat(vigor(result, "hero")).isZero();
    }

    /** En duel, l'armure de l'adversaire déclenche, et la Vigueur ne va qu'au porteur. */
    @Test
    void inADuelOnlyTheBearerIsPaidAndOnlyTheOpponentTriggers() {
        CFIRelicHandler handler = new CFIRelicHandler();
        CombatantState owner = human("hero", "hero-team", List.of(RELIC));
        CombatantState rival = human("rival", "rival-team", List.of());

        List<CombatantState> result =
                handler.onArmorGain(owner, rival, 8, List.of(owner, rival), new ArrayList<>());

        assertThat(vigor(result, "hero")).isEqualTo(4);
        assertThat(vigor(result, "rival")).isZero();
    }

    @Test
    void addsToVigorAlreadyStanding() {
        CFIRelicHandler handler = new CFIRelicHandler();
        CombatantState owner = new CombatantState(
                "hero", "hero-team", ControllerType.HUMAN, 50, 50, 0, 3, 3, 100,
                piles(), List.of(new StatusInstance("Vigor", 2, -1, null, 0)), List.of(RELIC), null, 0);
        CombatantState enemy = enemy("slime", "enemy-team");

        List<CombatantState> result =
                handler.onArmorGain(owner, enemy, 6, List.of(owner, enemy), new ArrayList<>());

        assertThat(vigor(result, "hero")).isEqualTo(5);
        assertThat(statuses(result, "hero")).hasSize(1);
    }

    /**
     * Le porteur ne gagne pas l'armure : c'est l'ennemi qui la gagne. Sans un dispatch qui
     * interroge tous les porteurs, le crochet n'était jamais atteint.
     */
    @Test
    void engineReachesTheBearerWhenSomebodyElseGainsTheArmor() {
        RelicEngine engine = new RelicEngine(List.of(new CFIRelicHandler()));
        CombatantState owner = human("hero", "hero-team", List.of(RELIC));
        CombatantState enemy = enemy("slime", "enemy-team");

        List<CombatantState> result =
                engine.processArmorGain(enemy, 9, List.of(owner, enemy), new ArrayList<>());

        assertThat(vigor(result, "hero")).isEqualTo(4);
    }

    private static int vigor(List<CombatantState> combatants, String combatantId) {
        return statuses(combatants, combatantId).stream()
                .filter(status -> "Vigor".equals(status.statusType()))
                .mapToInt(StatusInstance::value)
                .sum();
    }

    private static List<StatusInstance> statuses(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .statuses();
    }

    private static CombatantState human(String id, String teamId, List<String> relics) {
        return new CombatantState(
                id, teamId, ControllerType.HUMAN, 50, 50, 0, 3, 3, 100,
                piles(), List.of(), relics, null, 0);
    }

    private static CombatantState enemy(String id, String teamId) {
        return new CombatantState(
                id, teamId, ControllerType.AI, 30, 30, 0, 0, 0, 100,
                piles(), List.of(), List.of(), null, 0);
    }

    private static CardPiles piles() {
        return new CardPiles(List.of(), List.of(), List.of(), List.of());
    }
}
