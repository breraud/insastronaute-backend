package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReactivePlatingRelicHandler implements RelicHandler {
    @Override
    public String id() { return "ReactivePlatingRelic"; }

    @Override
    public ResolutionStep onDamageTaken(CombatantState owner, int amount, List<CombatantState> combatants, List<TimelineEntry> timeline, RandomState random, UUID combatId, long revision, List<CombatEvent> events) {
        if (amount > 0) {
            boolean alreadyUsed = owner.statuses().stream().anyMatch(s -> s.statusType().equals("ReactivePlatingUsed"));
            if (!alreadyUsed) {
                List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
                statuses.add(new StatusInstance("ReactivePlatingUsed", 1, -1, "", 0));
                
                CombatantState updatedOwner = new CombatantState(
                        owner.combatantId(), owner.teamId(), owner.controllerType(),
                        owner.maxHp(), owner.hp(), owner.armor() + 5, owner.energy(),
                        owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                        statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(), owner.relicMemory());
                
                List<CombatantState> newCombatants = new ArrayList<>(combatants);
                for (int i = 0; i < newCombatants.size(); i++) {
                    if (newCombatants.get(i).combatantId().equals(owner.combatantId())) {
                        newCombatants.set(i, updatedOwner);
                    }
                }
                return new ResolutionStep(newCombatants, timeline, events, random);
            }
        }
        return new ResolutionStep(combatants, timeline, events, random);
    }
}
