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
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MirrorShellRelic;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SolarCoreRelic;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
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

/** Deux reliques qui ne faisaient pas ce qu'elles annoncent, chacune dans un sens different. */
class SolarCoreAndMirrorShellTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000a11");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String CARD = "frappe";
    private static final int BASE_ENERGY = 3;
    private static final int OPENING_HAND = 5;

    // --- Panneau solaire ----------------------------------------------------------------------

    @Test
    void solarCoreDrawsItsTwoCards() {
        CombatState state = created("SolarCoreRelic").state();

        assertThat(combatant(state, HERO).piles().hand())
                .describedAs("les cinq de l'ouverture plus les deux du panneau")
                .hasSize(OPENING_HAND + 2);
    }

    @Test
    void solarCoreGrantsItsTwoEnergyOnTheOpeningTurn() {
        CombatState state = created("SolarCoreRelic").state();

        assertThat(combatant(state, HERO).energy()).isEqualTo(BASE_ENERGY + 2);
    }

    /** « Au debut du combat » : une fois, pas a chaque tour. */
    @Test
    void solarCoreNeverGrantsAgain() {
        CombatState state = backToTheHero(created("SolarCoreRelic").state());

        assertThat(combatant(state, HERO).energy())
                .describedAs("le deuxieme tour s'ouvre sur la dotation de base")
                .isEqualTo(BASE_ENERGY);
    }

    @Test
    void solarCoreDoesNotKeepDrawingTurnAfterTurn() {
        CombatState state = backToTheHero(created("SolarCoreRelic").state());

        // Une main est distribuee a chaque ouverture ; ce qui compte est qu'aucune paire de
        // cartes ne s'y ajoute une seconde fois.
        assertThat(combatant(state, HERO).piles().hand()).hasSize(OPENING_HAND);
    }

    // --- Carapace miroir ----------------------------------------------------------------------

    @Test
    void mirrorShellSendsBackHalfOfTheFirstBlow() {
        CombatResult result = engine().handle(strikeState(12), new PlayCardCommand(
                COMBAT_ID, 1, ENEMY, "blow-1", List.of(HERO)));

        assertThat(combatant(result.state(), ENEMY).hp())
                .describedAs("12 encaisses, 6 renvoyes")
                .isEqualTo(60 - 6);
    }

    @Test
    void mirrorShellAnswersOnlyOnce() {
        CombatEngine engine = engine();
        CombatState state = strikeState(6);

        CombatState afterFirst = engine.handle(state, new PlayCardCommand(
                COMBAT_ID, state.revision(), ENEMY, "blow-1", List.of(HERO))).state();
        int afterOne = combatant(afterFirst, ENEMY).hp();

        CombatState afterSecond = engine.handle(afterFirst, new PlayCardCommand(
                COMBAT_ID, afterFirst.revision(), ENEMY, "blow-2", List.of(HERO))).state();

        assertThat(combatant(afterSecond, ENEMY).hp())
                .describedAs("le second coup ne renvoie plus rien")
                .isEqualTo(afterOne);
    }

    /** Elle ne donne pas d'armure : c'etait une autre relique, et bien plus forte. */
    @Test
    void mirrorShellGrantsNoArmourAtTurnStart() {
        CombatState state = created("MirrorShellRelic").state();

        assertThat(combatant(state, HERO).armor()).isZero();
    }

    // --- helpers ------------------------------------------------------------------------------

    private static CombatState strikeState(int damage) {
        CombatantState hero = new CombatantState(
                HERO, "players", ControllerType.HUMAN, 60, 60, 0, BASE_ENERGY, BASE_ENERGY, 10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of("MirrorShellRelic"), null, 0);
        CombatantState enemy = new CombatantState(
                ENEMY, "enemies", ControllerType.AI, 60, 60, 0, 3, 3, 10,
                new CardPiles(List.of(),
                        List.of(new CardInstance("blow-1", CARD), new CardInstance("blow-2", CARD)),
                        List.of(), List.of()),
                List.of(), List.of(), null, 0);
        return new CombatState(
                COMBAT_ID, 1, "v1", CombatStatus.ACTIVE, null, new RandomState(5),
                List.of(hero, enemy),
                List.of(new TimelineEntry(ENEMY, 0, 1), new TimelineEntry(HERO, 10, 2)),
                ENEMY);
    }

    private static CombatState backToTheHero(CombatState from) {
        CombatEngine engine = engine();
        CombatState state = engine.handle(from, new EndTurnCommand(
                COMBAT_ID, from.revision(), HERO)).state();
        while (!HERO.equals(state.activeCombatantId())
                && state.status() == CombatStatus.ACTIVE) {
            state = engine.handle(state, new EndTurnCommand(
                    COMBAT_ID, state.revision(), state.activeCombatantId())).state();
        }
        return state;
    }

    private static CombatCreated created(String relicId) {
        return new CombatFactory(catalog(), relics()).create(new CombatSetup(
                COMBAT_ID, "v1", 99L,
                List.of(
                        new CombatantSetup(HERO, "players", ControllerType.HUMAN, 60, BASE_ENERGY,
                                10, 0, deck(), List.of(relicId), List.of(), 0),
                        new CombatantSetup(ENEMY, "enemies", ControllerType.AI, 60, 0,
                                10, 10, deck(), List.of(), List.of(), 0))));
    }

    private static CombatEngine engine() {
        return new CombatEngine(catalog(), relics());
    }

    private static RelicEngine relics() {
        return new RelicEngine(List.of(new SolarCoreRelic(), new MirrorShellRelic()));
    }

    private static CardCatalog catalog() {
        CardDefinition strike = new CardDefinition(
                CARD, 0, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 12)));
        return new CardCatalog("v1", Map.of(strike.id(), strike));
    }

    private static List<CardInstance> deck() {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
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
