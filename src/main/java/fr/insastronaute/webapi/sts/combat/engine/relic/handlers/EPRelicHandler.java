package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.engine.DrawEventFactory;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class EPRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "EPRelic";
    }

    @Override
    public TurnStart onTurnStart(
            CombatState state,
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            List<CombatEvent> events
    ) {
        int newEnergy = Math.min(owner.energy() + 1, 999); // assuming some max energy, but +1 is fine.

        // Announced, like the card it draws already is: a client that replays events rather than
        // polling state cannot show energy it was never told about.
        events.add(new fr.insastronaute.webapi.sts.combat.event.EnergyGained(
                state.combatId(), state.revision(), owner.combatantId(), 1, newEnergy));

        
        PileOperations.DrawResult draw = PileOperations.draw(owner.piles(), 1, random);
        events.addAll(DrawEventFactory.create(
                state.combatId(),
                state.revision(),
                owner.combatantId(),
                owner.piles().hand().size(),
                draw));

        // draw.random(), not the one we were handed: a draw that had to reshuffle spent it, and
        // handing back the old one would let the next reshuffle deal the very same order.
        return new TurnStart(
                new CombatantState(
                        owner.combatantId(),
                        owner.teamId(),
                        owner.controllerType(),
                        owner.maxHp(),
                        owner.hp(),
                        owner.armor(),
                        newEnergy,
                        owner.baseEnergy(),
                        owner.turnDelayTicks(),
                        draw.piles(),
                        owner.statuses(),
                        owner.relics(),
                        owner.intentCardId(),
                        owner.patternIndex(), owner.relicMemory()),
                draw.random());
    }
}
