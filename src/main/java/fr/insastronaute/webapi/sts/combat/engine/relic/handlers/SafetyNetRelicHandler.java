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
public class SafetyNetRelicHandler implements RelicHandler {
    
    private static final String USED = "used";

    @Override
    public String id() {
        return "SafetyNetRelic";
    }

    @Override
    public ResolutionStep onDamageTaken(CombatantState owner, int amount, List<CombatantState> combatants, List<TimelineEntry> timeline, RandomState random, UUID combatId, long revision, List<CombatEvent> events) {
        // Once per combat, and the combat is what remembers it: a marker status would be shown
        // to the player as part of the fight, and a card could dispel it off.
        if (amount <= 0 || owner.hp() <= 0 || owner.hp() > owner.maxHp() / 2
                || owner.recall(id(), USED) != 0) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        int newHp = Math.min(owner.hp() + 4, owner.maxHp());

        CombatantState updatedOwner = new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), newHp, owner.armor(),
                owner.energy(), owner.baseEnergy(), owner.turnDelayTicks(),
                owner.piles(), owner.statuses(), owner.relics(), owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory()).remembering(id(), USED, 1);
        
        List<CombatantState> newCombatants = new ArrayList<>(combatants);
        for (int i = 0; i < newCombatants.size(); i++) {
            if (newCombatants.get(i).combatantId().equals(updatedOwner.combatantId())) {
                newCombatants.set(i, updatedOwner);
                break;
            }
        }
        
        return new ResolutionStep(newCombatants, timeline, events, random);
    }
}
