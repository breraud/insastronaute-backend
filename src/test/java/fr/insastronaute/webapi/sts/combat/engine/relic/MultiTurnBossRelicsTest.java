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
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.CrownOfAshesRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SolarCageRelic;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
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
 * The two relics whose combat-start behaviour keeps acting turn after turn: {@code
 * CrownOfAshesRelic}'s energy bonus over the owner's first three turns, and {@code
 * SolarCageRelic}'s delayed damage at the end of the owner's first turn.
 *
 * <p>Driven end to end: {@link CombatFactory} creates the combat, then repeated {@link
 * EndTurnCommand}s advance it through {@link CombatEngine}, exactly as a real game would. The
 * hero is given a short {@code turnDelayTicks} and the enemy an unreachably distant {@code
 * initialReadyAtTick}, so the hero simply keeps acting turn after turn without the enemy ever
 * needing to play.
 */
class MultiTurnBossRelicsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000132");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void crownOfAshesGrantsEnergyOnlyOnTheOwnersFirstThreeTurns() {
        CombatFactory factory = factory(new CrownOfAshesRelic());
        CombatEngine engine = engine(new CrownOfAshesRelic());

        CombatCreated created = factory.create(setup("CrownOfAshesRelic"));
        CombatState state = created.state();

        // Combat start stands in for the owner's first turn: nothing resets energy or armour
        // between CombatFactory's initial deal and onCombatStart running, unlike Unity's own
        // turn loop, so the +1 granted here already covers turn one.
        assertThat(hero(state).energy()).isEqualTo(4);

        state = endTurn(engine, state, HERO);
        assertThat(hero(state).energy()).isEqualTo(4); // owner's turn two

        state = endTurn(engine, state, HERO);
        assertThat(hero(state).energy()).isEqualTo(4); // owner's turn three

        state = endTurn(engine, state, HERO);
        assertThat(hero(state).energy()).isEqualTo(3); // owner's turn four: no more bonus
    }

    @Test
    void solarCageDealsTenDamageAtTheEndOfTheOwnersFirstTurnOnly() {
        CombatFactory factory = factory(new SolarCageRelic());
        CombatEngine engine = engine(new SolarCageRelic());

        CombatCreated created = factory.create(setup("SolarCageRelic"));
        CombatState state = created.state();
        assertThat(hero(state).hp()).isEqualTo(80);

        state = endTurn(engine, state, HERO);
        assertThat(hero(state).hp()).isEqualTo(70);

        state = endTurn(engine, state, HERO);
        assertThat(hero(state).hp()).isEqualTo(70);
    }

    private static CombatState endTurn(CombatEngine engine, CombatState state, String actorId) {
        CombatResult result = engine.handle(
                state, new EndTurnCommand(COMBAT_ID, state.revision(), actorId));
        assertThat(result.accepted()).isTrue();
        return result.state();
    }

    private static CombatantState hero(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CombatSetup setup(String relicId) {
        CombatantSetup hero = new CombatantSetup(
                HERO, "players", ControllerType.HUMAN, 80, 3, 1, 0,
                deck("hero", 40), List.of(relicId));
        CombatantSetup enemy = new CombatantSetup(
                ENEMY, "enemies", ControllerType.AI, 30, 2, 1_000_000, 1_000_000, List.of());
        return new CombatSetup(COMBAT_ID, "v1", 42L, List.of(hero, enemy));
    }

    private static CombatFactory factory(fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler relic) {
        return new CombatFactory(catalog(), new RelicEngine(List.of(relic)));
    }

    private static CombatEngine engine(fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler relic) {
        return new CombatEngine(catalog(), new RelicEngine(List.of(relic)));
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
