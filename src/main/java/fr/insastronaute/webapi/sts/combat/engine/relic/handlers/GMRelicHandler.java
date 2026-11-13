package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.TimelineOperations;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GMRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "GMRelic";
    }

    @Override
    public List<TimelineEntry> onTargetArmorBroken(
            CombatantState source,
            CombatantState target,
            List<TimelineEntry> timeline,
            List<CombatEvent> events
    ) {
        List<TimelineEntry> updatedTimeline = new ArrayList<>(timeline);

        for (int i = 0; i < updatedTimeline.size(); i++) {
            TimelineEntry entry = updatedTimeline.get(i);
            if (entry.combatantId().equals(target.combatantId())) {
                updatedTimeline.set(i, new TimelineEntry(
                        entry.combatantId(),
                    entry.readyAtTick() + 10,
                        entry.sequence()
                ));
            }
        }

        return TimelineOperations.sorted(updatedTimeline);
    }
}
