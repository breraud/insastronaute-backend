package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.InfernalContractRelic;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * « Au début du combat, gagnez 2 énergie et piochez 2 cartes. »
 *
 * <p>Au début du combat, une fois : ni à chaque tour, ni jamais.
 */
class InfernalContractRelicTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String CARD = "frappe";
    private static final int BASE_ENERGY = 3;
    private static final int OPENING_HAND = 5;

    @Test
    void itDrawsTwoCardsOnTopOfTheOpeningHand() {
        CombatState state = created().state();

        assertThat(combatant(state, HERO).piles().hand())
                .describedAs("cinq cartes distribuées plus les deux du contrat")
                .hasSize(OPENING_HAND + 2);
    }

    @Test
    void itGrantsTwoEnergyOnTheOpeningTurn() {
        CombatState state = created().state();

        assertThat(combatant(state, HERO).energy()).isEqualTo(BASE_ENERGY + 2);
    }

    /** L'énergie du contrat est celle du premier tour, pas une rente. */
    @Test
    void itDoesNotGrantEnergyAgainOnLaterTurns() {
        CombatState state = created().state();
        CombatEngine engine = engine();

        // Le tour du héros se ferme, celui de l'ennemi aussi : la main revient au héros.
        state = engine.handle(state, new EndTurnCommand(
                COMBAT_ID, state.revision(), HERO)).state();
        while (!HERO.equals(state.activeCombatantId())) {
            state = engine.handle(state, new EndTurnCommand(
                    COMBAT_ID, state.revision(), state.activeCombatantId())).state();
        }

        assertThat(combatant(state, HERO).energy())
                .describedAs("le deuxième tour s'ouvre sur la dotation de base")
                .isEqualTo(BASE_ENERGY);
    }

    @Test
    void itPoisonsItsBearerOnce() {
        CombatState state = created().state();

        assertThat(combatant(state, HERO).statuses())
                .filteredOn(status -> "Poison".equals(status.statusType()))
                .hasSize(1);
    }

    private static CombatCreated created() {
        return new CombatFactory(catalog(), relicEngine()).create(new CombatSetup(
                COMBAT_ID, "v1", 99L,
                List.of(
                        new CombatantSetup(HERO, "players", ControllerType.HUMAN, 60, BASE_ENERGY,
                                10, 0, deck(), List.of("InfernalContractRelic"), List.of(), 0),
                        new CombatantSetup(ENEMY, "enemies", ControllerType.AI, 60, 0,
                                10, 10, deck(), List.of(), List.of(), 0))));
    }

    private static CombatEngine engine() {
        return new CombatEngine(catalog(), relicEngine());
    }

    private static RelicEngine relicEngine() {
        return new RelicEngine(List.of(new InfernalContractRelic()));
    }

    private static CardCatalog catalog() {
        CardDefinition strike = new CardDefinition(
                CARD, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 2)));
        return new CardCatalog("v1", Map.of(strike.id(), strike));
    }

    private static List<CardInstance> deck() {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            deck.add(new CardInstance(UUID.randomUUID().toString(), CARD));
        }
        return deck;
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}
