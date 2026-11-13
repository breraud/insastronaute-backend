package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class WarpedChronometerRelic implements RelicHandler {

    @Override
    public String id() {
        return "WarpedChronometerRelic";
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
        if (counters.cardsPlayedThisTurn().size() + 1 != 3) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        int lost = Math.min(owner.hp(), 1);
        CombatantState wounded = withHp(owner, owner.hp() - lost);
        List<CombatEvent> answered = new ArrayList<>(events);
        answered.add(new HpLost(combatId, revision, owner.combatantId(),
            owner.combatantId(), 1, lost, wounded.hp()));

        ResolutionStep drawn = RelicActions.draw(
            wounded, 2, combatants, timeline, random, combatId, revision, answered);
        CombatantState afterDraw = drawn.combatants().stream()
            .filter(combatant -> combatant.combatantId().equals(owner.combatantId()))
            .findFirst()
            .orElse(wounded);
        return RelicActions.gainEnergy(
            afterDraw, 1, drawn.combatants(), drawn.timeline(), drawn.random(),
            combatId, revision, drawn.events());
        }

        private static CombatantState withHp(CombatantState owner, int hp) {
        return new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
            owner.maxHp(), hp, owner.armor(), owner.energy(), owner.baseEnergy(),
            owner.turnDelayTicks(), owner.piles(), owner.statuses(), owner.relics(), owner.intentCardId(),
            owner.patternIndex(), owner.relicMemory());
        }
}
