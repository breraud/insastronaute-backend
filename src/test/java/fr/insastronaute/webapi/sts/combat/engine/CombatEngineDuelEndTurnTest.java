package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Un duel qui n'échange que des fins de tour.
 *
 * <p>Deux humains, aucune politique pour combler les silences : c'est la forme du PvP, et c'est
 * la commande dont le joueur dit qu'elle finit par rendre une erreur serveur.
 */
class CombatEngineDuelEndTurnTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final String ONE = "player-one";
    private static final String TWO = "player-two";
    private static final String CARD = "frappe";

    @Test
    void endingTurnsBackAndForthNeverThrows() {
        CombatState state = duel();
        CombatEngine engine = engine();

        assertThatCode(() -> {
            CombatState current = state;
            for (int turn = 0; turn < 40; turn++) {
                CombatResult result = engine.handle(current, new EndTurnCommand(
                        COMBAT_ID, current.revision(), current.activeCombatantId()));
                assertThat(result.accepted())
                        .describedAs("tour %d refusé : %s", turn, result.rejection())
                        .isTrue();
                current = result.state();
                if (current.status() != CombatStatus.ACTIVE) {
                    break;
                }
            }
        }).doesNotThrowAnyException();
    }

    /** La file garde exactement une entrée par combattant, tour après tour. */
    @Test
    void theTimelineKeepsOneEntryPerCombatant() {
        CombatState current = duel();
        CombatEngine engine = engine();

        for (int turn = 0; turn < 12 && current.status() == CombatStatus.ACTIVE; turn++) {
            current = engine.handle(current, new EndTurnCommand(
                    COMBAT_ID, current.revision(), current.activeCombatantId())).state();
            CombatState after = current;

            assertThat(after.timeline())
                    .describedAs("après %d fins de tour", turn + 1)
                    .extracting(TimelineEntry::combatantId)
                    .doesNotHaveDuplicates();
            assertThat(after.timeline())
                    .filteredOn(entry -> entry.combatantId().equals(after.activeCombatantId()))
                    .hasSize(1);
        }
    }

    /**
     * Une carte portant un effet EndTurn ferme le tour, au lieu de vider une bourse.
     *
     * <p>Le handler se contentait de mettre l'energie a zero, ce qui laissait le joueur sur son
     * tour avec une carte qui promettait de le finir.
     */
    @Test
    void aCardCarryingTheEndTurnEffectClosesTheTurn() {
        CombatState state = duelHolding(endTurnCard());
        CombatEngine engine = engineWith(endTurnCard());

        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), ONE, ONE + "-held", List.of(ONE)));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().activeCombatantId())
                .describedAs("la main doit avoir change de cote")
                .isEqualTo(TWO);
        assertThat(result.events()).anyMatch(TurnEnded.class::isInstance);
    }

    /** Elle le ferme meme jouee sans energie a depenser : rien ne s'y retire plus tot. */
    @Test
    void itClosesTheTurnEvenWithNoEnergyLeftToSpend() {
        CombatState state = duelHolding(endTurnCard(), 0);
        CombatEngine engine = engineWith(endTurnCard());

        CombatResult result = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), ONE, ONE + "-held", List.of(ONE)));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().activeCombatantId()).isEqualTo(TWO);
    }

    private static CardDefinition endTurnCard() {
        return new CardDefinition(
                "repos", 0, CardType.SKILL, TargetingMode.SELF, Set.of(),
                List.of(new EffectDefinition(EffectType.ARMOR, 4, true),
                        new EffectDefinition(EffectType.END_TURN, 0, true)));
    }

    private static CombatEngine engineWith(CardDefinition extra) {
        CardDefinition strike = new CardDefinition(
                CARD, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 2)));
        return new CombatEngine(
                new CardCatalog("v1", Map.of(strike.id(), strike, extra.id(), extra)),
                new RelicEngine(List.of()));
    }

    private static CombatState duelHolding(CardDefinition held) {
        return duelHolding(held, 3);
    }

    private static CombatState duelHolding(CardDefinition held, int energy) {
        CombatantState one = duellist(ONE, "team-one");
        CombatantState holding = new CombatantState(
                one.combatantId(), one.teamId(), one.controllerType(), one.maxHp(), one.hp(),
                one.armor(), energy, one.baseEnergy(), one.turnDelayTicks(),
                new CardPiles(one.piles().draw(),
                        List.of(new CardInstance(ONE + "-held", held.id())),
                        List.of(), List.of()),
                one.statuses(), one.relics(), one.intentCardId(), one.patternIndex());
        return new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(11),
                List.of(holding, duellist(TWO, "team-two")),
                List.of(new TimelineEntry(ONE, 0, 1), new TimelineEntry(TWO, 10, 2)),
                ONE);
    }

    private static CombatEngine engine() {
        CardDefinition strike = new CardDefinition(
                CARD, 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 2)));
        return new CombatEngine(
                new CardCatalog("v1", Map.of(strike.id(), strike)), new RelicEngine(List.of()));
    }

    private static CombatState duel() {
        return new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(11),
                List.of(duellist(ONE, "team-one"), duellist(TWO, "team-two")),
                List.of(new TimelineEntry(ONE, 0, 1), new TimelineEntry(TWO, 10, 2)),
                ONE);
    }

    private static CombatantState duellist(String id, String teamId) {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            deck.add(new CardInstance(id + "-card-" + index, CARD));
        }
        return new CombatantState(
                id, teamId, ControllerType.HUMAN, 60, 60, 0, 3, 3, 10,
                new CardPiles(deck, List.of(), List.of(), List.of()),
                List.of(), List.of(), null, 0);
    }
}
