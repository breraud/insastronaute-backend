package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
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
 * Confusion égare le coup des deux côtés de la ligne, son lanceur compris.
 *
 * <p>Le tirage se limitait autrefois au bord de la cible demandée. Dans un duel, où chaque bord ne
 * compte qu'une tête, il n'avait alors rien à choisir : le statut ne faisait rien du tout. Il tire
 * maintenant parmi tous les vivants, ce qui lui rend un sens à deux — au prix d'une attaque qui
 * peut revenir sur celui qui la joue.
 */
class CombatEngineConfusionTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000cf");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String ALLY = "ally";
    private static final String CARD = "tested-card";
    private static final int DAMAGE = 8;
    private static final int FULL_HP = 20;

    /** Le point de la manœuvre : en duel, une attaque confuse peut frapper son lanceur. */
    @Test
    void aConfusedBlowCanComeBackOnItsCasterInADuel() {
        assertThat(seedsWhere(true, HERO))
                .describedAs("aucune graine ne retourne le coup sur son lanceur")
                .isNotEmpty();
    }

    /** Et elle peut toujours partir là où elle était adressée. */
    @Test
    void aConfusedBlowCanStillLandOnTheRequestedTarget() {
        assertThat(seedsWhere(true, ENEMY)).isNotEmpty();
    }

    /** Sans Confusion, la cible demandée est la seule qui prend, quelle que soit la graine. */
    @Test
    void anUnconfusedBlowAlwaysLandsWhereItWasAimed() {
        assertThat(seedsWhere(false, HERO)).isEmpty();
        assertThat(seedsWhere(false, ENEMY)).hasSize(64);
    }

    // --- un ennemi confus ---------------------------------------------------------------------

    /**
     * Un ennemi piloté par la machine est égaré comme un joueur : son motif passe par la même
     * résolution de cible (CombatEngine.playAiPattern), qui interroge la Confusion du lanceur.
     */
    @Test
    void aConfusedEnemyCanStrikeItsOwnAlly() {
        assertThat(aiSeedsWhere(ALLY)).isNotEmpty();
    }

    @Test
    void aConfusedEnemyCanStrikeItself() {
        assertThat(aiSeedsWhere(ENEMY)).isNotEmpty();
    }

    @Test
    void aConfusedEnemyCanStillStrikeTheHeroItAimedAt() {
        assertThat(aiSeedsWhere(HERO)).isNotEmpty();
    }

    private static List<Long> aiSeedsWhere(String wounded) {
        return java.util.stream.LongStream.range(0, 64)
                .filter(seed -> {
                    CombatResult result = playAiPattern(seed);
                    return combatant(result, wounded).hp() == FULL_HP - DAMAGE;
                })
                .boxed()
                .toList();
    }

    /** Un ennemi confus, un allié à côté de lui, et un héros qu'il croit viser. */
    private static CombatResult playAiPattern(long seed) {
        CardDefinition definition = new CardDefinition(
                CARD, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, DAMAGE)));

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, FULL_HP, FULL_HP, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, FULL_HP, FULL_HP, 0, 0, 0, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Confusion", 0, 1, null, 0)), List.of(), null, 0);
        CombatantState ally = new CombatantState(
                ALLY, "enemies", ControllerType.AI, FULL_HP, FULL_HP, 0, 0, 0, 14,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(seed),
                List.of(hero, enemy, ally),
                List.of(new TimelineEntry(ENEMY, 0, 12), new TimelineEntry(HERO, 1, 10),
                        new TimelineEntry(ALLY, 2, 14)),
                ENEMY);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of()));
        return engine.handle(state,
                new AiPlayPatternCommand(COMBAT_ID, 7, ENEMY, CARD, List.of(HERO), -1));
    }

    /** Les graines, parmi les 64 premières, où {@code wounded} est celui qui a perdu des PV. */
    private static List<Long> seedsWhere(boolean confused, String wounded) {
        return java.util.stream.LongStream.range(0, 64)
                .filter(seed -> {
                    CombatResult result = play(confused, seed);
                    return combatant(result, wounded).hp() == FULL_HP - DAMAGE;
                })
                .boxed()
                .toList();
    }

    private static CombatResult play(boolean confused, long seed) {
        CardDefinition definition = new CardDefinition(
                CARD, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, DAMAGE)));
        CardInstance card = new CardInstance("played", CARD);

        List<StatusInstance> statuses = confused
                ? List.of(new StatusInstance("Confusion", 0, 1, null, 0))
                : List.of();
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, FULL_HP, FULL_HP, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(card), List.of(), List.of()),
                statuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, FULL_HP, FULL_HP, 0, 2, 2, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(seed),
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
