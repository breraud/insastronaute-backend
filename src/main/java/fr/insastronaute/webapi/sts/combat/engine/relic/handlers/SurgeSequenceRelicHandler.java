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
 * Sequencing: an Attack played right after a Power gives its owner one energy.
 */
@Component
public class SurgeSequenceRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "SurgeSequenceRelic";
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
        if (card.type() != CardType.ATTACK || counters.lastCardTypePlayed() != CardType.POWER) {
            return new ResolutionStep(combatants, timeline, events, random);
        }
        return RelicActions.gainEnergy(
                owner, 1, combatants, timeline, random, combatId, revision, events);
    }
}
