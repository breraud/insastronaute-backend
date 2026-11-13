package fr.insastronaute.webapi.sts.combat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PowerPassiveEngineTest {

    private static final UUID COMBAT_ID = UUID.randomUUID();
    private final PowerPassiveEngine engine = new PowerPassiveEngine(null);

    @Test
    void firstAttackTriggersDisciplineOnlyOnceUntilNextTurn() {
        CombatantState owner = combatant("player", "players", power("discipline"));
        CardDefinition attack = new CardDefinition("attack", 1, CardType.ATTACK,
                TargetingMode.NONE, Set.of(), List.of());
        CardPlayed played = new CardPlayed(COMBAT_ID, 1, "player", "card", "attack", List.of());

        PowerPassiveEngine.Result first = engine.onCardPlayed(
            COMBAT_ID, 1, played, attack, null, List.of(owner));
        PowerPassiveEngine.Result second = engine.onCardPlayed(
            COMBAT_ID, 1, played, attack, null, first.combatants());

        assertTrue(hasStatus(first.combatants().getFirst(), "Vigor"));
        assertEquals(1, first.events().size());
        assertTrue(second.events().isEmpty());
        assertEquals(1, powerOf(second.combatants().getFirst(), "discipline").progress());
    }

    @Test
    void armorAndEnergyEventsProduceImmutableFirstTriggerReactions() {
        CombatantState owner = combatant("player", "players", power("fondations_profondes"),
                power("condensateur"), power("circuit_de_secours"));
        List<CombatantState> before = List.of(owner);

        PowerPassiveEngine.Result armor = engine.onEvents(COMBAT_ID, 1,
                List.of(new ArmorGained(COMBAT_ID, 1, "player", "player", 3, 3, 3)), before);
        PowerPassiveEngine.Result energy = engine.onEvents(COMBAT_ID, 1,
                List.of(new EnergySpent(COMBAT_ID, 1, "player", 2, 0)), armor.combatants());

        assertNotSame(owner, armor.combatants().getFirst());
        assertTrue(hasStatus(armor.combatants().getFirst(), "Steadfast"));
        assertEquals(4, energy.combatants().getFirst().armor());
        assertTrue(energy.events().stream().anyMatch(ArmorGained.class::isInstance));
    }

    @Test
    void circuitDeSecoursAnswersTheCardsOwnEnergyPayment() {
        CombatantState owner = combatant("player", "players", power("circuit_de_secours"));

        PowerPassiveEngine.Result result = engine.onEvents(COMBAT_ID, 1,
                List.of(new EnergySpent(COMBAT_ID, 1, "player", 3, 0)), List.of(owner));

        assertEquals(4, result.combatants().getFirst().armor());
        assertTrue(result.events().stream().anyMatch(ArmorGained.class::isInstance));
    }

    @Test
    void catalystBrandsAEnemyWhenItsOwnerAppliesADebuff() {
        CombatantState owner = combatant("player", "players", power("catalyseur"));
        CombatantState enemy = combatant("enemy", "enemies");

        PowerPassiveEngine.Result result = engine.onEvents(COMBAT_ID, 1,
                List.of(new StatusApplied(COMBAT_ID, 1, "player", "enemy", "Weakness", 0, 1, false)),
                List.of(owner, enemy));

        CombatantState brandedEnemy = result.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("enemy")).findFirst().orElseThrow();
        assertTrue(hasStatus(brandedEnemy, "Brand"));
        assertFalse(result.events().isEmpty());
    }

    @Test
    void reactionEnChaineGainsEnergyWhenAnEnemyCrystallizeBecomesArmed() {
        CombatantState cfi = combatant("cfi", "players", power("reaction_en_chaine"));
        CombatantState enemy = combatant("enemy", "enemies");

        PowerPassiveEngine.Result result = engine.onEvents(COMBAT_ID, 1,
                List.of(new StatusApplied(COMBAT_ID, 1, "enemy", "enemy", "Crystallize", 3, 2, true)),
                List.of(cfi, enemy));

        CombatantState updated = result.combatants().stream()
                .filter(combatant -> "cfi".equals(combatant.combatantId()))
                .findFirst().orElseThrow();
        assertEquals(4, updated.energy());
        assertTrue(result.events().stream().anyMatch(EnergyGained.class::isInstance));
    }

        @Test
        void contrefaconCreatesTheSameOneUseCopyAsTheItiRelic() {
        CombatantState enemy = combatant("enemy", "enemies");
        CombatantState iti = combatant("iti", "players", power("contrefacon"));
        CardDefinition attack = new CardDefinition("attack", 1, CardType.ATTACK,
            TargetingMode.SINGLE_ENEMY, Set.of(), List.of());
        CardPlayed played = new CardPlayed(COMBAT_ID, 1, "enemy", "enemy-card", "attack", List.of("iti"));

        PowerPassiveEngine.Result result = engine.onCardPlayed(
            COMBAT_ID, 1, played, attack, null, List.of(enemy, iti));

        CombatantState updated = result.combatants().stream()
            .filter(combatant -> "iti".equals(combatant.combatantId()))
            .findFirst().orElseThrow();
        assertEquals(1, updated.piles().hand().size());
        assertTrue(updated.piles().hand().getFirst().tags().containsAll(
            List.of(CardTag.CREATED, CardTag.ETHEREAL, CardTag.EXHAUST)));
        assertTrue(result.events().stream().anyMatch(CardMoved.class::isInstance));
        }

    private static CombatantState combatant(String id, String team, StatusInstance... statuses) {
        return new CombatantState(id, team, ControllerType.HUMAN, 30, 30, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()), List.of(statuses));
    }

    private static StatusInstance power(String id) {
        return new StatusInstance("PowerPassive", 0, -1, id, 0);
    }

    private static boolean hasStatus(CombatantState combatant, String statusType) {
        return combatant.statuses().stream().anyMatch(status -> statusType.equals(status.statusType()));
    }

    private static StatusInstance powerOf(CombatantState combatant, String id) {
        return combatant.statuses().stream().filter(status -> id.equals(status.cardId())).findFirst().orElseThrow();
    }
}