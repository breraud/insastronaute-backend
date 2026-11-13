package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusRemoved;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Strips statuses off a combatant and says what came away.
 *
 * <p>Dispelling, stealing and transferring differ only in what they select and what they do with
 * what they took, so the taking itself lives here: they cannot drift apart on which statuses
 * resist, how many are taken, or whether a status leaves whole or in part.
 */
public final class StatusRemovalOutcome {

    private StatusRemovalOutcome() {
    }

    /**
     * @param remaining what the combatant still carries
     * @param taken     what came off, in the order it was taken
     * @param events    one {@link StatusRemoved} per status touched
     */
    public record Result(
            List<StatusInstance> remaining,
            List<StatusInstance> taken,
            List<CombatEvent> events,
            boolean tookFramed
    ) {

        public Result {
            remaining = List.copyOf(remaining);
            taken = List.copyOf(taken);
            events = List.copyOf(events);
        }

        public Result(
                List<StatusInstance> remaining,
                List<StatusInstance> taken,
                List<CombatEvent> events
        ) {
            this(remaining, taken, events, false);
        }
    }

    /**
     * Pavillon : 1 de Force pour son porteur quand l'effet qu'il vient de lancer a emporté un
     * statut encadré -- volé, transféré ou dissipé.
     *
     * @return the grant, or null when the source carries no Pavillon or took nothing framed
     */
    public static StatusInstance pavillonGrant(
            java.util.List<fr.insastronaute.webapi.sts.combat.model.CombatantState> combatants,
            String sourceId,
            boolean tookFramed
    ) {
        if (!tookFramed || combatants == null || sourceId == null) {
            return null;
        }
        boolean carriesPavillon = combatants.stream()
                .filter(combatant -> sourceId.equals(combatant.combatantId()))
                .flatMap(combatant -> combatant.statuses().stream())
                .anyMatch(status -> "Pavillon".equals(status.statusType()));
        return carriesPavillon ? new StatusInstance("Strength", 1, -1, null, 0) : null;
    }

    /**
     * Hands Pavillon's Strength to whoever dispelled something framed.
     *
     * @param touched the combatants the dispel already changed, which a step carries
     * @param roster  the whole table, where the dispeller is found when it touched nobody itself
     * @param events  appended to in place with the Strength gained
     * @return {@code touched}, with the dispeller in it once rewarded
     */
    public static List<fr.insastronaute.webapi.sts.combat.model.CombatantState> rewardPavillon(
            List<fr.insastronaute.webapi.sts.combat.model.CombatantState> touched,
            List<fr.insastronaute.webapi.sts.combat.model.CombatantState> roster,
            String sourceId,
            boolean tookFramed,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        StatusInstance grant = pavillonGrant(roster, sourceId, tookFramed);
        if (grant == null) {
            return touched;
        }
        fr.insastronaute.webapi.sts.combat.model.CombatantState source = touched.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElseGet(() -> roster.stream()
                        .filter(combatant -> combatant.combatantId().equals(sourceId))
                        .findFirst()
                        .orElse(null));
        if (source == null) {
            return touched;
        }
        List<StatusInstance> statuses = new ArrayList<>(source.statuses());
        StatusInstance applied = StatusOperations.insert(statuses, grant);
        events.add(new fr.insastronaute.webapi.sts.combat.event.StatusApplied(combatId, revision,
                sourceId, sourceId, applied.statusType(), applied.value(), applied.duration(),
                false));
        fr.insastronaute.webapi.sts.combat.model.CombatantState rewarded =
                new fr.insastronaute.webapi.sts.combat.model.CombatantState(
                        source.combatantId(), source.teamId(), source.controllerType(),
                        source.maxHp(), source.hp(), source.armor(), source.energy(),
                        source.baseEnergy(), source.turnDelayTicks(), source.piles(), statuses,
                        source.relics(), source.intentCardId(), source.patternIndex(),
                        source.relicMemory());
        List<fr.insastronaute.webapi.sts.combat.model.CombatantState> result =
                new ArrayList<>(touched);
        boolean replaced = false;
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).combatantId().equals(sourceId)) {
                result.set(index, rewarded);
                replaced = true;
            }
        }
        if (!replaced) {
            result.add(rewarded);
        }
        return result;
    }

    /**
     * @param limit             how many statuses to take, {@code -1} meaning every eligible one
     * @param carriedPercentage how much of each status leaves rather than all of it
     */
    public static Result take(
            UUID combatId,
            long revision,
            String combatantId,
            List<StatusInstance> statuses,
            Predicate<StatusInstance> eligible,
            int limit,
            int carriedPercentage,
            String reason
    ) {
        List<StatusInstance> remaining = new ArrayList<>();
        List<StatusInstance> taken = new ArrayList<>();
        List<CombatEvent> events = new ArrayList<>();
        // Lu sur l'instance avant qu'elle ne parte : la part qu'une prise partielle emporte est
        // une instance neuve, qui ne porte plus le cadre qu'une carte avait posé sur l'original.
        boolean tookFramed = false;

        for (StatusInstance status : statuses) {
            boolean withinLimit = limit < 0 || taken.size() < limit;
            if (!withinLimit || !eligible.test(status)) {
                remaining.add(status);
                continue;
            }
            tookFramed = tookFramed || StatusCatalog.isFramed(status);
            StatusOperations.Removal removal = StatusOperations.remove(status, carriedPercentage);
            taken.add(removal.removed());
            if (removal.remaining() != null) {
                remaining.add(removal.remaining());
            }
            events.add(new StatusRemoved(
                    combatId,
                    revision,
                    combatantId,
                    status.statusType(),
                    removal.removed().value(),
                    removal.remaining() == null ? 0 : removal.remaining().value(),
                    removal.remaining() == null ? 0 : removal.remaining().duration(),
                    reason));
        }
        return new Result(remaining, taken, events, tookFramed);
    }

    /**
     * Un lanceur brouillé ne retire plus de statut, à personne.
     *
     * <p>Le refus se lit sur celui qui lance l'effet, jamais sur la cible : ce que le statut
     * empêche, c'est de dissiper, pas d'être dissipé. Il vaut donc aussi pour le porteur qui
     * voudrait se nettoyer lui-même.
     */
    public static boolean isDispelBlocked(
            java.util.List<fr.insastronaute.webapi.sts.combat.model.CombatantState> combatants,
            String sourceId
    ) {
        if (combatants == null || sourceId == null) {
            return false;
        }
        return combatants.stream()
                .filter(combatant -> sourceId.equals(combatant.combatantId()))
                .flatMap(combatant -> combatant.statuses().stream())
                .anyMatch(status -> "DispelBlock".equals(status.statusType()));
    }
}
