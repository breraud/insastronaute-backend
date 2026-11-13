package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.event.StatusRemoved;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les statuts qui changent de nature quand la table change autour d'eux.
 *
 * <p>Sadisme mûrit à mesure que ses adversaires faiblissent : dès qu'un adversaire passe sous
 * 80 % de sa vie il devient Sadisme II, et sous 50 % Sadisme III. Unity le décide depuis
 * {@code SadismStatus.Update}, que chaque rafraîchissement de l'interface rappelle ; le client
 * ne le rappelle plus en combat autoritatif, et le serveur ne le faisait nulle part, si bien que
 * le statut restait au premier stade pour toute la durée du combat.
 *
 * <p>Le serveur le demande après chaque effet résolu et avant chaque révision construite : c'est
 * le plus près qu'il puisse venir du « aussitôt » d'Unity, et un coup qui fait passer le seuil
 * profite dès l'effet suivant du stade qu'il vient d'ouvrir.
 */
public final class StatusEvolution {

    private static final String SADISM = "Sadism";
    private static final String SADISM_II = "SadismII";
    private static final String SADISM_III = "SadismIII";

    private StatusEvolution() {
    }

    /**
     * @param events appended to in place: le stade quitté part, le suivant arrive
     * @return la table, chaque statut ayant atteint le stade que la situation lui donne
     */
    public static List<CombatantState> evolve(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        List<CombatantState> current = combatants;
        for (CombatantState holder : combatants) {
            if (holder.hp() <= 0 || holder.teamId() == null) {
                continue;
            }
            List<StatusInstance> statuses = new ArrayList<>(holder.statuses());
            boolean changed = false;
            for (int index = 0; index < statuses.size(); index++) {
                // Un stade peut en ouvrir un autre dans le même passage : un adversaire tombé
                // d'un coup sous la moitié de sa vie fait passer Sadisme directement au troisième.
                String next;
                while ((next = nextStage(statuses.get(index), holder, combatants)) != null) {
                    StatusInstance previous = statuses.get(index);
                    StatusInstance evolved = new StatusInstance(
                            next, 0, -1, previous.cardId(), previous.index())
                            .withResistanceOf(previous);
                    statuses.set(index, evolved);
                    changed = true;
                    events.add(new StatusRemoved(combatId, revision, holder.combatantId(),
                            previous.statusType(), previous.value(), 0, 0, "evolution"));
                    events.add(new StatusApplied(combatId, revision, holder.combatantId(),
                            holder.combatantId(), evolved.statusType(), evolved.value(),
                            evolved.duration(), false));
                }
            }
            if (changed) {
                current = replace(current, withStatuses(holder, statuses));
            }
        }
        return current;
    }

    /** @return the stage this status grows into now, or null when it stays as it is */
    private static String nextStage(
            StatusInstance status, CombatantState holder, List<CombatantState> combatants) {
        return switch (status.statusType()) {
            case SADISM -> anyAdversaryAtOrBelow(holder, combatants, 4, 5) ? SADISM_II : null;
            case SADISM_II -> anyAdversaryAtOrBelow(holder, combatants, 1, 2) ? SADISM_III : null;
            default -> null;
        };
    }

    /**
     * Whether a living adversary is down to {@code numerator/denominator} of its life or less,
     * asked in whole numbers so that 80 % of 21 is not rounded either way.
     */
    private static boolean anyAdversaryAtOrBelow(
            CombatantState holder,
            List<CombatantState> combatants,
            int numerator,
            int denominator
    ) {
        for (CombatantState adversary : combatants) {
            if (adversary.hp() <= 0 || holder.teamId().equals(adversary.teamId())) {
                continue;
            }
            if ((long) adversary.hp() * denominator <= (long) adversary.maxHp() * numerator) {
                return true;
            }
        }
        return false;
    }

    private static List<CombatantState> replace(
            List<CombatantState> combatants, CombatantState replacement) {
        List<CombatantState> updated = new ArrayList<>(combatants.size());
        for (CombatantState combatant : combatants) {
            updated.add(combatant.combatantId().equals(replacement.combatantId())
                    ? replacement
                    : combatant);
        }
        return updated;
    }

    private static CombatantState withStatuses(
            CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(),
                statuses, combatant.relics(), combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }
}
