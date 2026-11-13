package fr.insastronaute.webapi.sts.dto;

import java.util.List;

/**
 * An arbitrary combat asked for by hand, to exercise the engine outside a real run.
 *
 * <p>Everything but {@code enemyIds} is optional: a blank character keeps the run's own, an
 * empty deck falls back to that character's starting deck, and no relics means no relics.
 */
public record StsDebugCombatRequest(
        String character,
        Integer maxHp,
        List<String> enemyIds,
        List<String> cardIds,
        List<String> relicIds
) {
    public StsDebugCombatRequest {
        enemyIds = enemyIds == null ? List.of() : List.copyOf(enemyIds);
        cardIds = cardIds == null ? List.of() : List.copyOf(cardIds);
        relicIds = relicIds == null ? List.of() : List.copyOf(relicIds);
    }
}
