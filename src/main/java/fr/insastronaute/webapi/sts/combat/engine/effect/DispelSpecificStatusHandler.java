package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusRemovalOutcome;
import java.util.List;
import java.util.UUID;

public final class DispelSpecificStatusHandler implements EffectHandler {

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
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        // Brouillage : tant qu'il le porte, le lanceur ne retire plus aucun statut, ni sur
        // lui-même ni sur autrui. Le refus se lit sur celui qui lance l'effet et non sur la
        // cible, parce que c'est bien le lanceur qui est brouillé.
        if (StatusRemovalOutcome.isDispelBlocked(combatants, sourceId)) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }
        // Named removal reaches even a status a card could not otherwise strip; only true
        // resistance stops it, and the duration says how much of it is taken rather than all.
        StatusRemovalOutcome.Result result = StatusRemovalOutcome.take(
                combatId,
                revision,
                target.combatantId(),
                target.statuses(),
                status -> status.statusType().equals(effect.statusType())
                        && !StatusCatalog.resistsEverything(status),
                -1,
                effect.duration(),
                "dispel");
        
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
                result.remaining(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        // Pavillon : nommer un statut encadré pour l'emporter est une manipulation comme une
        // autre, qu'il soit buff ou debuff.
        List<fr.insastronaute.webapi.sts.combat.event.CombatEvent> events =
                new java.util.ArrayList<>(result.events());
        List<CombatantState> touched = StatusRemovalOutcome.rewardPavillon(
                List.of(updated), combatants, sourceId, result.tookFramed(),
                combatId, revision, events);
        return new ResolutionStep(touched, timeline, events, random);
    }
}
