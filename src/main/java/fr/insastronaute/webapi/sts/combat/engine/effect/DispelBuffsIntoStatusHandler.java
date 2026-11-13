package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusRemovalOutcome;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns every buff on the target into a status of the caster's choosing.
 *
 * <p>Each buff is converted on its own rather than summed first: applying one status three times
 * is not the same as applying it once with three times the numbers, because a timed status adds
 * its turns while keeping the stronger value.
 */
public final class DispelBuffsIntoStatusHandler implements EffectHandler {

    @Override
    public ResolutionStep handle(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card,
            List<StatModifier> cardModifiers
    ) {
        // Brouillage : tant qu'il le porte, le lanceur ne retire plus aucun statut, ni sur
        // lui-même ni sur autrui. Le refus se lit sur celui qui lance l'effet et non sur la
        // cible, parce que c'est bien le lanceur qui est brouillé.
        if (StatusRemovalOutcome.isDispelBlocked(combatants, sourceId)) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }
        if (effect.statusType() == null || effect.statusType().isBlank()) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        // Conversion strips a buff whole, and only true resistance stops it.
        StatusRemovalOutcome.Result dispelled = StatusRemovalOutcome.take(
                combatId,
                revision,
                target.combatantId(),
                target.statuses(),
                status -> StatusCatalog.isBuff(status) && !StatusCatalog.resistsEverything(status),
                -1,
                0,
                // Unity never counts this conversion toward ActionDispelled (EffectResolver.cs,
                // DispelBuffsIntoStatus has no effectsDispelled++), only DispelBuff/DispelDebuff/
                // DispelSpecificStatus do.
                "dispelIntoStatus");

        List<CombatEvent> events = new ArrayList<>(dispelled.events());
        List<StatusInstance> statuses = new ArrayList<>(dispelled.remaining());
        for (StatusInstance buff : dispelled.taken()) {
            int converted = effect.value()
                    * (Math.max(0, buff.value()) + Math.max(0, buff.duration()));
            if (converted <= 0) {
                continue;
            }
            StatusInstance applied = StatusOperations.insert(statuses, new StatusInstance(
                    effect.statusType(),
                    converted,
                    effect.duration(),
                    effect.cardId() != null ? effect.cardId() : card.id(),
                    statuses.size()));
            events.add(new StatusApplied(combatId, revision, sourceId, target.combatantId(),
                    applied.statusType(), applied.value(), applied.duration(), true));
        }

        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                target.hp(),
                target.armor(),
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                statuses,
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        // Pavillon : cette conversion dissipe des buffs, et un buff encadré emporté ici compte
        // comme sous DispelBuff.
        List<CombatantState> touched = StatusRemovalOutcome.rewardPavillon(
                List.of(updated), combatants, sourceId, dispelled.tookFramed(),
                combatId, revision, events);
        return new ResolutionStep(touched, timeline, events, random);
    }
}
