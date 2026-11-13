package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code Rare/FortifiedMomentumRelic.cs}, "Élan victorieux":
 *
 * <blockquote>La première fois du combat que vous brisez l'Armure d'un ennemi, gagnez 1 de Force
 * et 1 de Dextérité.</blockquote>
 *
 * <p>It used to be ported as {@code shouldClearArmor -> false}, which is a different relic
 * entirely: the owner's armour never fell, for ever and unconditionally. Nothing revealed it while
 * that gate was dead code -- the status tick cleared the armour again regardless -- so the relic
 * looked inert and the wrong port went unnoticed. Honouring the gate woke it up.
 *
 * <p>"La première fois du combat" lives in the relic's own memory rather than in a handler field:
 * a handler is a singleton shared by every combat on the server.
 */
@Component
public class FortifiedMomentumRelic implements RelicHandler {

    private static final String TRIGGERED = "triggered";

    @Override
    public String id() {
        return "FortifiedMomentumRelic";
    }

    @Override
    public CombatantState onTargetArmorBroken(CombatantState owner, CombatantState target) {
        if (owner.recall(id(), TRIGGERED) != 0) {
            return owner;
        }
        if (owner.combatantId().equals(target.combatantId())) {
            // Breaking your own armour is not breaking an enemy's.
            return owner;
        }

        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Strength", 1, -1, "", 0));
        statuses.add(new StatusInstance("Dexterity", 1, -1, "", 0));

        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory())
                .remembering(id(), TRIGGERED, 1);
    }
}
