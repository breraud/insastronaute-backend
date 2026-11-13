package fr.insastronaute.webapi.sts.combat.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Builds the {@link CardDefinition} backing one entry of an enemy {@code movePattern}.
 *
 * <p>A move either references a catalog card through {@code cardId} or declares its effects
 * inline. Both forms go through {@link EffectDefinitionParser}, so an enemy move supports exactly
 * the effects a card supports and fails on exactly the same data.
 */
public final class EnemyMoveParser {

    private final EffectDefinitionParser effectParser = new EffectDefinitionParser();

    /**
     * @return the definition for this move, or {@code null} when the move declares neither a
     *         {@code cardId} nor inline effects
     */
    public CardDefinition parseMove(
            CardCatalog baseCatalog,
            JsonNode move,
            String enemyId,
            int index
    ) {
        String cardId = move.path("cardId").asText("").trim();
        if (!cardId.isBlank()) {
            return baseCatalog.require(cardId);
        }

        String context = "enemy '" + enemyId + "'.movePattern[" + index + "]";
        List<EffectDefinition> effects = effectParser.parseEffects(move, context);
        if (effects.isEmpty()) {
            return null;
        }
        return new CardDefinition(
                moveId(enemyId, index),
                0,
                CardType.NONE,
                targetingModeFor(effects),
                java.util.Set.of(),
                effects);
    }

    /**
     * The move only needs a target when at least one of its effects is aimed at someone else.
     * Widening the whole move to SELF because one effect is self-targeted would send its damage
     * back at the caster.
     */
    private static TargetingMode targetingModeFor(List<EffectDefinition> effects) {
        boolean everyEffectIsSelfTargeted = effects.stream().allMatch(EffectDefinition::targetSelf);
        return everyEffectIsSelfTargeted ? TargetingMode.SELF : TargetingMode.SINGLE_ENEMY;
    }

    /** What every enemy move definition's id starts with: they are this engine's, not the game's. */
    public static final String MOVE_ID_PREFIX = "enemy-move:";

    public static String moveId(String enemyId, int index) {
        return MOVE_ID_PREFIX + enemyId + ":" + index;
    }
}
