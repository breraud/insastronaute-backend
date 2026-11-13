package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A multihit strikes the same target once per hit.
 *
 * <p>Unity flattens a multihit into one damage effect per hit, each aimed at the effect's own
 * targets ({@code CombatManager.cs:1532-1544}). The handler used to read the next target off the
 * head of the combatant roster, which is the player: every hit after the first landed on the
 * caster, and the first hit never reached the state at all.
 */
class MultihitHandlerTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final CardInstance PLAYED_CARD = new CardInstance("played", "tested-card");

    @Test
    void landsEveryHitOnTheTargetAndNoneOnTheCaster() {
        CombatResult result = play(state(20, 0));

        assertThat(combatant(result, ENEMY).hp()).isEqualTo(8);
        assertThat(combatant(result, HERO).hp()).isEqualTo(30);
    }

    @Test
    void takesArmorOffOnceHitAfterHitRatherThanAgainstTheFullBlow() {
        CombatResult result = play(state(20, 5));

        CombatantState enemy = combatant(result, ENEMY);
        assertThat(enemy.armor()).isZero();
        assertThat(enemy.hp()).isEqualTo(13);
    }

    @Test
    void firesEveryHitEvenOnceTheTargetIsDown() {
        CombatResult result = play(state(5, 0));

        long blows = result.events().stream()
                .filter(event -> event instanceof fr.insastronaute.webapi.sts.combat.event.DamageApplied)
                .count();
        assertThat(blows).isEqualTo(3);
    }

    private static CombatResult play(CombatState state) {
        CardDefinition definition = new CardDefinition(
                "tested-card",
                1,
                TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(
                        EffectType.MULTIHIT, 4, false, null, 3, "", null)));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        CombatResult result = engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        HERO,
                        PLAYED_CARD.instanceId(),
                        List.of(ENEMY)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState state(int enemyHp, int enemyArmor) {
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                10,
                new CardPiles(List.of(), List.of(PLAYED_CARD), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                enemyHp,
                enemyArmor,
                2,
                2,
                12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1, 1)),
                HERO);
    }
}
