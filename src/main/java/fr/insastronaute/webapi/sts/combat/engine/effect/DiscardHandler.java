package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DiscardHandler implements EffectHandler {

    private static final String HAND_PILE = "HAND";
    private static final String DISCARD_PILE = "DISCARD";

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
        int count = effect.value();
        List<CardInstance> hand = new ArrayList<>(target.piles().hand());
        List<CardInstance> discard = new ArrayList<>(target.piles().discard());
        List<CombatEvent> events = new ArrayList<>();
        RandomState currentRandom = random;

        for (int i = 0; i < count; i++) {
            if (hand.isEmpty()) {
                break;
            }
            RandomState.RandomStep step = currentRandom.nextInt(hand.size());
            int cardIndex = step.value();
            currentRandom = step.next();
            CardInstance removed = hand.remove(cardIndex);
            discard.add(removed);
            events.add(new CardMoved(
                    combatId,
                    revision,
                    target.combatantId(),
                    removed.instanceId(),
                    removed.definitionId(),
                    HAND_PILE,
                    DISCARD_PILE,
                    discard.size() - 1
            ));
        }

        CardPiles updatedPiles = new CardPiles(
                target.piles().draw(),
                hand,
                discard,
                target.piles().exhaust()
        );
        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                target.hp(),
                target.armor(),
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                updatedPiles,
                target.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        return new ResolutionStep(updated, timeline, events, currentRandom);
    }
}
