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
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.GMRelicHandler;
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
 * L'effet Break réveille les reliques, comme le fait un coup qui emporte la dernière armure.
 *
 * <p>Il ne les réveillait pas : il annonçait l'événement et s'arrêtait là, si bien que la relique
 * du Grand Magistrat répondait à l'épée et jamais à l'effet qui porte son nom.
 */
class BreakEffectReachesRelicsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b4");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String CARD = "shatter";
    private static final long ENEMY_TICK = 12;

    @Test
    void theBreakEffectDelaysTheTargetThroughTheGrandMagistratsRelic() {
        CombatResult result = playBreak(20);

        assertThat(tickOf(result, ENEMY))
                .describedAs("l'armure brisée par l'effet retarde son porteur de 10")
                .isEqualTo(ENEMY_TICK + 10);
    }

    /** Rien à briser, rien à annoncer : la relique n'a pas à répondre. */
    @Test
    void anUnarmouredTargetIsLeftWhereItStands() {
        CombatResult result = playBreak(0);

        assertThat(tickOf(result, ENEMY)).isEqualTo(ENEMY_TICK);
    }

    private static long tickOf(CombatResult result, String combatantId) {
        return result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .readyAtTick();
    }

    private static CombatResult playBreak(int enemyArmor) {
        CardDefinition definition = new CardDefinition(
                CARD, 1, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.BREAK, 1)));
        CardInstance card = new CardInstance("played", CARD);

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 80, 80, 0, 3, 3, 25,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                List.of(), List.of("GMRelic"), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 40, 40, enemyArmor, 0, 0, 25,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(11),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, ENEMY_TICK, 1)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of(new GMRelicHandler())));
        return engine.handle(state,
                new PlayCardCommand(COMBAT_ID, 7, HERO, card.instanceId(), List.of(ENEMY)));
    }
}
