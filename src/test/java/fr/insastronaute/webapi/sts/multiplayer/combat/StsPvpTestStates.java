package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PvP combat states built by the real engine, shared by the PvP combat tests.
 *
 * <p>The states come out of {@link CombatFactory}, so the timeline, the active combatant
 * and the RNG are exactly what a live PvP combat would hold rather than a hand-written
 * approximation. The PvP parameters come from {@link StsPvpCombatSetupFactory} for the same
 * reason; the {@link CombatSetup} itself is assembled here because these fixtures name their
 * combatants with readable ids instead of the user UUIDs the production factory derives them
 * from.
 */
public final class StsPvpTestStates {

    private static final UUID COMBAT_ID =
            UUID.fromString("0e5f9a2c-0000-4000-8000-000000000001");
    private static final String DATA_VERSION = "pvp-test-v1";
    private static final long SEED = 20260822L;
    private static final String STRIKE = "strike";
    private static final int DECK_SIZE = 10;
    private static final int OPENING_HAND_SIZE = 5;

    private StsPvpTestStates() {
    }

    /**
     * @return a live PvP combat where {@code firstCombatantId} is the combatant the engine
     *         made active, both combatants are alive and both hold cards in hand
     */
    public static CombatState twoHumans(String firstCombatantId, String secondCombatantId) {
        CombatState created = new CombatFactory(catalog(), new RelicEngine(List.of()))
                .create(setup(firstCombatantId, secondCombatantId))
                .state();
        // The engine only deals an opening hand to the combatant whose turn it is; the waiting
        // player is dealt one here so the fixture can stand in for a combat already under way.
        return withHandDealtTo(created, secondCombatantId);
    }

    /**
     * @return the same combat once the {@code team-1} combatant has been reduced to 0 hp, which
     *         {@link CombatState} only accepts as a finished combat won by {@code team-0}
     */
    public static CombatState secondTeamDefeated(String firstCombatantId, String secondCombatantId) {
        CombatState live = twoHumans(firstCombatantId, secondCombatantId);
        List<CombatantState> combatants = live.combatants().stream()
                .map(combatant -> combatant.combatantId().equals(secondCombatantId)
                        ? withHp(combatant, 0)
                        : combatant)
                .toList();
        String survivingTeamId = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::teamId)
                .findFirst()
                .orElseThrow();

        return new CombatState(
                live.combatId(),
                live.revision(),
                live.dataVersion(),
                CombatStatus.FINISHED,
                survivingTeamId,
                live.random(),
                combatants,
                live.timeline(),
                null,
                live.counters());
    }

    static CardCatalog catalog() {
        CardDefinition strike = new CardDefinition(
                STRIKE,
                1,
                TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, 6)));
        return new CardCatalog(DATA_VERSION, Map.of(STRIKE, strike));
    }

    static CombatSetup setup(String firstCombatantId, String secondCombatantId) {
        return new CombatSetup(
                COMBAT_ID,
                DATA_VERSION,
                SEED,
                List.of(
                        combatantSetup(firstCombatantId, 0),
                        combatantSetup(secondCombatantId, 1)));
    }

    private static CombatantSetup combatantSetup(String combatantId, int teamIndex) {
        List<CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < DECK_SIZE; index++) {
            deck.add(new CardInstance(combatantId + "-card-" + index, STRIKE));
        }
        return new CombatantSetup(
                combatantId,
                StsPvpCombatSetupFactory.teamId(teamIndex),
                ControllerType.HUMAN,
                StsPvpCombatSetupFactory.PVP_MAX_HP,
                StsPvpCombatSetupFactory.PVP_BASE_ENERGY,
                StsPvpCombatSetupFactory.PVP_TURN_DELAY_TICKS,
                0L,
                deck,
                List.of());
    }

    private static CombatState withHandDealtTo(CombatState state, String combatantId) {
        List<CombatantState> combatants = state.combatants().stream()
                .map(combatant -> combatant.combatantId().equals(combatantId)
                        ? withOpeningHand(combatant)
                        : combatant)
                .toList();
        return new CombatState(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.random(),
                combatants,
                state.timeline(),
                state.activeCombatantId(),
                state.counters());
    }

    private static CombatantState withOpeningHand(CombatantState combatant) {
        CardPiles piles = combatant.piles();
        if (!piles.hand().isEmpty()) {
            return combatant;
        }
        int dealt = Math.min(OPENING_HAND_SIZE, piles.draw().size());
        return withPiles(combatant, new CardPiles(
                piles.draw().subList(dealt, piles.draw().size()),
                piles.draw().subList(0, dealt),
                piles.discard(),
                piles.exhaust()));
    }

    private static CombatantState withPiles(CombatantState combatant, CardPiles piles) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                combatant.hp(),
                combatant.armor(),
                combatant.energy(),
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                piles,
                combatant.statuses(),
                combatant.relics(),
                combatant.intentCardId(),
                combatant.patternIndex(),
                combatant.relicMemory());
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
                combatant.piles(),
                combatant.statuses(),
                combatant.relics(),
                combatant.intentCardId(),
                combatant.patternIndex(),
                combatant.relicMemory());
    }
}
