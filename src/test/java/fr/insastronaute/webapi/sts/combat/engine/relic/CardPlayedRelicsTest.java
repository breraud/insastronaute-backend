package fr.insastronaute.webapi.sts.combat.engine.relic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.ScholarSequenceRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SpeedCadenceRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.StudyNotesRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.SurgeSequenceRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The relics that answer a card being played.
 *
 * <p>Fourteen relics in Unity hang off {@code OnCardPlayed}. The hook did not exist on the Java
 * interface, so four handlers carried the behaviour in a method nothing ever called, and the
 * others were empty stubs. What those relics read — how many cards and attacks this turn, what
 * kind of card came before — belongs to the combat, not to a handler: the handlers are singletons
 * and the state has to survive being saved and reloaded.
 */
class CardPlayedRelicsTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";

    @Test
    void surgeSequenceGivesEnergyForAnAttackPlayedRightAfterAPower() {
        CombatResult result = play(
                new SurgeSequenceRelicHandler(),
                "SurgeSequenceRelic",
                attack(),
                counters().afterPlaying("earlier", "some-power", CardType.POWER));

        assertThat(hero(result).energy()).isEqualTo(3);
    }

    @Test
    void surgeSequenceStaysQuietForAnAttackAfterAnotherAttack() {
        CombatResult result = play(
                new SurgeSequenceRelicHandler(),
                "SurgeSequenceRelic",
                attack(),
                counters().afterPlaying("earlier", "some-attack", CardType.ATTACK));

        assertThat(hero(result).energy()).isEqualTo(2);
    }

    @Test
    void speedCadenceGrantsSpeedOnEveryThirdAttackOfTheTurn() {
        CombatResult result = play(
                new SpeedCadenceRelicHandler(),
                "SpeedCadenceRelic",
                attack(),
                counters()
                        .afterPlaying("a", "some-attack", CardType.ATTACK)
                        .afterPlaying("b", "some-attack", CardType.ATTACK));

        assertThat(hero(result).statuses())
                .anySatisfy(status -> assertThat(status.statusType()).isEqualTo("Speed"));
    }

    @Test
    void strengthCadenceGrantsStrengthOnEveryThirdAttackOfTheTurn() {
        CombatResult result = play(
                new fr.insastronaute.webapi.sts.combat.engine.relic.handlers
                        .StrengthCadenceRelicHandler(),
                "StrengthCadenceRelic",
                attack(),
                counters()
                        .afterPlaying("a", "some-attack", CardType.ATTACK)
                        .afterPlaying("b", "some-attack", CardType.ATTACK));

        assertThat(hero(result).statuses())
                .anySatisfy(status -> assertThat(status.statusType()).isEqualTo("Strength"));
    }

    @Test
    void dexterityCadenceStaysQuietOnTheSecondAttackOfTheTurn() {
        CombatResult result = play(
                new fr.insastronaute.webapi.sts.combat.engine.relic.handlers
                        .DexterityCadenceRelicHandler(),
                "DexterityCadenceRelic",
                attack(),
                counters().afterPlaying("a", "some-attack", CardType.ATTACK));

        assertThat(hero(result).statuses()).isEmpty();
    }

    @Test
    void studyNotesDrawsOnTheThirdCardOfTheTurn() {
        CombatResult result = play(
                new StudyNotesRelicHandler(),
                "StudyNotesRelic",
                attack(),
                counters()
                        .afterPlaying("a", "whatever", CardType.SKILL)
                        .afterPlaying("b", "whatever", CardType.SKILL));

        assertThat(hero(result).piles().hand())
                .extracting(CardInstance::instanceId)
                .contains("spare-1");
    }

    @Test
    void scholarSequenceDrawsTwoForAPowerPlayedRightAfterASkill() {
        CombatResult result = play(
                new ScholarSequenceRelicHandler(),
                "ScholarSequenceRelic",
                power(),
                counters().afterPlaying("earlier", "some-skill", CardType.SKILL));

        assertThat(hero(result).piles().hand()).hasSize(2);
    }

    private static CombatantState hero(CombatResult result) {
        return result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(HERO))
                .findFirst()
                .orElseThrow();
    }

    private static CombatCounters counters() {
        return CombatCounters.initial();
    }

    private static CardDefinition attack() {
        return new CardDefinition(
                "played-card", 1, CardType.ATTACK, TargetingMode.SINGLE_ENEMY, java.util.Set.of(),
                List.of(new EffectDefinition(EffectType.DAMAGE, 1, false, null, 0, "", null)));
    }

    private static CardDefinition power() {
        return new CardDefinition(
                "played-card", 1, CardType.POWER, TargetingMode.SELF, java.util.Set.of(), List.of());
    }

    private static CombatResult play(
            RelicHandler relic,
            String relicId,
            CardDefinition definition,
            CombatCounters counters
    ) {
        CombatState state = state(relicId, counters);
        CombatEngine engine = new CombatEngine(
                new CardCatalog("v1", Map.of(definition.id(), definition)),
                new RelicEngine(List.of(relic)));
        CombatResult result = engine.handle(
                state,
                new PlayCardCommand(
                        COMBAT_ID,
                        state.revision(),
                        HERO,
                        "played",
                        definition.targetingMode() == TargetingMode.SELF
                                ? List.of(HERO)
                                : List.of(ENEMY)));
        assertThat(result.accepted()).isTrue();
        return result;
    }

    private static CombatState state(String relicId, CombatCounters counters) {
        List<CardInstance> draw = new ArrayList<>();
        draw.add(new CardInstance("spare-1", "played-card"));
        draw.add(new CardInstance("spare-2", "played-card"));
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(
                        draw,
                        List.of(new CardInstance("played", "played-card")),
                        List.of(),
                        List.of()),
                List.<StatusInstance>of(),
                List.of(relicId),
                null,
                0);
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                2,
                2,
                200,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(hero, enemy),
                List.of(new TimelineEntry(HERO, 0, 0), new TimelineEntry(ENEMY, 100, 1)),
                HERO,
                counters);
    }
}
