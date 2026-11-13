package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Carapace miroir : « La première fois que vous subissez des dégâts à chaque combat, renvoyez-en
 * la moitié. »
 *
 * <p>Le handler faisait tout autre chose : il donnait 4 d'Armure à l'ouverture de chaque tour, ne
 * renvoyait rien, et ne s'arrêtait jamais. C'était une autre relique, et une bien plus forte.
 *
 * <p>Le renvoi passe par les points de vie et non par l'armure de l'agresseur, comme
 * {@code source.TakeDamage} le fait dans Unity sur un coup qui ne vient d'aucune carte, et il
 * rend au moins 1 : la moitié d'un seul point de dégât reste un point.
 */
@Component
public class MirrorShellRelic implements RelicHandler {

    private static final String USED = "used";

    @Override
    public String id() {
        return "MirrorShellRelic";
    }

    @Override
    public ResolutionStep onDamageTaken(
            CombatantState owner,
            int amount,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (amount <= 0 || owner.recall(id(), USED) != 0) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        // Qui a frappé : le coup vient d'être porté, donc l'agresseur est le combattant actif
        // d'un autre camp. La relique ne renvoie rien à une morsure de statut, qui n'a personne
        // derrière elle -- Unity l'écarte de la même façon, sur une source nulle.
        CombatantState striker = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .filter(combatant -> owner.teamId() == null
                        || !owner.teamId().equals(combatant.teamId()))
                .findFirst()
                .orElse(null);
        if (striker == null) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        int reflected = Math.max(1, amount / 2);
        int lost = Math.min(striker.hp(), reflected);
        CombatantState wounded = withHp(striker, striker.hp() - lost);

        List<CombatantState> updated = new ArrayList<>(combatants);
        replace(updated, wounded);
        replace(updated, owner.remembering(id(), USED, 1));

        List<CombatEvent> answered = new ArrayList<>(events);
        answered.add(new HpLost(combatId, revision, owner.combatantId(),
                striker.combatantId(), reflected, lost, wounded.hp()));
        return new ResolutionStep(updated, timeline, answered, random);
    }

    private static CombatantState withHp(CombatantState combatant, int hp) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), hp, combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(),
                combatant.statuses(), combatant.relics(), combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }

    private static void replace(List<CombatantState> combatants, CombatantState replacement) {
        for (int index = 0; index < combatants.size(); index++) {
            if (combatants.get(index).combatantId().equals(replacement.combatantId())) {
                combatants.set(index, replacement);
                return;
            }
        }
    }
}
