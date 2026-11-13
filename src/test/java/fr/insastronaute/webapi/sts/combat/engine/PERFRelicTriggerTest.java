package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.ai.SingleCardEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.PERFRelicHandler;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Alternateur de performance : « Quand vous subissez des degats, jouez la carte du dessus de
 * votre pioche. »
 *
 * <p>« Subir des degats » se compte sur le coup porte, pas sur ce qui a traverse : une armure qui
 * encaisse tout n'annule pas le coup. Un Multihit porte plusieurs coups et vaut donc plusieurs
 * declenchements, et Gravite en est un comme un autre.
 */
class PERFRelicTriggerTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000c33");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String BLOW = "coup";
    private static final String SPARE = "carte-du-dessus";
    private static final int DECK = 6;

    @Test
    void aBlowThatLandsSpendsTheTopCard() {
        assertThat(cardsSpent(damage(8), 0)).isEqualTo(1);
    }

    /**
     * Ce que le client anime est l'evenement CardPlayed, pas le vidage de la pioche.
     *
     * <p>La carte quitte la pioche dans l'etat que le handler rend, ce qui se produit de toute
     * facon ; savoir si elle a ete *jouee* se lit sur les evenements, et c'est la seule mesure
     * qui corresponde a ce que le joueur voit.
     */
    @Test
    void aBlowThatLandsActuallyPlaysTheCard() {
        assertThat(cardsPlayed(damage(8), 0)).isEqualTo(1);
    }

    @Test
    void everyHitOfAMultihitPlaysItsOwnCard() {
        assertThat(cardsPlayed(multihit(3, 3), 0)).isEqualTo(3);
    }

    /**
     * La carte jouee est annoncee sous son propre identifiant : celle du dessus de la pioche, pas
     * un exemplaire fabrique que le client ne saurait retrouver nulle part.
     */
    @Test
    void thePlayNamesTheActualTopCard() {
        List<String> named = played(damage(8), 0).events().stream()
                .filter(fr.insastronaute.webapi.sts.combat.event.CardPlayed.class::isInstance)
                .map(fr.insastronaute.webapi.sts.combat.event.CardPlayed.class::cast)
                .filter(event -> SPARE.equals(event.definitionId()))
                .map(fr.insastronaute.webapi.sts.combat.event.CardPlayed::cardInstanceId)
                .toList();

        assertThat(named).containsExactly("spare-0");
    }

    @Test
    void gravityPlaysItsCardToo() {
        assertThat(cardsPlayed(gravity(50), 0)).isEqualTo(1);
    }

    /** L'armure qui encaisse tout n'empeche pas d'avoir subi le coup. */
    @Test
    void aBlowTheArmourFullyBlocksStillSpendsOne() {
        assertThat(cardsSpent(damage(8), 30)).isEqualTo(1);
    }

    @Test
    void aMultihitSpendsOnePerHit() {
        assertThat(cardsSpent(multihit(3, 3), 0)).isEqualTo(3);
    }

    @Test
    void aBlockedMultihitStillSpendsOnePerHit() {
        assertThat(cardsSpent(multihit(3, 3), 30)).isEqualTo(3);
    }

    @Test
    void gravitySpendsOneToo() {
        assertThat(cardsSpent(gravity(50), 0)).isEqualTo(1);
    }

    /**
     * Plusieurs tours d'ennemi enchaines dans une seule commande : chaque coup doit jouer sa
     * carte.
     *
     * <p>C'est ce que fait {@code CombatOrchestrator} des que l'ennemi est plus rapide que le
     * joueur, et c'est le cas dont le joueur dit que la relique ne fait rien.
     */
    @Test
    void everyBlowOfAChainOfEnemyTurnsPlaysItsOwnCard() {
        CardDefinition attack = new CardDefinition(
                BLOW, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(damage(4)));
        CardDefinition spare = new CardDefinition(
                SPARE, 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
        CardCatalog catalog = new CardCatalog(
                "v1", Map.of(attack.id(), attack, spare.id(), spare));
        CombatEngine engine = new CombatEngine(
                catalog, new RelicEngine(List.of(new PERFRelicHandler())));

        List<CardInstance> draw = new ArrayList<>();
        for (int index = 0; index < DECK; index++) {
            draw.add(new CardInstance("spare-" + index, SPARE));
        }
        // L'ennemi est bien plus rapide : plusieurs de ses tours passent avant celui du heros.
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 200, 200, 0, 3, 3, 100,
                new CardPiles(draw, List.of(), List.of(), List.of()),
                List.<StatusInstance>of(), List.of("PERFRelic"), null, 0);
        List<CardInstance> enemyHand = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            enemyHand.add(new CardInstance("blow-" + index, BLOW));
        }
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 200, 200, 0, 3, 3, 10,
                new CardPiles(List.of(), enemyHand, List.of(), List.of()),
                List.<StatusInstance>of(), List.of(), null, 0);
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry(ENEMY, 0, 1), new TimelineEntry(HERO, 90, 2)),
                ENEMY);

        CombatOrchestrator.Advance advance =
                new CombatOrchestrator(engine, new SingleCardEnemyPolicy(catalog))
                        .advanceAi(state);

        long blows = advance.events().stream()
                .filter(fr.insastronaute.webapi.sts.combat.event.DamageApplied.class::isInstance)
                .count();
        long played = advance.events().stream()
                .filter(fr.insastronaute.webapi.sts.combat.event.CardPlayed.class::isInstance)
                .map(fr.insastronaute.webapi.sts.combat.event.CardPlayed.class::cast)
                .filter(event -> SPARE.equals(event.definitionId()))
                .count();

        assertThat(blows).describedAs("l'ennemi a bien frappe plusieurs fois").isGreaterThan(1);
        assertThat(played)
                .describedAs("une carte jouee par coup recu, sur toute la chaine")
                .isEqualTo(blows);
    }

    /**
     * Une pioche epuisee se remplit de la defausse plutot que d'eteindre la relique.
     *
     * <p>Elle retire une carte par coup recu, donc la pioche se vide vite ; sans melange elle se
     * taisait pour le reste du combat.
     */
    @Test
    void itReshufflesTheDiscardWhenTheDrawPileRunsOut() {
        assertThat(cardsPlayed(multihit(3, 3), 0, 1))
                .describedAs("une seule carte en pioche, et trois coups a encaisser")
                .isEqualTo(3);
    }

    /** Le cas du joueur : une Multifrappe alors qu'il ne reste presque rien a piocher. */
    @Test
    void aMultihitKeepsPlayingOncePerHitOnANearlyEmptyPile() {
        assertThat(cardsPlayed(multihit(2, 4), 0, 2)).isEqualTo(4);
    }

    /** Rien en pioche ni en defausse : il n'y a plus rien a jouer, et c'est tout. */
    @Test
    void anEmptyDeckPlaysNothingRatherThanFailing() {
        assertThat(cardsPlayed(damage(8), 0, 0)).isZero();
    }

    // --- helpers ------------------------------------------------------------------------------

    /** @return combien de cartes ont quitte la pioche du heros */
    private static int cardsSpent(EffectDefinition blow, int heroArmor) {
        CombatResult result = played(blow, heroArmor);
        return DECK - result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow()
                .piles().draw().size();
    }

    private static CombatResult played(EffectDefinition blow, int heroArmor) {
        return played(blow, heroArmor, DECK);
    }

    private static CombatResult played(EffectDefinition blow, int heroArmor, int deckSize) {
        CardDefinition attack = new CardDefinition(
                BLOW, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(), List.of(blow));
        CardDefinition spare = new CardDefinition(
                SPARE, 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 1, true)));
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(attack.id(), attack, spare.id(), spare)),
                new RelicEngine(List.of(new PERFRelicHandler())));

        List<CardInstance> draw = new ArrayList<>();
        for (int index = 0; index < deckSize; index++) {
            draw.add(new CardInstance("spare-" + index, SPARE));
        }
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 200, 200, heroArmor, 3, 3, 10,
                new CardPiles(draw, List.of(), List.of(), List.of()),
                List.<StatusInstance>of(), List.of("PERFRelic"), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 200, 200, 0, 3, 3, 10,
                new CardPiles(List.of(), List.of(new CardInstance("blow", BLOW)),
                        List.of(), List.of()),
                List.<StatusInstance>of(), List.of(), null, 0);
        CombatState state = new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(9),
                List.of(hero, enemy),
                List.of(new TimelineEntry(ENEMY, 0, 1), new TimelineEntry(HERO, 10, 2)),
                ENEMY);

        return engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), ENEMY, "blow", List.of(HERO)));
    }

    /** @return combien de cartes du dessus ont ete reellement jouees, evenements en main */
    private static int cardsPlayed(EffectDefinition blow, int heroArmor) {
        return cardsPlayed(blow, heroArmor, DECK);
    }

    private static int cardsPlayed(EffectDefinition blow, int heroArmor, int deckSize) {
        return (int) played(blow, heroArmor, deckSize).events().stream()
                .filter(fr.insastronaute.webapi.sts.combat.event.CardPlayed.class::isInstance)
                .map(fr.insastronaute.webapi.sts.combat.event.CardPlayed.class::cast)
                .filter(event -> SPARE.equals(event.definitionId()))
                .count();
    }

    private static EffectDefinition damage(int value) {
        return new EffectDefinition(EffectType.DAMAGE, value);
    }

    private static EffectDefinition multihit(int value, int hits) {
        return new EffectDefinition(EffectType.MULTIHIT, value, false, null, hits, null, null);
    }

    private static EffectDefinition gravity(int percent) {
        return new EffectDefinition(EffectType.GRAVITY, percent);
    }
}
