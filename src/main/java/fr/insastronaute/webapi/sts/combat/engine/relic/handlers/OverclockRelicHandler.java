package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Surcadencage : au debut des 2 premiers tours d'un combat, son porteur gagne 1 energie
 * ({@code OverclockRelic.cs}).
 *
 * <p><strong>Les deux premiers, et pas tous.</strong> Elle donnait l'energie a chaque tour, sous
 * un commentaire disant qu'il faudrait un compteur de tours. Il en existe un, et surtout il
 * existe {@link CombatantState#remembering}, ou une relique retient ce qu'elle a deja fait.
 * Une energie par tour pour toujours n'est pas la meme relique que deux energies en ouverture.</p>
 *
 * <p>Le compteur n'avance que lorsque la relique donne, comme dans l'original C# : les deux fois
 * ou elle sert sont les deux premiers tours du porteur, et le compte s'arrete ensuite.</p>
 */
@Component
public class OverclockRelicHandler implements RelicHandler {

    private static final String TURNS = "turns";
    private static final int TURNS_GRANTED = 2;

    @Override
    public String id() {
        return "OverclockRelic";
    }

    @Override
    public CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        int alreadyGranted = owner.recall(id(), TURNS);
        if (alreadyGranted >= TURNS_GRANTED) {
            return owner;
        }
        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor(),
                owner.energy() + 1,
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                owner.piles(),
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(),
                owner.relicMemory()).remembering(id(), TURNS, alreadyGranted + 1);
    }
}
