package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Offensive cadence: every third Attack played in a turn grants one Strength.
 */
@Component
public class StrengthCadenceRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "StrengthCadenceRelic";
    }

    @Override
    public ResolutionStep onCardPlayed(
            CombatantState owner,
            CardDefinition card,
            CombatCounters counters,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (card.type() != CardType.ATTACK
                || (counters.attacksPlayedThisTurn() + 1) % 3 != 0) {
            return new ResolutionStep(combatants, timeline, events, random);
        }
        return RelicActions.grant(
                owner,
                new StatusInstance("Strength", 1, -1, "", 0),
                combatants,
                timeline,
                random,
                combatId,
                revision,
                events);
    }
}
