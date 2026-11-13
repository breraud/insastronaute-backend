package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a card in play actually is: its definition plus whatever this copy picked up.
 *
 * <p>Everything downstream reads one definition, so a card that was merged, made cheaper or given
 * an extra effect during the fight is played exactly like an authored card that always looked
 * that way. Nothing has to remember to ask the instance a second question.
 */
public final class EffectiveCard {

    private EffectiveCard() {
    }

    public static CardDefinition of(CardInstance instance, CardDefinition definition) {
        if (instance == null || instance.isPlain()) {
            return definition;
        }
        Set<CardTag> tags = new LinkedHashSet<>(definition.tags());
        tags.addAll(instance.tags());
        tags.removeAll(instance.removedTags());

        List<CardModifier> modifiers = new ArrayList<>(definition.modifiers());
        modifiers.addAll(instance.modifiers());

        List<EffectDefinition> effects = new ArrayList<>(definition.effects());
        effects.addAll(instance.addedEffects());

        return new CardDefinition(
                definition.id(),
                instance.displayName() == null ? definition.name() : instance.displayName(),
                definition.favoredCharacter(),
                // A copy priced or re-classed by whatever conjured it answers with its own numbers
                // here, so every reader downstream sees them -- the follow-ups that watch what
                // kind of card was played, the pile a card lands in, the selection filters -- and
                // not only the two places that thought to ask the instance directly.
                instance.costOr(definition.cost()),
                definition.xCost(),
                instance.typeOr(definition.type()),
                instance.targetingMode() == null ? definition.targetingMode() : instance.targetingMode(),
                tags,
                modifiers,
                effects);
    }
}
