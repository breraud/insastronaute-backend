package fr.insastronaute.webapi.sts.combat.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CombatSetup(
        UUID combatId,
        String dataVersion,
        long seed,
        List<CombatantSetup> combatants
) {

    public CombatSetup {
        Objects.requireNonNull(combatId, "combatId");
        if (dataVersion == null || dataVersion.isBlank()) {
            throw new IllegalArgumentException("dataVersion must not be blank");
        }
        combatants = List.copyOf(combatants);
        if (combatants.size() < 2) {
            throw new IllegalArgumentException("combat must contain at least two combatants");
        }
        if (combatants.stream().map(CombatantSetup::teamId).distinct().count() < 2) {
            throw new IllegalArgumentException("combat must contain at least two teams");
        }
        if (combatants.stream()
                .filter(combatant -> combatant.startingHp() > 0)
                .map(CombatantSetup::teamId)
                .distinct()
                .count() < 2) {
            throw new IllegalArgumentException("combat must contain at least two living teams");
        }
    }
}
