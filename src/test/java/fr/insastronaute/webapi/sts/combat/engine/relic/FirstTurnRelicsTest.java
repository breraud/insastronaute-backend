package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.CrownOfAshesRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.EPRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.PERFRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A relic that fires at the start of every turn must fire on the first one too.
 *
 * <p>The first turn is the one turn no {@code handTurnOn} ever opens: {@link CombatFactory} deals
 * its hand and sets its energy itself. It used to call only {@code onCombatStart}, so every
 * per-turn relic sat out the turn the player actually notices most — EP's own description reads
 * "au début de chaque tour", and its owner opened on the bare 5 cards and 3 energy of somebody
 * with no relic at all.
 */
class FirstTurnRelicsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000414");

    /** EP: "Au début de chaque tour, gagnez 1 d'énergie et piochez une carte." */
    @Test
    void epGrantsItsEnergyAndItsCardOnTheOpeningTurn() {
        CombatantState hero = openWith("EPRelic");

        assertThat(hero.energy())
                .as("baseEnergy 3, +1 from EP")
                .isEqualTo(4);
        assertThat(hero.piles().hand())
                .as("five dealt, plus EP's own draw")
                .hasSize(6);
    }

    /** PERF: "Gagne 1 énergie en moins par tour." */
    @Test
    void perfTakesItsEnergyBackOnTheOpeningTurn() {
        CombatantState hero = openWith("PERFRelic");

        assertThat(hero.energy()).isEqualTo(2);
    }

    /**
     * The regression this fix could have caused. Crown of Ashes grants one energy per turn for
     * three turns and counts them in its own memory; it used to split that count across
     * {@code onCombatStart} and {@code onTurnStart} precisely because the opening turn reached
     * only the first. With both hooks now firing on that turn, a relic that kept the split would
     * hand out two energies and burn two of its three turns at once.
     */
    @Test
    void crownOfAshesStillGrantsExactlyOneEnergyOnTheOpeningTurn() {
        CombatantState hero = openWith("CrownOfAshesRelic");

        assertThat(hero.energy()).isEqualTo(4);
        assertThat(hero.recall("CrownOfAshesRelic", "turnsGranted"))
                .as("one of the three turns spent, not two")
                .isEqualTo(1);
    }

    /** The combatant who does not open keeps its turn-start relic for its own turn. */
    @Test
    void aRelicOfTheCombatantWhoDoesNotOpenStaysQuiet() {
        CombatCreated created = create("EPRelic", "EPRelic");
        CombatantState waiting = created.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("enemy"))
                .findFirst()
                .orElseThrow();

        assertThat(waiting.energy())
                .as("its turn has not opened, so nothing of its turn start has run")
                .isZero();
        assertThat(waiting.piles().hand()).isEmpty();
    }

    // ------------------------------------------------------------------------ fixtures

    private static CombatantState openWith(String relicId) {
        return create(relicId, null).state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("hero"))
                .findFirst()
                .orElseThrow();
    }

    private static CombatCreated create(String heroRelic, String enemyRelic) {
        RelicEngine relicEngine = new RelicEngine(List.of(
                new EPRelicHandler(),
                new PERFRelicHandler(),
                new CrownOfAshesRelic()));
        CardCatalog catalog = new CardCatalog("v1", Map.of("strike", strike()));
        CombatFactory factory = new CombatFactory(catalog, relicEngine);

        CombatantSetup hero = new CombatantSetup(
                "hero", "players", ControllerType.HUMAN, 80, 3, 10, 0,
                deck("hero", 20), List.of(heroRelic));
        CombatantSetup enemy = new CombatantSetup(
                "enemy", "enemies", ControllerType.AI, 30, 0, 12, 100,
                deck("enemy", 5),
                enemyRelic == null ? List.of() : List.of(enemyRelic));

        return factory.create(new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero, enemy)));
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
                "strike", 1, fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, java.util.Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1, false, null, 0, "", null)));
    }
}
