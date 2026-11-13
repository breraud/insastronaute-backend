package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Mirrors {@code Boss/CrownOfAshesRelic.cs}: +1 energy at the start of the owner's first 3 turns.
 *
 * <p>Unity counts through {@code EnergyOnTurnStart}, called on every one of the owner's turns, and
 * so does this: {@code onTurnStart} grants and counts all three, the opening turn included, now
 * that {@code CombatFactory} runs that hook for the combatant who acts first. It used to split the
 * count across two hooks to make up for the opening turn reaching only {@code onCombatStart}; that
 * workaround would grant two energies on that turn today. The relic's own memory (not a handler
 * field, not a status) is what survives across turns and reloads.
 *
 * <p>The relic also costs 12 max HP on acquisition ({@code OnAcquire}/{@code LoseMaxHP}); that is
 * a run-level effect outside a combat's scope and is not implemented here.
 */
@Component
public class CrownOfAshesRelic implements RelicHandler {

    private static final String TURNS_GRANTED = "turnsGranted";
    private static final int MAX_TURNS = 3;

    @Override
    public String id() {
        return "CrownOfAshesRelic";
    }

    @Override
    public CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        int turnsGranted = owner.recall(id(), TURNS_GRANTED);
        if (turnsGranted >= MAX_TURNS) {
            return owner;
        }

        int newEnergy = owner.energy() + 1;
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), newEnergy,
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                owner.statuses(), owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory())
                .remembering(id(), TURNS_GRANTED, turnsGranted + 1);
    }
}
