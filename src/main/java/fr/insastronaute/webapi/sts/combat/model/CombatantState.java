package fr.insastronaute.webapi.sts.combat.model;

import java.util.List;
import java.util.Objects;

public record CombatantState(
        String combatantId,
        String teamId,
        ControllerType controllerType,
        int maxHp,
        int hp,
        int armor,
        int energy,
        int baseEnergy,
        int turnDelayTicks,
        CardPiles piles,
        List<StatusInstance> statuses,
        List<String> relics,
        String intentCardId,
        int patternIndex,
        List<RelicMemory> relicMemory
) {

    public CombatantState {
        requireNonBlank(combatantId, "combatantId");
        requireNonBlank(teamId, "teamId");
        Objects.requireNonNull(controllerType, "controllerType");
        Objects.requireNonNull(piles, "piles");
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        relics = relics == null ? List.of() : List.copyOf(relics);
        // A combat saved before relics had a memory reloads without one.
        relicMemory = relicMemory == null ? List.of() : List.copyOf(relicMemory);
        requireNonNegative(maxHp, "maxHp");
        requireNonNegative(hp, "hp");
        requireNonNegative(armor, "armor");
        requireNonNegative(energy, "energy");
        requireNonNegative(baseEnergy, "baseEnergy");
        requireNonNegative(patternIndex, "patternIndex");
        if (hp > maxHp) {
            throw new IllegalArgumentException("hp must not exceed maxHp");
        }
        if (turnDelayTicks <= 0) {
            throw new IllegalArgumentException("turnDelayTicks must be positive");
        }
    }

    public CombatantState(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int hp,
            int armor,
            int energy,
            int baseEnergy,
            int turnDelayTicks,
            CardPiles piles
    ) {
        this(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy, turnDelayTicks, piles, List.of(), List.of(), null, 0, List.of());
    }

    /** The state as it was written before relics could remember anything. */
    public CombatantState(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int hp,
            int armor,
            int energy,
            int baseEnergy,
            int turnDelayTicks,
            CardPiles piles,
            List<StatusInstance> statuses,
            List<String> relics,
            String intentCardId,
            int patternIndex
    ) {
        this(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy,
                turnDelayTicks, piles, statuses, relics, intentCardId, patternIndex, List.of());
    }

    /** @return what {@code relicId} remembers under {@code key}, or zero when it remembers nothing */
    public int recall(String relicId, String key) {
        return relicMemory.stream()
                .filter(memory -> memory.relicId().equals(relicId) && memory.key().equals(key))
                .mapToInt(RelicMemory::value)
                .findFirst()
                .orElse(0);
    }

    /** @return the same combatant, with what {@code relicId} remembers under {@code key} set */
    public CombatantState remembering(String relicId, String key, int value) {
        List<RelicMemory> updated = new java.util.ArrayList<>(relicMemory.stream()
                .filter(memory -> !(memory.relicId().equals(relicId) && memory.key().equals(key)))
                .toList());
        updated.add(new RelicMemory(relicId, key, value));
        updated.sort(java.util.Comparator.comparing(RelicMemory::relicId)
                .thenComparing(RelicMemory::key));
        return new CombatantState(combatantId, teamId, controllerType, maxHp, hp, armor, energy,
                baseEnergy, turnDelayTicks, piles, statuses, relics, intentCardId, patternIndex,
                List.copyOf(updated));
    }

    public CombatantState(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int hp,
            int armor,
            int energy,
            int baseEnergy,
            int turnDelayTicks,
            CardPiles piles,
            List<StatusInstance> statuses
    ) {
        this(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy, turnDelayTicks, piles, statuses, List.of(), null, 0);
    }

    public CombatantState(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int hp,
            int armor,
            int energy,
            int baseEnergy,
            int turnDelayTicks,
            CardPiles piles,
            List<StatusInstance> statuses,
            List<String> relics
    ) {
        this(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy, turnDelayTicks, piles, statuses, relics, null, 0);
    }

    public CombatantState(
            String combatantId,
            String teamId,
            ControllerType controllerType,
            int maxHp,
            int hp,
            int armor,
            int energy,
            int baseEnergy,
            int turnDelayTicks,
            CardPiles piles,
            List<StatusInstance> statuses,
            List<String> relics,
            String intentCardId
    ) {
        this(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy, turnDelayTicks, piles, statuses, relics, intentCardId, 0);
    }

    public CombatantState withIntentCardId(String intentCardId) {
        return new CombatantState(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy, turnDelayTicks, piles, statuses, relics, intentCardId, patternIndex, relicMemory);
    }

    public CombatantState withPatternIndex(int patternIndex) {
        return new CombatantState(combatantId, teamId, controllerType, maxHp, hp, armor, energy, baseEnergy, turnDelayTicks, piles, statuses, relics, intentCardId, patternIndex, relicMemory);
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
