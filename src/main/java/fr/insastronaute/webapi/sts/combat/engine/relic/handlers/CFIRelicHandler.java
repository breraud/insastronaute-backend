package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyseur de combat : « Lorsqu'un ennemi gagne de l'Armure, votre prochaine attaque en inflige
 * la moitié en dégâts supplémentaires. »
 *
 * <p>Les deux mots qui comptent sont <em>ennemi</em> et <em>votre</em>. Le handler regardait ni
 * l'un ni l'autre : il répondait à l'armure de son propre porteur, la seule qu'on lui donnait à
 * voir, et distribuait la Vigueur à tous les humains de la table — ce qui, en duel, soignait
 * l'adversaire autant que soi.
 */
@Component
public class CFIRelicHandler implements RelicHandler {

    private static final String VIGOR = "Vigor";

    @Override
    public String id() {
        return "CFIRelic";
    }

    @Override
    public List<CombatantState> onArmorGain(
            CombatantState owner,
            CombatantState target,
            int amount,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        if (amount <= 0) return combatants;
        if (!isOpponent(owner, target)) return combatants;

        int vigorAmount = amount / 2;
        if (vigorAmount <= 0) return combatants;

        List<CombatantState> newCombatants = new ArrayList<>(combatants);
        for (int i = 0; i < newCombatants.size(); i++) {
            CombatantState c = newCombatants.get(i);
            if (!c.combatantId().equals(owner.combatantId())) {
                continue;
            }

            List<StatusInstance> statuses = new ArrayList<>(c.statuses());
            boolean found = false;
            for (int j = 0; j < statuses.size(); j++) {
                StatusInstance s = statuses.get(j);
                if (VIGOR.equals(s.statusType())) {
                    statuses.set(j, new StatusInstance(VIGOR, s.value() + vigorAmount, s.duration(), s.cardId(), s.index()));
                    found = true;
                    break;
                }
            }
            if (!found) {
                statuses.add(new StatusInstance(VIGOR, vigorAmount, -1, null, statuses.size()));
            }

            CombatantState updatedOwner = new CombatantState(
                    c.combatantId(),
                    c.teamId(),
                    c.controllerType(),
                    c.maxHp(),
                    c.hp(),
                    c.armor(),
                    c.energy(),
                    c.baseEnergy(),
                    c.turnDelayTicks(),
                    c.piles(),
                    statuses,
                    c.relics(),
                    c.intentCardId(),
                    c.patternIndex(), c.relicMemory());
            newCombatants.set(i, updatedOwner);
            break;
        }

        return newCombatants;
    }

    /**
     * Un combattant d'un autre camp que le porteur.
     *
     * <p>Les camps se lisent sur l'équipe et non sur le type de contrôleur : un duel oppose deux
     * humains, et {@code ControllerType} ne les sépare pas. Une équipe absente ne peut pas être
     * déclarée adverse — mieux vaut ne rien déclencher que de déclencher sur soi-même.
     */
    private boolean isOpponent(CombatantState owner, CombatantState target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.combatantId().equals(target.combatantId())) {
            return false;
        }
        return owner.teamId() != null && !owner.teamId().equals(target.teamId());
    }
}
