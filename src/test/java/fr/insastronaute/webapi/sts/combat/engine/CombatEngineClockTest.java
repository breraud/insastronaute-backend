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
 * L'Horloge : chaque coup porté repousse les tours de qui l'a pris.
 *
 * <p>Elle n'était déclarée qu'au catalogue et n'agissait donc nulle part. Le pourcentage est celui
 * que la carte annonce — {@code Value × 2} — pris sur le délai de la cible, comme l'effet
 * DelayTurn lit le sien.
 */
class CombatEngineClockTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final int ENEMY_DELAY = 100;
    private static final long ENEMY_TICK = 500;
    private static final int PERMANENT = -1;

    /** Horloge (3) : 6% du délai de la cible, soit 6 crans sur 100. */
    @Test
    void aClockPushesTheStruckTurnBackByTwiceItsValueInPercent() {
        CombatResult result = play(damage(5), clock(3));

        assertThat(tickOf(result, ENEMY) - ENEMY_TICK).isEqualTo(6);
    }

    @Test
    void nothingMovesWithoutAClock() {
        assertThat(tickOf(play(damage(5), List.of()), ENEMY)).isEqualTo(ENEMY_TICK);
    }

    /** Elle répond au coup et non à la carte : trois frappes retardent trois fois. */
    @Test
    void aMultihitPushesOncePerBlow() {
        CardDefinition threeBlows = new CardDefinition(
                "card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.MULTIHIT, 5, false, null, 3, null, null)));

        assertThat(tickOf(play(threeBlows, clock(3)), ENEMY) - ENEMY_TICK).isEqualTo(18);
    }

    /** Ce qu'elle compte est le coup porté, pas les PV tombés : l'armure ne l'arrête pas. */
    @Test
    void anArmouredTargetIsStillPushedBack() {
        CombatResult result = play(damage(5), clock(3), 50);

        assertThat(tickOf(result, ENEMY) - ENEMY_TICK).isEqualTo(6);
        assertThat(enemy(result).hp())
                .describedAs("l'armure a tout mangé, et le tour recule quand même")
                .isEqualTo(200);
    }

    /** Deux Horloges s'ajoutent, chacune la sienne. */
    @Test
    void twoClocksAddUp() {
        List<StatusInstance> two = List.of(
                new StatusInstance("Clock", 3, PERMANENT, null, 0),
                new StatusInstance("Clock", 2, PERMANENT, null, 0));

        assertThat(tickOf(play(damage(5), two), ENEMY) - ENEMY_TICK)
                .describedAs("6% et 4% font 10%")
                .isEqualTo(10);
    }

    /** Une carte qui ne frappe pas ne retarde rien. */
    @Test
    void aCardThatStrikesNobodyPushesNothing() {
        CardDefinition armourCard = new CardDefinition(
                "card", 1, CardType.SKILL, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 5)));

        assertThat(tickOf(play(armourCard, clock(3)), ENEMY)).isEqualTo(ENEMY_TICK);
    }

    // --- montage -------------------------------------------------------------------------------

    private static List<StatusInstance> clock(int value) {
        return List.of(new StatusInstance("Clock", value, PERMANENT, null, 0));
    }

    private static CardDefinition damage(int amount) {
        return new CardDefinition(
                "card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, amount)));
    }

    private static CombatantState enemy(CombatResult result) {
        return result.state().combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(ENEMY))
                .findFirst()
                .orElseThrow();
    }

    private static long tickOf(CombatResult result, String combatantId) {
        return result.state().timeline().stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .mapToLong(TimelineEntry::readyAtTick)
                .min()
                .orElseThrow();
    }

    private static CombatResult play(CardDefinition definition, List<StatusInstance> heroStatuses) {
        return play(definition, heroStatuses, 0);
    }

    private static CombatResult play(
            CardDefinition definition, List<StatusInstance> heroStatuses, int enemyArmor) {
        CardInstance card = new CardInstance("played", definition.id());

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 80, 80, 0, 3, 3, 20,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                heroStatuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 200, 200, enemyArmor, 0, 0, ENEMY_DELAY,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(4),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, ENEMY_TICK, 1)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        return engine.handle(state,
                new PlayCardCommand(COMBAT_ID, 7, HERO, card.instanceId(), List.of(ENEMY)));
    }
}
