package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CommandRejection;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineValidationTest {

    private static final RelicEngine RELIC_ENGINE = new RelicEngine(List.of());
    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final String HERO = "hero";
    private static final String ENEMY = "enemy";
    private static final String ALLY = "ally";
    private static final String SELF_CARD = "self-instance";
    private static final String ENEMY_CARD = "enemy-instance";
    private static final String EXPENSIVE_CARD = "expensive-instance";
    private static final String ANY_PLAYER_CARD = "any-player-instance";

    private static final CardCatalog CATALOG = new CardCatalog("v1", Map.of(
            "self", definition("self", 1, TargetingMode.SELF),
            "strike", definition("strike", 1, TargetingMode.SINGLE_ENEMY),
            "expensive", definition("expensive", 4, TargetingMode.SINGLE_ENEMY),
            "rally", definition("rally", 1, TargetingMode.ANY_PLAYER)));
    private final CombatEngine engine = new CombatEngine(CATALOG, RELIC_ENGINE);

    @Test
    void rejectsACommandForAnotherCombatBeforeOtherValidation() {
        CombatState state = state();
        EndTurnCommand command = new EndTurnCommand(UUID.randomUUID(), 99, "intruder");

        assertRejected(state, command, RejectionCode.COMBAT_NOT_FOUND_OR_MISMATCH);
    }

    @Test
    void rejectsAStaleRevisionBeforeTurnValidation() {
        CombatState state = state();

        assertRejected(
                state,
                new EndTurnCommand(COMBAT_ID, state.revision() + 1, "intruder"),
                RejectionCode.STALE_REVISION);
    }

    @Test
    void rejectsAStateCreatedFromAnotherCatalogVersionWithoutTransitioningIt() {
        CombatState original = state();
        CombatState mismatched = new CombatState(
                original.combatId(),
                original.revision(),
                "other-version",
                original.status(),
                original.winnerTeamId(),
                original.random(),
                original.combatants(),
                original.timeline(),
                original.activeCombatantId());

        CombatResult result = engine.handle(
                mismatched,
                new EndTurnCommand(COMBAT_ID, mismatched.revision(), HERO));

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejection().code()).isEqualTo(RejectionCode.DATA_VERSION_MISMATCH);
        assertThat(result.state()).isSameAs(mismatched);
        assertThat(result.events()).isEmpty();
    }

    @Test
    void rejectsACommandWhenCombatIsFinished() {
        CombatState active = state();
        List<CombatantState> finishedCombatants = replaceCombatants(
                active.combatants(),
                ENEMY,
                withHp(combatant(active, ENEMY), 0));
        CombatState finished = new CombatState(
                active.combatId(),
                active.revision(),
                active.dataVersion(),
                CombatStatus.FINISHED,
                "players",
                active.random(),
                finishedCombatants,
                active.timeline(),
                null);

        assertRejected(
                finished,
                new EndTurnCommand(COMBAT_ID, finished.revision(), HERO),
                RejectionCode.COMBAT_FINISHED);
    }

    @Test
    void rejectsAnActorWhoDoesNotOwnTheTurn() {
        CombatState state = state();

        assertRejected(
                state,
                new EndTurnCommand(COMBAT_ID, state.revision(), ENEMY),
                RejectionCode.NOT_ACTOR_TURN);
    }

    @Test
    void stateRejectsADeadActiveActorBeforeCommandValidation() {
        CombatState state = state();

        assertThatIllegalArgumentException().isThrownBy(
                () -> replaceCombatant(state, HERO, withHp(combatant(state, HERO), 0)));
    }

    @Test
    void rejectsACardThatIsNotInTheActorsHand() {
        CombatState state = state();

        assertRejected(
                state,
                play(state, "missing", List.of(HERO)),
                RejectionCode.CARD_NOT_IN_HAND);
    }

    @Test
    void rejectsAnUnknownCardDefinitionWithoutLeakingCatalogFailure() {
        CombatState state = replaceCardDefinition(state(), SELF_CARD, "unknown");

        CombatResult result = assertRejected(
                state,
                play(state, SELF_CARD, List.of(HERO)),
                RejectionCode.UNSUPPORTED_EFFECT);

        assertThat(result.rejection().message())
                .isEqualTo("Card definition is unavailable or unsupported")
                .doesNotContain("unknown")
                .doesNotContain("Unknown card id");
    }

    @Test
    void rejectsACardWhenEnergyIsInsufficient() {
        CombatState state = state();

        assertRejected(
                state,
                play(state, EXPENSIVE_CARD, List.of(ENEMY)),
                RejectionCode.INSUFFICIENT_ENERGY);
    }

    @Test
    void rejectsInvalidSelfTargets() {
        CombatState state = state();

        assertRejected(state, play(state, SELF_CARD, List.of()), RejectionCode.INVALID_TARGET);
        assertRejected(state, play(state, SELF_CARD, List.of(ENEMY)), RejectionCode.INVALID_TARGET);
        assertRejected(
                state,
                play(state, SELF_CARD, List.of(HERO, HERO)),
                RejectionCode.INVALID_TARGET);
    }

    @Test
    void rejectsSameTeamEnemyTarget() {
        CombatState state = state();

        assertRejected(
                state,
                play(state, ENEMY_CARD, List.of(ALLY)),
                RejectionCode.INVALID_TARGET);
    }

    @Test
    void acceptsAnyPlayerTargetOnSelfOrSameTeamAlly() {
        CombatState state = state();

        assertThat(engine.handle(state, play(state, ANY_PLAYER_CARD, List.of(HERO))).accepted())
                .isTrue();
        assertThat(engine.handle(state, play(state, ANY_PLAYER_CARD, List.of(ALLY))).accepted())
                .isTrue();
    }

    @Test
    void rejectsAnyPlayerTargetOnTheOtherTeamOrSeveralTargets() {
        CombatState state = state();

        assertRejected(
                state,
                play(state, ANY_PLAYER_CARD, List.of(ENEMY)),
                RejectionCode.INVALID_TARGET);
        assertRejected(
                state,
                play(state, ANY_PLAYER_CARD, List.of(HERO, ALLY)),
                RejectionCode.INVALID_TARGET);
        assertRejected(
                state,
                play(state, ANY_PLAYER_CARD, List.of()),
                RejectionCode.INVALID_TARGET);
    }

    @Test
    void rejectsUnknownEnemyTarget() {
        CombatState state = state();

        assertRejected(
                state,
                play(state, ENEMY_CARD, List.of("missing")),
                RejectionCode.INVALID_TARGET);
    }

    @Test
    void rejectsDeadEnemyTarget() {
        CombatState original = state();
        CombatantState backupEnemy = new CombatantState(
                "backup-enemy",
                "enemies",
                ControllerType.AI,
                10,
                10,
                0,
                1,
                1,
                10,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        List<CombatantState> combatants = new ArrayList<>(replaceCombatants(
                original.combatants(),
                ENEMY,
                withHp(combatant(original, ENEMY), 0)));
        combatants.add(backupEnemy);
        CombatState state = new CombatState(
                original.combatId(),
                original.revision(),
                original.dataVersion(),
                original.status(),
                original.winnerTeamId(),
                original.random(),
                combatants,
                List.of(
                        new TimelineEntry(HERO, 0, 0),
                        new TimelineEntry(ALLY, 1, 1),
                        new TimelineEntry(ENEMY, 2, 2),
                        new TimelineEntry("backup-enemy", 3, 3)),
                HERO);

        assertRejected(
                state,
                play(state, ENEMY_CARD, List.of(ENEMY)),
                RejectionCode.INVALID_TARGET);
    }

    @Test
    void rejectsMultipleEnemyTargets() {
        CombatState state = state();

        assertRejected(
                state,
                play(state, ENEMY_CARD, List.of(ENEMY, ENEMY)),
                RejectionCode.INVALID_TARGET);
    }

    @Test
    void acceptsAValidSelfCardAfterValidation() {
        CombatState state = state();

        CombatResult result = engine.handle(state, play(state, SELF_CARD, List.of(HERO)));

        assertAcceptedCardPlay(state, result);
    }

    @Test
    void acceptsAValidSingleEnemyCardAfterValidation() {
        CombatState state = state();

        CombatResult result = engine.handle(state, play(state, ENEMY_CARD, List.of(ENEMY)));

        assertAcceptedCardPlay(state, result);
    }

    @Test
    void acceptsAValidEndTurnAfterValidation() {
        CombatState state = state();

        CombatResult result =
                engine.handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), HERO));

        assertThat(result.accepted()).isTrue();
        assertThat(result.rejection()).isNull();
        assertThat(result.state().revision()).isEqualTo(state.revision() + 1);
        assertThat(result.events()).isNotEmpty();
        assertThat(state.activeCombatantId()).isEqualTo(HERO);
    }

    @Test
    void commandsAndResultsDefensivelyCopyLists() {
        List<String> targets = new ArrayList<>(List.of(HERO));
        PlayCardCommand command = new PlayCardCommand(COMBAT_ID, 0, HERO, SELF_CARD, targets);
        CombatState state = state();
        List<CombatEvent> events = new ArrayList<>(
                List.of(new TurnStarted(COMBAT_ID, 0, HERO, 0, 3)));
        CombatResult result = new CombatResult(state, events, null);

        targets.clear();
        events.clear();

        assertThat(command.targetIds()).containsExactly(HERO);
        assertThat(result.events()).hasSize(1);
        assertThatThrownBy(() -> command.targetIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.events().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEventsOnARejectedResult() {
        CombatState state = state();
        CombatEvent event = new TurnStarted(COMBAT_ID, 0, HERO, 0, 3);
        CommandRejection rejection =
                new CommandRejection(RejectionCode.STALE_REVISION, "Stale revision");

        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatResult(state, List.of(event), rejection));
    }

    @Test
    void acceptedFactoryPreservesEventsAndMarksResultAccepted() {
        CombatState state = state();
        CombatEvent event = new TurnStarted(COMBAT_ID, 0, HERO, 0, 3);

        CombatResult result = CombatResult.accepted(state, List.of(event));

        assertThat(result.state()).isSameAs(state);
        assertThat(result.events()).containsExactly(event);
        assertThat(result.rejection()).isNull();
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void rejectedFactoryDropsEventsAndMarksResultRejected() {
        CombatState state = state();

        CombatResult result =
                CombatResult.rejected(state, RejectionCode.STALE_REVISION, "Stale revision");

        assertThat(result.state()).isSameAs(state);
        assertThat(result.events()).isEmpty();
        assertThat(result.rejection())
                .isEqualTo(new CommandRejection(RejectionCode.STALE_REVISION, "Stale revision"));
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void rejectsMalformedPlayCardCommands() {
        assertThatNullPointerException().isThrownBy(
                () -> new PlayCardCommand(null, 0, HERO, SELF_CARD, List.of(HERO)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PlayCardCommand(COMBAT_ID, -1, HERO, SELF_CARD, List.of(HERO)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PlayCardCommand(COMBAT_ID, 0, " ", SELF_CARD, List.of(HERO)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PlayCardCommand(COMBAT_ID, 0, HERO, " ", List.of(HERO)));
        assertThatNullPointerException().isThrownBy(
                () -> new PlayCardCommand(COMBAT_ID, 0, HERO, SELF_CARD, null));
        assertThatNullPointerException().isThrownBy(
                () -> new PlayCardCommand(COMBAT_ID, 0, HERO, SELF_CARD,
                        new ArrayList<>(java.util.Arrays.asList(HERO, null))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PlayCardCommand(COMBAT_ID, 0, HERO, SELF_CARD, List.of(" ")));
    }

    @Test
    void rejectsMalformedEndTurnCommands() {
        assertThatNullPointerException().isThrownBy(() -> new EndTurnCommand(null, 0, HERO));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new EndTurnCommand(COMBAT_ID, -1, HERO));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new EndTurnCommand(COMBAT_ID, 0, " "));
    }

    @Test
    void combatEndedAllowsADrawButValidatesItsIdentityAndRevision() {
        assertThat(new CombatEnded(COMBAT_ID, 3, null).winnerTeamId()).isNull();
        assertThat(new CombatEnded(COMBAT_ID, 3, "players").winnerTeamId())
                .isEqualTo("players");
        assertThatNullPointerException().isThrownBy(() -> new CombatEnded(null, 3, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatEnded(COMBAT_ID, -1, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatEnded(COMBAT_ID, 3, " "));
    }

    @Test
    void rejectsInvalidEngineAndResultInputs() {
        CombatState state = state();

        assertThatNullPointerException().isThrownBy(() -> new CombatEngine(null, RELIC_ENGINE));
        assertThatNullPointerException().isThrownBy(() -> new CombatEngine(CATALOG, null));
        assertThatNullPointerException().isThrownBy(
                () -> engine.handle(null, new EndTurnCommand(COMBAT_ID, 0, HERO)));
        assertThatNullPointerException().isThrownBy(() -> engine.handle(state, null));
        assertThatNullPointerException().isThrownBy(
                () -> new CommandRejection(null, "message"));
        assertThatNullPointerException().isThrownBy(
                () -> new CommandRejection(RejectionCode.STALE_REVISION, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CommandRejection(RejectionCode.STALE_REVISION, " "));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatResult(null, List.of(), null));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatResult(state, null, null));
    }

    private CombatResult assertRejected(
            CombatState state,
            fr.insastronaute.webapi.sts.combat.command.CombatCommand command,
            RejectionCode expectedCode
    ) {
        CombatResult result = engine.handle(state, command);

        assertThat(result.accepted()).isFalse();
        assertThat(result.state()).isSameAs(state);
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.state().revision()).isEqualTo(state.revision());
        assertThat(result.events()).isEmpty();
        assertThat(result.rejection().code()).isEqualTo(expectedCode);
        assertThat(result.rejection().message()).isNotBlank();
        return result;
    }

    private static void assertAcceptedCardPlay(CombatState original, CombatResult result) {
        assertThat(result.accepted()).isTrue();
        assertThat(result.rejection()).isNull();
        assertThat(result.state().revision()).isEqualTo(original.revision() + 1);
        assertThat(result.events()).isNotEmpty();
    }

    private static CombatState state() {
        CombatSetup setup = new CombatSetup(
                COMBAT_ID,
                "v1",
                42,
                List.of(
                        new CombatantSetup(
                                HERO,
                                "players",
                                ControllerType.HUMAN,
                                80,
                                3,
                                10,
                                0,
                                List.of(
                                        new CardInstance(SELF_CARD, "self"),
                                        new CardInstance(ENEMY_CARD, "strike"),
                                        new CardInstance(EXPENSIVE_CARD, "expensive"),
                                        new CardInstance(ANY_PLAYER_CARD, "rally"))),
                        new CombatantSetup(
                                ALLY,
                                "players",
                                ControllerType.HUMAN,
                                40,
                                3,
                                10,
                                1,
                                List.of()),
                        new CombatantSetup(
                                ENEMY,
                                "enemies",
                                ControllerType.AI,
                                30,
                                2,
                                12,
                                2,
                                List.of())));
        return new CombatFactory(CATALOG, RELIC_ENGINE).create(setup).state();
    }

    private static CardDefinition definition(String id, int cost, TargetingMode targetingMode) {
        return new CardDefinition(
                id,
                cost,
                targetingMode,
                List.of(new EffectDefinition(EffectType.DAMAGE, 1)));
    }

    private static PlayCardCommand play(
            CombatState state,
            String cardInstanceId,
            List<String> targetIds
    ) {
        return new PlayCardCommand(
                state.combatId(), state.revision(), HERO, cardInstanceId, targetIds);
    }

    private static CombatantState combatant(CombatState state, String id) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static CombatState replaceCombatant(
            CombatState state,
            String id,
            CombatantState replacement
    ) {
        return new CombatState(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.random(),
                replaceCombatants(state.combatants(), id, replacement),
                state.timeline(),
                state.activeCombatantId());
    }

    private static List<CombatantState> replaceCombatants(
            List<CombatantState> combatants,
            String id,
            CombatantState replacement
    ) {
        return combatants.stream()
                .map(combatant -> combatant.combatantId().equals(id)
                        ? replacement
                        : combatant)
                .toList();
    }

    private static CombatantState withHp(CombatantState combatant, int hp) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                hp,
                combatant.armor(),
                combatant.energy(),
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                combatant.piles());
    }

    private static CombatState replaceCardDefinition(
            CombatState state,
            String instanceId,
            String definitionId
    ) {
        CombatantState hero = combatant(state, HERO);
        List<CardInstance> hand = hero.piles().hand().stream()
                .map(card -> card.instanceId().equals(instanceId)
                        ? new CardInstance(instanceId, definitionId)
                        : card)
                .toList();
        CardPiles piles = new CardPiles(
                hero.piles().draw(), hand, hero.piles().discard(), hero.piles().exhaust());
        CombatantState replacement = new CombatantState(
                hero.combatantId(),
                hero.teamId(),
                hero.controllerType(),
                hero.maxHp(),
                hero.hp(),
                hero.armor(),
                hero.energy(),
                hero.baseEnergy(),
                hero.turnDelayTicks(),
                piles);
        return replaceCombatant(state, HERO, replacement);
    }

}
