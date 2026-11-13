package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BrutalConvictionRelic implements RelicHandler {

    @Override
    public String id() {
        return "BrutalConvictionRelic";
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
        if (card.type() != CardType.ATTACK) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        CombatantState updated = owner;
        if (counters.attacksPlayedThisTurn() == 0) {
            List<StatusInstance> statuses = new ArrayList<>(updated.statuses());
            statuses.add(new StatusInstance("Strength", 1, -1, "", 0));
            updated = withStatuses(updated, statuses);
        }

        List<CombatEvent> answered = new ArrayList<>(events);
        // Unity resets its counter after the fourth attack because it checks > 3.
        if (counters.attacksPlayedThisCombat() + 1 > 3) {
            int lost = Math.min(updated.hp(), 1);
            updated = withHp(updated, updated.hp() - lost);
            answered.add(new HpLost(combatId, revision, updated.combatantId(),
                    updated.combatantId(), 1, lost, updated.hp()));
        }

        return new ResolutionStep(replace(combatants, updated), timeline, answered, random);
    }

    private static CombatantState withStatuses(CombatantState owner, List<StatusInstance> statuses) {
        return new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), statuses, owner.relics(), owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }

    private static CombatantState withHp(CombatantState owner, int hp) {
        return new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), hp, owner.armor(), owner.energy(), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), owner.statuses(), owner.relics(), owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }

    private static List<CombatantState> replace(List<CombatantState> combatants, CombatantState updated) {
        List<CombatantState> result = new ArrayList<>(combatants);
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).combatantId().equals(updated.combatantId())) {
                result.set(index, updated);
                break;
            }
        }
        return result;
    }
}
