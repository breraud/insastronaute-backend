package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MultihitHandler implements EffectHandler {

    private final DamageHandler damageHandler;

    public MultihitHandler(fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine) {
        this.damageHandler = new DamageHandler(relicEngine);
    }

    public MultihitHandler() {
        this(null);
    }

    @Override
    public ResolutionStep handle(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        int hits = effect.duration();
        List<CombatantState> currentCombatants = combatants;
        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        List<CombatEvent> allEvents = new ArrayList<>();
        List<ResolutionStep.PendingCardPlay> pendingCardPlays = new ArrayList<>();
        
        EffectDefinition damageEffect = new EffectDefinition(
                EffectType.DAMAGE,
                effect.value(),
                effect.targetSelf(),
                effect.statusType(),
                0, // duration is not passed down to damage
                effect.description(),
                effect.cardId()
        );

        for (int hit = 0; hit < hits; hit++) {
            // The blow lands on the combatant the effect named, read back from the state each
            // hit so armor already taken off stays off. Reading the head of the roster instead
            // aimed every hit after the first at the player, who is always first in it.
            CombatantState struck = currentCombatants.stream()
                    .filter(combatant -> combatant.combatantId().equals(target.combatantId()))
                    .findFirst()
                    .orElse(target);

            ResolutionStep step = damageHandler.handle(
                    combatId,
                    revision,
                    sourceId,
                    struck,
                    damageEffect,
                    currentRandom,
                    currentCombatants,
                    currentTimeline,
                    card,
                    cardModifiers
            );

            currentCombatants = merge(currentCombatants, step.combatants());
            currentTimeline = step.timeline();
            currentRandom = step.random();
            allEvents.addAll(step.events());
            pendingCardPlays.addAll(step.pendingCardPlays());
        }

        return new ResolutionStep(
                currentCombatants, currentTimeline, allEvents, currentRandom, pendingCardPlays);
    }

    private static List<CombatantState> merge(
            List<CombatantState> current,
            List<CombatantState> updates
    ) {
        List<CombatantState> merged = new ArrayList<>(current);
        for (CombatantState update : updates) {
            for (int index = 0; index < merged.size(); index++) {
                if (merged.get(index).combatantId().equals(update.combatantId())) {
                    merged.set(index, update);
                }
            }
        }
        return List.copyOf(merged);
    }
}
