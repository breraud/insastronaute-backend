package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Eco-cup : la premiere fois que son porteur est soigne dans un combat, il gagne 2 d'energie
 * ({@code MeditativeFocusRelic.cs}).
 *
 * <p><strong>La premiere fois, et pas chaque fois.</strong> Elle donnait les 2 d'energie a tous
 * les soins, sous un commentaire disant qu'il faudrait pouvoir retenir quelque chose d'un soin
 * au suivant. C'est exactement ce qu'est {@link CombatantState#remembering}, et quarante-trois
 * reliques s'en servaient deja pendant que celle-ci l'attendait.</p>
 *
 * <p>La memoire vit dans l'etat du combattant, donc elle s'efface avec le combat : le compte
 * repart a zero au combat suivant, ce que l'original C# obtient par son {@code OnCombatStart}.</p>
 */
@Component
public class MeditativeFocusRelicHandler implements RelicHandler {

    private static final String USED = "used";
    private static final int ENERGY = 2;

    @Override
    public HealInterceptionResult onHeal(
            CombatState state, CombatantState owner, int amount, List<CombatEvent> events) {
        if (amount <= 0 || owner.recall(id(), USED) != 0) {
            return new HealInterceptionResult(amount, owner);
        }
        CombatantState energised = new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor(),
                owner.energy() + ENERGY,
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                owner.piles(),
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(),
                owner.relicMemory()).remembering(id(), USED, 1);
        return new HealInterceptionResult(amount, energised);
    }

    @Override
    public String id() {
        return "MeditativeFocusRelic";
    }
}
