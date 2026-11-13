package fr.insastronaute.webapi.sts.combat.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SingleCardEnemyPolicyTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final CardInstance EXPENSIVE = new CardInstance("expensive-1", "expensive");
    private static final CardInstance STRIKE = new CardInstance("strike-1", "strike");
    private static final CardInstance DEFEND = new CardInstance("defend-1", "defend");

    private final CardCatalog catalog = new CardCatalog("v1", Map.of(
            "expensive", definition("expensive", 4, TargetingMode.SINGLE_ENEMY),
            "strike", definition("strike", 1, TargetingMode.SINGLE_ENEMY),
            "defend", definition("defend", 1, TargetingMode.SELF)));
    private final SingleCardEnemyPolicy policy = new SingleCardEnemyPolicy(catalog);

    @Test
    void choosesFirstAffordableCardAndFirstLivingOpponentInCombatantOrder() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                2,
                List.of(EXPENSIVE, STRIKE, DEFEND));

        CombatCommand command = policy.nextCommand(state);

        assertThat(command).isEqualTo(new PlayCardCommand(
                COMBAT_ID, 12, "enemy", STRIKE.instanceId(), List.of("hero")));
        assertThat(state).isEqualTo(state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                2,
                List.of(EXPENSIVE, STRIKE, DEFEND)));
        assertThat(state.random()).isEqualTo(new RandomState(99));
    }

    @Test
    void targetsTheActiveAiForASelfCard() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                1,
                List.of(DEFEND));

        assertThat(policy.nextCommand(state)).isEqualTo(new PlayCardCommand(
                COMBAT_ID, 12, "enemy", DEFEND.instanceId(), List.of("enemy")));
    }

    @Test
    void endsTurnWhenNoCardIsPlayable() {
        CombatState state = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                0,
                List.of(STRIKE, EXPENSIVE));

        assertThat(policy.nextCommand(state))
                .isEqualTo(new EndTurnCommand(COMBAT_ID, 12, "enemy"));
    }

    @Test
    void failsFastUnlessTheActiveCombatantIsALivingAiInAnActiveCombat() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.nextCommand(state(
                CombatStatus.FINISHED,
                ControllerType.AI,
                10,
                1,
                List.of(STRIKE))));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.nextCommand(state(
                CombatStatus.ACTIVE,
                ControllerType.HUMAN,
                10,
                1,
                List.of(STRIKE))));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.nextCommand(state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                0,
                1,
                List.of(STRIKE))));
    }

    @Test
    void failsFastForUnknownCardDefinitionsAndInvalidInputs() {
        CombatState unknownCard = state(
                CombatStatus.ACTIVE,
                ControllerType.AI,
                10,
                1,
                List.of(new CardInstance("unknown-1", "unknown")));

        assertThatIllegalStateException().isThrownBy(() -> policy.nextCommand(unknownCard));
        assertThatNullPointerException().isThrownBy(() -> new SingleCardEnemyPolicy(null));
        assertThatNullPointerException().isThrownBy(() -> policy.nextCommand(null));
    }

    private static CombatState state(
            CombatStatus status,
            ControllerType activeController,
            int activeHp,
            int activeEnergy,
            List<CardInstance> hand
    ) {
        CombatantState deadOpponent = combatant(
                "fallen", "players", ControllerType.HUMAN, 0, 0, List.of());
        int opposingHp = status == CombatStatus.FINISHED ? 0 : 20;
        CombatantState hero = combatant(
                "hero", "players", ControllerType.HUMAN, opposingHp, 3, List.of());
        CombatantState otherHero = combatant(
                "other-hero", "players", ControllerType.HUMAN, opposingHp, 3, List.of());
        CombatantState enemy = combatant(
                "enemy", "enemies", activeController, activeHp, activeEnergy, hand);
        return new CombatState(
                COMBAT_ID,
                12,
                "v1",
                status,
                status == CombatStatus.FINISHED ? "enemies" : null,
                new RandomState(99),
                List.of(deadOpponent, hero, otherHero, enemy),
                List.of(
                        new TimelineEntry("enemy", 0, 0),
                        new TimelineEntry("hero", 1, 1),
                        new TimelineEntry("other-hero", 2, 2)),
                status == CombatStatus.ACTIVE ? "enemy" : null);
    }

    private static CombatantState combatant(
            String id,
            String teamId,
            ControllerType controller,
            int hp,
            int energy,
            List<CardInstance> hand
    ) {
        return new CombatantState(
                id,
                teamId,
                controller,
                20,
                hp,
                0,
                energy,
                3,
                10,
                new CardPiles(List.of(), hand, List.of(), List.of()));
    }

    private static CardDefinition definition(
            String id,
            int cost,
            TargetingMode targetingMode
    ) {
        return new CardDefinition(
                id,
                cost,
                targetingMode,
                List.of(new EffectDefinition(EffectType.DAMAGE, 1)));
    }
}
