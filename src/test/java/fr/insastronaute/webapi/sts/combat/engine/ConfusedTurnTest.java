package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
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
 * Confusion joue le tour de celui qui la porte, jusqu'à ce que son énergie ne paie plus rien.
 */
class ConfusedTurnTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c0");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    /** Trois cartes à 1, trois points d'énergie : la main y passe entière. */
    @Test
    void aConfusedTurnSpendsEveryPointOfEnergyOnItsOwn() {
        Advanced advanced = advance(confused(), hand(3, 1), 3);

        assertThat(advanced.cardsPlayed()).isEqualTo(3);
        assertThat(actor(advanced).energy()).isZero();
    }

    /** Elle s'arrête sur ce qu'elle ne peut pas payer plutôt que de forcer le passage. */
    @Test
    void aConfusedTurnStopsAtWhatItCannotAfford() {
        Advanced advanced = advance(confused(), hand(3, 2), 3);

        assertThat(advanced.cardsPlayed())
                .describedAs("une seule carte à 2 tient dans 3 d'énergie")
                .isEqualTo(1);
    }

    /** Sans Confusion, le tour reste au joueur : l'orchestrateur n'y touche pas. */
    @Test
    void anUnconfusedPlayerKeepsTheirTurn() {
        Advanced advanced = advance(List.of(), hand(3, 1), 3);

        assertThat(advanced.cardsPlayed()).isZero();
        assertThat(advanced.state().activeCombatantId()).isEqualTo(HERO);
        assertThat(actor(advanced).piles().hand()).hasSize(3);
    }

    /**
     * Le point de ce plafond : une carte gratuite ne vide jamais l'énergie, et la boucle doit
     * pourtant se terminer -- ici, dans le serveur, sur la requête d'un joueur.
     */
    @Test
    void aHandOfFreeCardsStillEndsTheTurnRatherThanSpinningForever() {
        Advanced advanced = advance(confused(), hand(3, 0), 3);

        assertThat(advanced.cardsPlayed())
                .describedAs("plafonné, et le tour rendu")
                .isLessThanOrEqualTo(ConfusedTurn.MAX_PLAYS_PER_TURN);
        assertThat(advanced.state().activeCombatantId())
                .describedAs("le tour est passé à l'adversaire")
                .isEqualTo(ENEMY);
    }

    /** Le tirage sort du flux du combat : deux fois le même état, deux fois le même tour. */
    @Test
    void aConfusedTurnReplaysIdentically() {
        assertThat(playedCardIds(advance(confused(), hand(4, 1), 3)))
                .isEqualTo(playedCardIds(advance(confused(), hand(4, 1), 3)));
    }

    /** « Coût nul » joue gratuitement ce que son prix imprimé refuserait. */
    @Test
    void aConfusedTurnPlaysWhatCostNullifyMakesFree() {
        Advanced advanced = advance(confusedWithCostNullify(2), hand(3, 2), 0);

        assertThat(advanced.cardsPlayed())
                .describedAs("deux charges, deux cartes gratuites ; la troisième coûte 2 sans énergie")
                .isEqualTo(2);
        assertThat(actor(advanced).statuses())
                .describedAs("les deux charges sont consommées")
                .noneMatch(status -> "CostNullify".equals(status.statusType()));
    }

    // --- montage -------------------------------------------------------------------------------

    private record Advanced(CombatState state, List<CombatEvent> events) {
        int cardsPlayed() {
            return (int) events.stream().filter(CardPlayed.class::isInstance).count();
        }
    }

    private static List<String> playedCardIds(Advanced advanced) {
        return advanced.events().stream()
                .filter(CardPlayed.class::isInstance)
                .map(event -> ((CardPlayed) event).cardInstanceId())
                .toList();
    }

    private static CombatantState actor(Advanced advanced) {
        return advanced.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static List<StatusInstance> confused() {
        return List.of(new StatusInstance("Confusion", 0, 1, null, 0));
    }

    private static List<StatusInstance> confusedWithCostNullify(int charges) {
        return List.of(
                new StatusInstance("Confusion", 0, 1, null, 0),
                new StatusInstance("CostNullify", charges, -1, "", 0));
    }

    /** {@code count} cartes distinctes, toutes au même coût, qui frappent pour 1. */
    private static List<CardInstance> hand(int count, int cost) {
        List<CardInstance> hand = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            hand.add(new CardInstance("held-" + index, "card-" + cost));
        }
        return hand;
    }

    private static Advanced advance(
            List<StatusInstance> statuses, List<CardInstance> hand, int energy) {
        Map<String, CardDefinition> definitions = new LinkedHashMap<>();
        for (int cost = 0; cost <= 2; cost++) {
            definitions.put("card-" + cost, new CardDefinition(
                    "card-" + cost, cost, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                    List.of(new EffectDefinition(EffectType.DAMAGE, 1))));
        }

        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 60, 60, 0, energy, energy, 10,
                new CardPiles(List.of(), hand, List.of(), List.of()),
                statuses, List.of(), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.HUMAN, 60, 60, 0, 3, 3, 12,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));

        CombatState state = new CombatState(
                COMBAT_ID, 7, "v1", CombatStatus.ACTIVE, null, new RandomState(99),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 10), new TimelineEntry(ENEMY, 1, 12)),
                HERO);

        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", definitions), new RelicEngine(List.of()));
        // Les deux camps sont humains : aucune politique d'ennemi n'a rien à dire ici, et c'est
        // bien la Confusion seule qui fait avancer le tour.
        EnemyPolicy noPolicy = ignored -> {
            throw new IllegalStateException("no enemy policy should be consulted");
        };
        CombatOrchestrator.Advance advance =
                new CombatOrchestrator(engine, noPolicy).advanceAi(state);
        return new Advanced(advance.state(), advance.events());
    }
}
