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

public final class AddCardToDiscardPileHandler implements EffectHandler {

    private static final String NONE_PILE = "NONE";
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
        int count = Math.max(1, effect.value());
        List<CardInstance> discard = new ArrayList<>(target.piles().discard());
        List<CombatEvent> events = new ArrayList<>();
        RandomState currentRandom = random;

        for (int i = 0; i < count; i++) {
            currentRandom = currentRandom.advance();
            CardInstance instance = new CardInstance(
                    GeneratedCardId.fromRandom(currentRandom), effect.cardId());
            // Glissée dans la défausse, pas posée dessous : ce qui remonte au remélange est
            // ainsi le même hasard que pour la pioche.
            fr.insastronaute.webapi.sts.combat.engine.PileOperations.Insertion insertion =
                    fr.insastronaute.webapi.sts.combat.engine.PileOperations.insertAtRandom(
                            discard, instance, currentRandom);
            currentRandom = insertion.random();
            events.add(new CardMoved(
                    combatId,
                    revision,
                    target.combatantId(),
                    instance.instanceId(),
                    instance.definitionId(),
                    NONE_PILE,
                    DISCARD_PILE,
                    insertion.index()
            ));
        }

        CardPiles updatedPiles = new CardPiles(
                target.piles().draw(),
                target.piles().hand(),
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
