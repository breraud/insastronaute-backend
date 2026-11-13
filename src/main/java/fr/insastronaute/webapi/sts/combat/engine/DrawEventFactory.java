package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DrawEventFactory {

    /** The only pile the engine ever shuffles: the discard going back under the draw. */
    private static final String DRAW_PILE = "DRAW";

    private DrawEventFactory() {
    }

    public static List<CombatEvent> create(
            UUID combatId,
            long revision,
            String combatantId,
            int initialHandSize,
            PileOperations.DrawResult result
    ) {
        List<CombatEvent> events = new ArrayList<>();
        for (int index = 0; index < result.drawn().size(); index++) {
            if (index == result.reshuffleAfterDrawnCount()) {
                addShuffleIfNeeded(events, combatId, revision, combatantId, result);
            }
            CardInstance card = result.drawn().get(index);
            events.add(new CardDrawn(
                    combatId,
                    revision,
                    combatantId,
                    card.instanceId(),
                    card.definitionId(),
                    initialHandSize + index));
        }
        if (result.reshuffleAfterDrawnCount() == result.drawn().size()) {
            addShuffleIfNeeded(events, combatId, revision, combatantId, result);
        }
        return List.copyOf(events);
    }

    private static void addShuffleIfNeeded(
            List<CombatEvent> events,
            UUID combatId,
            long revision,
            String combatantId,
            PileOperations.DrawResult result
    ) {
        if (result.reshuffledOrder().size() > 1) {
            events.add(new PileShuffled(
                    combatId,
                    revision,
                    combatantId,
                    DRAW_PILE,
                    result.reshuffledOrder().stream()
                            .map(CardInstance::instanceId)
                            .toList()));
        }
    }
}
