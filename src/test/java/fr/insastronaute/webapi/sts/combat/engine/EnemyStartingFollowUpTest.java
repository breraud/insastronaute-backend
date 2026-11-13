package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Un CardFollowUp qu'un ennemi porte dès le départ doit se déclencher sur ses propres coups. */
class EnemyStartingFollowUpTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f0");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String MOVE = "strike";
    private static final String FOLLOW_UP = "baba";

    @Test
    void anEnemysStartingCardFollowUpFiresOnItsOwnMove() {
        CombatResult result = enemyPlays(
                StatusCatalog.instantiate("CardFollowUp", 1, 0, FOLLOW_UP, 0));

        assertThat(playedCardIds(result)).contains(FOLLOW_UP);
    }

    /** Ce que les données écrivent réellement : le nom affiché, pas l'identifiant. */
    @Test
    void theFollowUpCardMayBeNamedRatherThanIdentified() {
        CombatResult result = enemyPlays(
                StatusCatalog.instantiate("CardFollowUp", 1, 0, "Baba", 0));

        assertThat(playedCardIds(result)).contains(FOLLOW_UP);
    }

    private static List<String> playedCardIds(CombatResult result) {
        return result.events().stream()
                .filter(CardPlayed.class::isInstance)
                .map(event -> ((CardPlayed) event).definitionId())
                .toList();
    }

    private static CombatResult enemyPlays(StatusInstance startingStatus) {
        Map<String, CardDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MOVE, new CardDefinition(
                MOVE, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 3))));
        definitions.put(FOLLOW_UP, new CardDefinition(
                FOLLOW_UP, "Baba", null, 0, false, CardType.ATTACK, TargetingMode.SINGLE_ENEMY,
                Set.of(), List.of(), List.of(new EffectDefinition(EffectType.DAMAGE, 5))));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 80, 80, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 40, 40, 0, 0, 0, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(startingStatus), List.of(), null, 0);

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(5),
                List.of(hero, enemy),
                List.of(new TimelineEntry(ENEMY, 0, 12), new TimelineEntry(HERO, 1, 10)),
                ENEMY);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", definitions), new RelicEngine(List.of()));
        return engine.handle(state,
                new AiPlayPatternCommand(COMBAT_ID, 7, ENEMY, MOVE, List.of(HERO), -1));
    }
}
