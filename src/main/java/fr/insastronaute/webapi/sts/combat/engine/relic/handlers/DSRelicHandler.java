package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Annales des DS : au deuxieme tour d'un combat, son porteur gagne 16 d'armure
 * ({@code DSRelic.cs}).
 *
 * <p><strong>Au deuxieme tour, et pas a chaque tour.</strong> Elle donnait les 16 d'armure a
 * tous les tours, sous un commentaire disant qu'on manquait d'un compteur de tours et d'un
 * endroit ou retenir quelque chose entre deux tours. Les deux existent depuis :
 * {@link CombatantState#recall} et {@link CombatantState#remembering}, dont quarante-trois
 * reliques se servent deja. Le commentaire est reste, et avec lui une relique qui donnait un
 * bonus permanent la ou elle promettait un bonus au deuxieme tour.</p>
 *
 * <p>Le compte est celui des tours <strong>du porteur</strong>, pas ceux du combat. Le
 * {@code turnCount} de {@code CombatCounters} avance a chaque tour, ennemis compris, et ne dirait
 * donc pas la meme chose des que quelqu'un d'autre joue. C'est aussi ce que fait l'original C#,
 * qui compte dans son propre {@code OnTurnStart}.</p>
 */
@Component
public class DSRelicHandler implements RelicHandler {

    private static final String TURNS = "turns";
    private static final int ARMOR = 16;

    @Override
    public String id() {
        return "DSRelic";
    }

    @Override
    public CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        int turn = owner.recall(id(), TURNS) + 1;
        if (turn != 2) {
            return owner.remembering(id(), TURNS, turn);
        }
        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor() + ARMOR,
                owner.energy(),
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                owner.piles(),
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(),
                owner.relicMemory()).remembering(id(), TURNS, turn);
    }
}
