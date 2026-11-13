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
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MECARelicHandler;
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
 * Autoreparation : les soins alimentent une reserve qui releve son porteur a 0 PV, et les ennemis
 * en subissent la moitie.
 *
 * <p>Quatre moments a verifier separement : le soin qui passe et compte, la reserve qui se
 * remplit, la resurrection qui la depense, et la revanche qui suit. Chacun peut echouer sans les
 * autres.
 */
class MECARelicBehaviourTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000b22");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String HEAL_CARD = "soin";
    private static final String STRIKE = "frappe";
    private static final String RELIC = "MECARelic";

    /**
     * Le soin est rendu <em>et</em> compte : la relique ne l'echange plus contre sa reserve.
     *
     * <p>Elle l'avalait, si bien que son porteur ne regagnait pas un point de vie de tout le
     * combat — bien pire que ce que font les autres reliques.
     */
    @Test
    void healingIsBothGivenAndCounted() {
        CombatResult result = engine().handle(
                state(20, 40, List.of(new CardInstance("held", HEAL_CARD))),
                new PlayCardCommand(COMBAT_ID, 1, HERO, "held", List.of(HERO)));

        CombatantState hero = combatant(result.state(), HERO);
        assertThat(hero.hp()).describedAs("le soin est bel et bien rendu").isEqualTo(20 + 12);
        // Ce heros est monte a la main, sans la reserve de depart que la run apporte : la
        // reserve ne vaut donc ici que ce que ce soin vient d'y verser.
        assertThat(hero.recall(RELIC, "charge"))
                .describedAs("et s'ajoute a la reserve")
                .isEqualTo(12);
    }

    /**
     * Rechargee entre-temps, elle releve son porteur une seconde fois dans le meme combat.
     *
     * <p>Un drapeau « deja servi ce combat » l'en empechait ; c'est desormais la reserve seule
     * qui limite, et elle se regagne en se soignant.
     */
    @Test
    void itSavesAgainOnceHealingHasRechargedIt() {
        CombatState first = charged(30);

        CombatState afterFirstSave = engine().handle(first, new PlayCardCommand(
                COMBAT_ID, first.revision(), ENEMY, "blow", List.of(HERO))).state();
        assertThat(combatant(afterFirstSave, HERO).recall(RELIC, "charge"))
                .describedAs("la reserve est videe en servant")
                .isZero();

        // Rechargee par un soin, puis remise a un souffle de vie.
        CombatantState hero = combatant(afterFirstSave, HERO).remembering(RELIC, "charge", 18);
        CombatState reloaded = new CombatState(
                COMBAT_ID, afterFirstSave.revision(), "v1", CombatStatus.ACTIVE, null,
                new RandomState(4),
                List.of(rebuildAt(hero, 6), combatant(afterFirstSave, ENEMY)),
                afterFirstSave.timeline(), ENEMY);

        CombatState afterSecondSave = engine().handle(reloaded, new PlayCardCommand(
                COMBAT_ID, reloaded.revision(), ENEMY, "blow-2", List.of(HERO))).state();

        assertThat(combatant(afterSecondSave, HERO).hp())
                .describedAs("relevee une seconde fois dans le meme combat")
                .isEqualTo(18);
    }

    @Test
    void aLethalBlowBringsTheStoredHitPointsBack() {
        CombatState charged = charged(30);

        CombatResult killed = engine().handle(charged, new PlayCardCommand(
                COMBAT_ID, charged.revision(), ENEMY, "blow", List.of(HERO)));

        assertThat(combatant(killed.state(), HERO).hp())
                .describedAs("les PV emmagasines sont restitues")
                .isEqualTo(30);
    }

    @Test
    void theEnemiesTakeHalfOfWhatWasStored() {
        CombatState charged = charged(30);

        CombatResult killed = engine().handle(charged, new PlayCardCommand(
                COMBAT_ID, charged.revision(), ENEMY, "blow", List.of(HERO)));

        assertThat(combatant(killed.state(), ENEMY).hp())
                .describedAs("la moitie de 30 rendue a l'ennemi")
                .isEqualTo(60 - 15);
    }

    /** Sans rien d'emmagasine, la relique ne retient pas le coup fatal. */
    @Test
    void anUnchargedRelicSavesNobody() {
        CombatState state = state(6, 60, List.of());

        CombatResult killed = engine().handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), ENEMY, "blow", List.of(HERO)));

        assertThat(combatant(killed.state(), HERO).hp()).isZero();
    }

    /**
     * La reserve vaut 20 avant d'avoir soigne quoi que ce soit (MECARelic.cs:6).
     *
     * <p>Elle partait de zero a chaque combat, si bien qu'un combat sans soin laissait la garde
     * de charge renvoyer aussitot : la relique ne faisait rien, jamais.
     */
    @Test
    void theRelicOpensARunWithItsBaselineStored() {
        assertThat(new MECARelicHandler().runScopedMemory())
                .containsEntry("charge", 20);
    }

    /** La reserve est la seule chose que la relique retient, et elle suit la run. */
    @Test
    void onlyTheChargeIsCarriedFromOneCombatToTheNext() {
        assertThat(new MECARelicHandler().runScopedMemory()).containsOnlyKeys("charge");
    }

    /** Une morsure de statut tue sans passer par une carte : la relique doit repondre quand meme. */
    @Test
    void aPoisonDeathStillBringsTheStoredHitPointsBack() {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 60, 1, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(new StatusInstance("Poison", 0, 5, null, 0)),
                List.of(RELIC), null, 0)
                .remembering(RELIC, "charge", 25);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 60, 60, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("blow", STRIKE)),
                        List.of(), List.of()),
                List.<StatusInstance>of(), List.of(), null, 0);
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(4),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 1), new TimelineEntry(ENEMY, 10, 2)),
                HERO);

        // Le Poison mord a l'ouverture du tour de son porteur : on ferme celui-ci pour y arriver.
        CombatResult result = engine().handle(state,
                new fr.insastronaute.webapi.sts.combat.command.EndTurnCommand(
                        COMBAT_ID, state.revision(), HERO));
        CombatState afterEnemyTurn = result.state();
        while (!HERO.equals(afterEnemyTurn.activeCombatantId())
                && afterEnemyTurn.status() == CombatStatus.ACTIVE) {
            afterEnemyTurn = engine().handle(afterEnemyTurn,
                    new fr.insastronaute.webapi.sts.combat.command.EndTurnCommand(
                            COMBAT_ID, afterEnemyTurn.revision(),
                            afterEnemyTurn.activeCombatantId())).state();
        }

        assertThat(combatant(afterEnemyTurn, HERO).hp())
                .describedAs("releve par la relique, pas mort du poison")
                .isEqualTo(25);
    }

    // --- helpers ------------------------------------------------------------------------------

    private static CombatantState rebuildAt(CombatantState combatant, int hp) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), hp, combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(),
                combatant.statuses(), combatant.relics(), combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }

    /** Le heros a {@code stored} PV en reserve et un souffle de vie : le prochain coup le tue. */
    private static CombatState charged(int stored) {
        CombatState state = state(6, 60, List.of());
        CombatantState hero = combatant(state, HERO)
                .remembering(RELIC, "charge", stored);
        return new CombatState(
                state.combatId(), state.revision(), state.dataVersion(), state.status(),
                state.winnerTeamId(), state.random(),
                List.of(hero, combatant(state, ENEMY)),
                state.timeline(), state.activeCombatantId());
    }

    private static CombatState state(int heroHp, int enemyHp, List<CardInstance> heroHand) {
        boolean heroActs = !heroHand.isEmpty();
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 60, heroHp, 0, 3, 3, 10,
                new CardPiles(List.of(), heroHand, List.of(), List.of()),
                List.<StatusInstance>of(), List.of(RELIC), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 60, enemyHp, 0, 3, 3, 10,
                // Deux coups : de quoi tuer une fois, puis une seconde apres rechargement.
                new CardPiles(List.of(),
                        List.of(new CardInstance("blow", STRIKE),
                                new CardInstance("blow-2", STRIKE)),
                        List.of(), List.of()),
                List.<StatusInstance>of(), List.of(), null, 0);
        return new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(4),
                List.of(hero, enemy),
                heroActs
                        ? List.of(new TimelineEntry(HERO, 0, 1), new TimelineEntry(ENEMY, 10, 2))
                        : List.of(new TimelineEntry(ENEMY, 0, 1), new TimelineEntry(HERO, 10, 2)),
                heroActs ? HERO : ENEMY);
    }

    private static CombatEngine engine() {
        CardDefinition heal = new CardDefinition(
                HEAL_CARD, 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.HEAL, 12, true)));
        CardDefinition strike = new CardDefinition(
                STRIKE, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 30)));
        return new CombatEngine(
                new CardCatalog("v1", Map.of(heal.id(), heal, strike.id(), strike)),
                new RelicEngine(List.of(new MECARelicHandler())));
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}
