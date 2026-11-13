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
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * La Cristallisation compte les coups d'Attaque, et rend une Recristallisation quand elle est
 * pleine. Mégacristal lui demande un coup de moins.
 */
class CrystallizeMegacristalTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000cd");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String STRIKE = "strike";
    private static final String RECRYSTALLIZE = "recristallisation";

    @Test
    void threeBlowsArmAnOrdinaryCrystallize() {
        assertThat(recrystallizationsOverBlows(3, false)).isPositive();
    }

    @Test
    void twoBlowsAreNotEnoughWithoutMegacristal() {
        assertThat(recrystallizationsOverBlows(2, false)).isZero();
    }

    /** Le point du rapport : avec Mégacristal, deux coups doivent suffire. */
    @Test
    void twoBlowsArmACrystallizeUnderMegacristal() {
        assertThat(recrystallizationsOverBlows(2, true)).isPositive();
    }

    /** Et il ne doit pas falloir plus de coups qu'ordinairement, quel qu'en soit le nombre. */
    @Test
    void megacristalNeverMakesItHarder() {
        assertThat(recrystallizationsOverBlows(4, true)).isPositive();
    }

    /**
     * Une cible sous armure : les coups portent, l'armure les mange, et le compteur avance quand
     * même. C'est le coup porté qui compte et non la blessure -- la règle d'Unity, où
     * {@code OnDamageTaken} reçoit {@code dmg} sans jamais voir l'armure.
     */
    @Test
    void anArmouredTargetStillFeedsTheCount() {
        assertThat(recrystallizationsOverBlows(4, false, 500))
                .describedAs("quatre coups sur une cible cuirassée")
                .isPositive();
    }

    /**
     * Le vrai chemin : la Cristallisation est posée par une carte alors que le Mégacristal est
     * déjà là, et reçoit donc son cadre d'or au passage (StatusHandler).
     */
    @Test
    void aCrystallizeAppliedUnderMegacristalStillArms() {
        assertThat(recrystallizationsAfterApplying(true))
                .describedAs("posée par une carte sur une cible mégacristallisée")
                .isPositive();
    }

    @Test
    void theSameCardArmsWithoutMegacristal() {
        assertThat(recrystallizationsAfterApplying(false)).isPositive();
    }


    /**
     * Pose la Cristallisation avec une carte, puis frappe jusqu'à cinq fois.
     *
     * @return combien de Recristallisations sont parties
     */
    private static int recrystallizationsAfterApplying(boolean megacristal) {
        Map<String, CardDefinition> definitions = new LinkedHashMap<>();
        definitions.put(STRIKE, new CardDefinition(
                STRIKE, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1))));
        definitions.put("poser", new CardDefinition(
                "poser", 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 4),
                        new EffectDefinition(
                                EffectType.STATUS, 0, false, "Crystallize", 9, null, null))));
        definitions.put(RECRYSTALLIZE, new CardDefinition(
                RECRYSTALLIZE, "Recristallisation", null, 0, false, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5))));

        List<CardInstance> hand = new ArrayList<>();
        hand.add(new CardInstance("poser-0", "poser"));
        for (int index = 0; index < 5; index++) {
            hand.add(new CardInstance("blow-" + index, STRIKE));
        }

        List<StatusInstance> enemyStatuses = megacristal
                ? List.of(StatusCatalog.instantiate("Megacristal", 0, 0, null, 0))
                : List.<StatusInstance>of();

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 200, 200, 0, 9, 9, 20,
                new CardPiles(List.of(), hand, List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 500, 500, 0, 0, 0, 1_000_000,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                enemyStatuses, List.of(), null, 0);

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(21),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1_000_000, 1)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", definitions), new RelicEngine(List.of()));

        List<String> toPlay = new ArrayList<>();
        toPlay.add("poser-0");
        for (int index = 0; index < 5; index++) {
            toPlay.add("blow-" + index);
        }

        int fired = 0;
        for (String instanceId : toPlay) {
            CombatResult result = engine.handle(state, new PlayCardCommand(
                    COMBAT_ID, state.revision(), HERO, instanceId, List.of(ENEMY)));
            if (!result.accepted()) {
                throw new IllegalStateException(instanceId + " was rejected");
            }
            fired += (int) result.events().stream()
                    .filter(CardPlayed.class::isInstance)
                    .map(CardPlayed.class::cast)
                    .filter(played -> RECRYSTALLIZE.equals(played.definitionId()))
                    .count();
            state = result.state();
        }
        return fired;
    }

    private static int recrystallizationsOverBlows(int blows, boolean megacristal) {
        return recrystallizationsOverBlows(blows, megacristal, 0);
    }

    /** Combien de Recristallisations sont parties au bout de {@code blows} coups. */
    private static int recrystallizationsOverBlows(int blows, boolean megacristal, int enemyArmor) {
        Map<String, CardDefinition> definitions = new LinkedHashMap<>();
        definitions.put(STRIKE, new CardDefinition(
                STRIKE, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1))));
        definitions.put(RECRYSTALLIZE, new CardDefinition(
                RECRYSTALLIZE, "Recristallisation", null, 0, false, CardType.ATTACK,
                TargetingMode.SINGLE_ENEMY, Set.of(), List.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 5))));

        List<CardInstance> hand = new ArrayList<>();
        for (int index = 0; index < blows; index++) {
            hand.add(new CardInstance("blow-" + index, STRIKE));
        }

        List<StatusInstance> enemyStatuses = new ArrayList<>();
        enemyStatuses.add(StatusCatalog.instantiate("Crystallize", 1, 9, null, 0));
        if (megacristal) {
            enemyStatuses.add(StatusCatalog.instantiate("Megacristal", 0, -1, null, 0));
        }

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 200, 200, 0, 9, 9, 20,
                new CardPiles(List.of(), hand, List.of(), List.of()),
                List.of(), List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 500, 500, enemyArmor, 0, 0, 1_000_000,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                enemyStatuses, List.of(), null, 0);

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(21),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 1_000_000, 1)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", definitions), new RelicEngine(List.of()));

        int fired = 0;
        for (int index = 0; index < blows; index++) {
            CombatResult result = engine.handle(state, new PlayCardCommand(
                    COMBAT_ID, state.revision(), HERO, "blow-" + index, List.of(ENEMY)));
            if (!result.accepted()) {
                throw new IllegalStateException("blow " + index + " was rejected");
            }
            fired += (int) result.events().stream()
                    .filter(CardPlayed.class::isInstance)
                    .map(CardPlayed.class::cast)
                    .filter(played -> RECRYSTALLIZE.equals(played.definitionId()))
                    .count();
            state = result.state();
        }
        return fired;
    }
}
