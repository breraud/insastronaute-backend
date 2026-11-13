package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers.FlameEnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Le Saut qui retombe, l'Aura de feu qui brûle, et les Étourdissements retardés qui s'empilent. */
class JumpFlameAndDelayedStunTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000ee");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String LANDING = "Atterrissage";
    private static final String STRIKE = "frappe";
    private static final int PERMANENT = -1;

    // --- Jump ---------------------------------------------------------------------------------

    /**
     * Le Saut est dépensé par son atterrissage.
     *
     * <p>Sa durée est permanente, donc le décompte de fin de tour ne l'entamait pas, et rien ne le
     * retirait ensuite : le porteur atterrissait à chaque tour, indéfiniment.
     */
    @Test
    void jumpIsGoneOnceItHasLanded() {
        CombatResult result = endTurnWith(List.of(status("Jump", 6, PERMANENT)));

        assertThat(statusesOf(result, HERO))
                .extracting(StatusInstance::statusType)
                .doesNotContain("Jump");
    }

    @Test
    void theLandingStillLandsBeforeTheJumpIsSpent() {
        CombatResult result = endTurnWith(List.of(status("Jump", 6, PERMANENT)));

        assertThat(combatant(result, ENEMY).hp())
                .describedAs("l'atterrissage doit avoir frappé")
                .isLessThan(40);
    }

    /** Sans Saut, rien ne change : le tour se ferme comme d'habitude. */
    @Test
    void aTurnWithoutJumpLandsNothing() {
        CombatResult result = endTurnWith(List.of());

        assertThat(combatant(result, ENEMY).hp()).isEqualTo(40);
    }

    // --- Flame --------------------------------------------------------------------------------

    /**
     * « Aura de feu » pose une Brûlure.
     *
     * <p>Le nom du statut partait dans le paramètre de la carte, si bien que l'effet arrivait
     * sans statut à poser et ne brûlait personne.
     */
    @Test
    void flameBurnsItsTarget() {
        CombatResult result = playEnchanted(new EnchantmentInstance("Aura de feu", 3));

        assertThat(statusesOf(result, ENEMY))
                .extracting(StatusInstance::statusType)
                .contains("Burn");
    }

    @Test
    void flameBurnsForAsLongAsItsLevel() {
        CombatResult result = playEnchanted(new EnchantmentInstance("Aura de feu", 3));

        assertThat(statusesOf(result, ENEMY).stream()
                .filter(status -> "Burn".equals(status.statusType()))
                .findFirst()
                .orElseThrow()
                .duration())
                .isEqualTo(3);
    }

    @Test
    void anUnenchantedCardBurnsNobody() {
        CombatResult result = playEnchanted(null);

        assertThat(statusesOf(result, ENEMY))
                .extracting(StatusInstance::statusType)
                .doesNotContain("Burn");
    }

    // --- DelayedStun --------------------------------------------------------------------------

    @Test
    void delayedStunsStackRatherThanMerging() {
        List<StatusInstance> statuses = new ArrayList<>();
        StatusOperations.insert(statuses, status("DelayedStun", 0, 2));
        StatusOperations.insert(statuses, status("DelayedStun", 0, 3));

        assertThat(statuses).hasSize(2);
        assertThat(statuses).extracting(StatusInstance::duration).containsExactly(2, 3);
    }

    /**
     * Chaque exemplaire décompte pour lui-même quand un coup tombe.
     *
     * <p>Les réponses étaient appariées par le seul nom du statut, donc les deux retombaient sur
     * le premier exemplaire : il décomptait deux fois pendant que le second ne bougeait jamais.
     * Le trajet passe par le moteur, parce que c'est l'appariement du handler qui est en cause,
     * pas la réponse elle-même.
     */
    @Test
    void eachDelayedStunCountsDownOnItsOwnWhenABlowLands() {
        CardDefinition strike = new CardDefinition(
                STRIKE, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 4)));
        CardInstance card = new CardInstance("played", STRIKE);
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(strike.id(), strike)), new RelicEngine(List.of()));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 40, 40, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(status("DelayedStun", 0, 2), status("DelayedStun", 0, 3)),
                List.of(), null, 0);
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(7),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 1), new TimelineEntry(ENEMY, 10, 2)),
                HERO);

        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, card.instanceId(), List.of(ENEMY)));

        assertThat(statusesOf(result, ENEMY).stream()
                .filter(status -> "DelayedStun".equals(status.statusType()))
                .map(StatusInstance::duration))
                .describedAs("chacun perd un tour, pas le meme deux fois")
                .containsExactly(1, 2);
    }

    // --- helpers ------------------------------------------------------------------------------

    private static CombatResult endTurnWith(List<StatusInstance> heroStatuses) {
        CardDefinition landing = new CardDefinition(
                LANDING, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 9)));
        CardDefinition strike = new CardDefinition(
                STRIKE, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 2)));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(landing.id(), landing, strike.id(), strike)),
                new RelicEngine(List.of()));

        // Le Saut retombe a l'ouverture du tour de son porteur : on ferme celui de l'autre.
        CombatState state = state(List.of(), heroStatuses, ENEMY);
        return engine.handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), ENEMY));
    }

    private static CombatResult playEnchanted(EnchantmentInstance enchantment) {
        CardDefinition strike = new CardDefinition(
                STRIKE, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 4)));
        CardInstance card = new CardInstance(
                "played", STRIKE,
                enchantment == null ? List.of() : List.of(enchantment),
                null, null, List.of(), List.of(), List.of());
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(strike.id(), strike)),
                new RelicEngine(List.of()),
                new EnchantmentEngine(List.of(new FlameEnchantmentHandler())));

        CombatState state = state(List.of(card), List.of(), HERO);
        return engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), HERO, card.instanceId(), List.of(ENEMY)));
    }

    private static CombatState state(
            List<CardInstance> heroHand, List<StatusInstance> heroStatuses, String active) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 40, 40, 0, 3, 3, 10,
                new CardPiles(List.of(), heroHand, List.of(), List.of()),
                heroStatuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 40, 40, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        List<TimelineEntry> timeline = HERO.equals(active)
                ? List.of(new TimelineEntry(HERO, 0, 1), new TimelineEntry(ENEMY, 10, 2))
                : List.of(new TimelineEntry(ENEMY, 0, 1), new TimelineEntry(HERO, 10, 2));
        return new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(7),
                List.of(hero, enemy), timeline, active);
    }

    private static StatusInstance status(String type, int value, int duration) {
        return new StatusInstance(type, value, duration, null, 0);
    }

    private static CombatantState combatant(CombatResult result, String combatantId) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static List<StatusInstance> statusesOf(CombatResult result, String combatantId) {
        return combatant(result, combatantId).statuses();
    }
}
