package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.GCRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * GC, "Fondation solide", read from its own text:
 *
 * <blockquote>À la fin de chaque tour (ennemi ou allié), gagnez 2 d'Armure. L'Armure n'est pas
 * perdue quand vous jouez plusieurs tours à la suite.</blockquote>
 *
 * <p>Both halves are read literally, and the engine used to honour neither. It cleared the armour
 * at the next turn's opening whatever the relic said -- the relic gate was consulted and then
 * overwritten inside the status tick -- and once that was fixed the armour never fell at all,
 * which the relic never promised either: "plusieurs tours à la suite" is a condition, not a
 * decoration.
 *
 * <p>The grant reaches only the owner's own turn ends. Unity fires it on every turn through
 * OnFieldTurnEnd; granting on the enemy's too was tried and judged too strong, and the grant was
 * raised from 2 to 4 in exchange. That is a balance decision, and these tests are where it is
 * written down.
 *
 * <p>And the grant arrives with an event now. It used not to, so a client that replays events
 * rather than polling state never saw the armour appear.
 */
class GCRelicTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000415");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void grantsTwoArmourWhenTheOwnerEndsItsTurn() {
        CombatState state = created();
        CombatResult result = endTurn(state, HERO);

        assertThat(hero(result.state()).armor()).isEqualTo(2);
    }

    @Test
    void announcesThatArmourSoTheClientCanShowIt() {
        CombatState state = created();
        CombatResult result = endTurn(state, HERO);

        assertThat(result.events())
                .as("armour that changes without an event is armour the client never draws")
                .anySatisfy(event -> assertThat(event)
                        .isInstanceOfSatisfying(ArmorGained.class, armor -> {
                            assertThat(armor.targetId()).isEqualTo(HERO);
                            assertThat(armor.appliedArmor()).isEqualTo(2);
                            assertThat(armor.resultingArmor()).isEqualTo(2);
                        }));
    }

    /// "L'Armure n'est pas perdue quand vous jouez plusieurs tours à la suite" -- the case the
    /// condition is for. The enemy here sits a million ticks away and never acts.
    @Test
    void keepsTheArmourAcrossTheOwnersConsecutiveTurns() {
        CombatState state = created();

        state = endTurn(state, HERO).state();
        state = endTurn(state, HERO).state();

        assertThat(hero(state).armor())
                .as("two turns in a row, two grants, and nothing cleared in between")
                .isEqualTo(4);
    }

    /**
     * "À la fin de chaque tour (ennemi ou allié)" -- read literally now. The grant on somebody
     * else's turn end is real, and then the owner's own turn opens on a turn that was not its own,
     * which is what takes the armour away again. Both halves are asserted, because the armour
     * reading zero at the end says nothing on its own about whether the grant happened.
     */
    @Test
    void grantsOnSomebodyElsesTurnEndTooAndThenTheOwnersTurnClearsIt() {
        CombatState state = createdWithAnEnemyThatActs();

        state = endTurn(state, HERO).state();
        assertThat(hero(state).armor())
                .as("the hero's own turn end granted, and the enemy's turn opening left it alone")
                .isEqualTo(2);

        CombatResult enemyTurn = endTurn(state, ENEMY);
        assertThat(enemyTurn.events())
                .as("the enemy's turn end grants the hero its two, as the relic says")
                .anySatisfy(event -> assertThat(event)
                        .isInstanceOfSatisfying(ArmorGained.class, armor -> {
                            assertThat(armor.targetId()).isEqualTo(HERO);
                            assertThat(armor.appliedArmor()).isEqualTo(2);
                        }));

        assertThat(hero(enemyTurn.state()).armor())
                .as("and the hero's turn then opens on a turn that was not its own, so it falls")
                .isZero();
    }

    /** A duel closes half as many turns a round, so a turn is worth twice as much there. */
    @Test
    void aDuelGrantsTheLargerValue() {
        CombatState duel = factory().create(duelSetup()).state();

        assertThat(hero(endTurn(duel, HERO).state()).armor()).isEqualTo(4);
    }

    /// The condition itself, stated on its own: an alternation clears, a chain keeps.
    @Test
    void losesTheArmourWhenSomebodyElsePlayedInBetween() {
        CombatState alternating = createdWithAnEnemyThatActs();
        alternating = endTurn(alternating, HERO).state();
        alternating = endTurn(alternating, ENEMY).state();

        CombatState chained = created();
        chained = endTurn(chained, HERO).state();
        chained = endTurn(chained, HERO).state();

        assertThat(hero(alternating).armor()).isZero();
        assertThat(hero(chained).armor()).isEqualTo(4);
    }

    /** And a combatant without the relic is owed nothing by it. */
    @Test
    void leavesACombatantWithoutTheRelicAlone() {
        CombatState state = createdWithAnEnemyThatActs();

        state = endTurn(state, HERO).state();

        assertThat(combatant(state, ENEMY).armor()).isZero();
    }

    // ------------------------------------------------------------------------ fixtures

    private static CombatState created() {
        return factory().create(setup(1_000_000L)).state();
    }

    private static CombatState createdWithAnEnemyThatActs() {
        return factory().create(setup(1L)).state();
    }

    private static CombatResult endTurn(CombatState state, String actorId) {
        return engine().handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), actorId));
    }

    private static CombatantState hero(CombatState state) {
        return combatant(state, HERO);
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static CombatSetup setup(long enemyReadyAtTick) {
        CombatantSetup hero = new CombatantSetup(
                HERO, "players", ControllerType.HUMAN, 80, 3, 1, 0,
                deck("hero", 40), List.of("GCRelic"));
        CombatantSetup enemy = new CombatantSetup(
                ENEMY, "enemies", ControllerType.AI, 30, 2, 1_000_000, enemyReadyAtTick,
                List.of());
        return new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero, enemy));
    }

    /** Deux humains, aucun siege tenu par la machine : le format du duel. */
    private static CombatSetup duelSetup() {
        CombatantSetup hero = new CombatantSetup(
                HERO, "players", ControllerType.HUMAN, 80, 3, 1, 0,
                deck("hero", 40), List.of("GCRelic"));
        CombatantSetup rival = new CombatantSetup(
                ENEMY, "enemies", ControllerType.HUMAN, 80, 3, 1_000_000, 1_000_000L,
                deck("rival", 40), List.of());
        return new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero, rival));
    }

    private static CombatFactory factory() {
        return new CombatFactory(catalog(), new RelicEngine(List.of(new GCRelicHandler())));
    }

    private static CombatEngine engine() {
        return new CombatEngine(catalog(), new RelicEngine(List.of(new GCRelicHandler())));
    }

    private static CardCatalog catalog() {
        return new CardCatalog("v1", Map.of("strike", strike()));
    }

    private static List<CardInstance> deck(String prefix, int size) {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            deck.add(new CardInstance(prefix + "-" + index, "strike"));
        }
        return deck;
    }

    private static CardDefinition strike() {
        return new CardDefinition(
                "strike", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, java.util.Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1, false, null, 0, "", null)));
    }
}
