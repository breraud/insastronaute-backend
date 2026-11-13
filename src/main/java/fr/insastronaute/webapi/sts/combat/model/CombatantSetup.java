package fr.insastronaute.webapi.sts.combat.model;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.List;
import java.util.Objects;

/**
 * A combatant as it walks into a combat.
 *
 * <p>{@code startingHp} is the health the combatant opens on, which is not always {@code maxHp}:
 * a run carries its player's wounds from one encounter to the next. Enemies are built fresh for
 * each encounter, so the convenience constructors open them at full health.
 */
public record CombatantSetup(
        String combatantId,
        String teamId,
        ControllerType controllerType,
        int maxHp,
        int startingHp,
        int baseEnergy,
        int turnDelayTicks,
        long initialReadyAtTick,
        List<CardInstance> deck,
        List<String> relics,
        List<StatusInstance> startingStatuses,
        int startingPatternIndex,
        List<RelicMemory> relicMemory
) {

    public CombatantSetup {
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(teamId, "teamId");
        Objects.requireNonNull(controllerType, "controllerType");
        requireNonNegative(maxHp, "maxHp");
        requireNonNegative(startingHp, "startingHp");
        if (startingHp > maxHp) {
            throw new IllegalArgumentException("startingHp must not exceed maxHp");
        }
        requireNonNegative(baseEnergy, "baseEnergy");
        if (turnDelayTicks <= 0) {
            throw new IllegalArgumentException("turnDelayTicks must be positive");
        }
        if (initialReadyAtTick < 0) {
            throw new IllegalArgumentException("initialReadyAtTick must not be negative");
        }
        deck = List.copyOf(deck);
        relics = relics == null ? List.of() : List.copyOf(relics);
        startingStatuses = startingStatuses == null ? List.of() : List.copyOf(startingStatuses);
        requireNonNegative(startingPatternIndex, "startingPatternIndex");
        // Ce qu'une relique rapporte des combats précédents. Vide pour presque tout le monde :
        // seules les clés qu'un handler déclare comme portées par la run arrivent jusqu'ici.
        relicMemory = relicMemory == null ? List.of() : List.copyOf(relicMemory);
    }

    /** La forme que tout ce qui n'apporte aucune mémoire de run emploie encore. */
    public CombatantSetup(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int startingHp,
            int baseEnergy,
            int turnDelayTicks,
            long initialReadyAtTick,
            List<CardInstance> deck,
            List<String> relics,
            List<StatusInstance> startingStatuses,
            int startingPatternIndex
    ) {
        this(combatantId, teamId, controllerType, maxHp, startingHp, baseEnergy, turnDelayTicks,
                initialReadyAtTick, deck, relics, startingStatuses, startingPatternIndex,
                List.of());
    }

    public CombatantSetup(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int baseEnergy,
            int turnDelayTicks,
            long initialReadyAtTick,
            List<CardInstance> deck,
            List<String> relics,
            List<StatusInstance> startingStatuses,
            int startingPatternIndex
    ) {
        this(combatantId, teamId, controllerType, maxHp, maxHp, baseEnergy, turnDelayTicks,
                initialReadyAtTick, deck, relics, startingStatuses, startingPatternIndex);
    }

    public CombatantSetup(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int baseEnergy,
            int turnDelayTicks,
            long initialReadyAtTick,
            List<CardInstance> deck,
            List<String> relics
    ) {
        this(combatantId, teamId, controllerType, maxHp, maxHp, baseEnergy, turnDelayTicks,
                initialReadyAtTick, deck, relics, List.of(), 0);
    }

    public CombatantSetup(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int baseEnergy,
            int turnDelayTicks,
            long initialReadyAtTick,
            List<CardInstance> deck
    ) {
        this(combatantId, teamId, controllerType, maxHp, maxHp, baseEnergy, turnDelayTicks,
                initialReadyAtTick, deck, List.of(), List.of(), 0);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
