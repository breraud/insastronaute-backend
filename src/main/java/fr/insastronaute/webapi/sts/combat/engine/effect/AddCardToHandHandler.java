package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.GeneratedCardId;
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

public final class AddCardToHandHandler implements EffectHandler {

    private static final String NONE_PILE = "NONE";
    private static final String HAND_PILE = "HAND";

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
        int count = Math.max(1, effect.value());
        List<CardInstance> hand = new ArrayList<>(target.piles().hand());
        List<CombatEvent> events = new ArrayList<>();
        RandomState currentRandom = random;

        for (int i = 0; i < count; i++) {
            currentRandom = currentRandom.advance();
            CardInstance instance = new CardInstance(
                    GeneratedCardId.fromRandom(currentRandom), effect.cardId());
            hand.add(instance);
            events.add(new CardMoved(
                    combatId,
                    revision,
                    target.combatantId(),
                    instance.instanceId(),
                    instance.definitionId(),
                    NONE_PILE,
                    HAND_PILE,
                    hand.size() - 1
            ));
        }

        CardPiles updatedPiles = new CardPiles(
                target.piles().draw(),
                hand,
                target.piles().discard(),
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
