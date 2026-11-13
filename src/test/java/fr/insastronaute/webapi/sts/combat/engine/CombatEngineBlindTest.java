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
import fr.insastronaute.webapi.sts.combat.engine.status.StatusBehaviours;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
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
 * Aveuglé compte des coups annulés, non des tours.
 *
 * <p>C'est ce qui le distingue d'Étourdissement ou de Divination : sa durée ne bouge que
 * lorsqu'un coup est effectivement réduit à néant, et pas au rythme de l'horloge.
 */
class CombatEngineBlindTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String CARD = "tested-card";

    @Test
    void aBlindedBlowDealsNothingAndSpendsOneCharge() {
        CombatResult result = play(2, 8, 0);

        assertThat(combatant(result, ENEMY).hp()).isEqualTo(20);
        assertThat(blindLeft(result)).isEqualTo(1);
    }

    /** Chaque instance de dégâts d'un Multihit consomme sa propre charge. */
    @Test
    void aMultihitSpendsOneChargePerBlowAndThenLandsTheRest() {
        CombatResult result = play(2, 5, 4);

        assertThat(blindLeft(result)).isNull();
        // Deux coups annulés, deux qui passent.
        assertThat(combatant(result, ENEMY).hp()).isEqualTo(10);
    }

    /** Une charge est dépensée même quand l'armure aurait tout bloqué de toute façon. */
    @Test
    void anArmouredTargetStillCostsACharge() {
        CombatResult result = play(2, 8, 0, 30);

        assertThat(blindLeft(result)).isEqualTo(1);
    }

    @Test
    void blindDoesNotRunDownWithTheTurnClock() {
        StatusInstance blind = new StatusInstance("Blind", 0, 2, null, 0);

        assertThat(StatusBehaviours.atTurnEnd(blind).ticks()).isFalse();
        assertThat(StatusBehaviours.atTurnStart(blind).ticks()).isFalse();
    }

    /** Signal vient d'une carte de Puissance : la durée qu'on lui écrit ne le retient pas. */
    @Test
    void signalIsPermanentWhateverDurationIsAuthored() {
        assertThat(StatusCatalog.instantiate("Signal", 3, 3, null, 0).duration()).isEqualTo(-1);
    }

    private static Integer blindLeft(CombatResult result) {
        return combatant(result, HERO).statuses().stream()
                .filter(status -> "Blind".equals(status.statusType()))
                .map(StatusInstance::duration)
                .findFirst()
                .orElse(null);
    }

    private static CombatResult play(int blindCharges, int damage, int hits) {
        return play(blindCharges, damage, hits, 0);
    }

    /** @param hits 0 pour un simple coup, sinon un Multihit de ce nombre de frappes */
    private static CombatResult play(int blindCharges, int damage, int hits, int enemyArmor) {
        EffectDefinition attack = hits > 0
                ? new EffectDefinition(EffectType.MULTIHIT, damage, false, null, hits, null, null)
                : new EffectDefinition(EffectType.DAMAGE, damage);
        CardDefinition definition = new CardDefinition(
                CARD, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(), List.of(attack));
        CardInstance card = new CardInstance("played", CARD);

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 30, 20, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                List.of(new StatusInstance("Blind", 0, blindCharges, null, 0)),
                List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 20, 20, enemyArmor, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 10), new TimelineEntry(ENEMY, 1, 12)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        return engine.handle(state,
                new PlayCardCommand(COMBAT_ID, 7, HERO, card.instanceId(), List.of(ENEMY)));
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}
