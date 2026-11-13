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
 * Study notes: the third card played in a turn draws its owner one card.
 */
@Component
public class StudyNotesRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "StudyNotesRelic";
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
        // The counters stand as they did before this card, so this one is the next along.
        if (counters.cardsPlayedThisTurn().size() + 1 != 3) {
            return new ResolutionStep(combatants, timeline, events, random);
        }
        return RelicActions.draw(
                owner, 1, combatants, timeline, random, combatId, revision, events);
    }
}
