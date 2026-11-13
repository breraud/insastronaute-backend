package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusRemovalOutcome;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.List;
import java.util.UUID;

/**
 * Strips buffs off the target.
 *
 * <p>A buff that resists removal only comes off when the effect is authored to ignore
 * resistance, and one with true resistance never does. The effect's duration is how much of each
 * buff is taken away rather than destroyed outright.
 */
public final class DispelBuffHandler implements EffectHandler {

        private final CardCatalog catalog;

        public DispelBuffHandler(CardCatalog catalog) {
                this.catalog = catalog;
        }

        public DispelBuffHandler() {
                this(null);
        }

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
        StatusRemovalOutcome.Result result = StatusRemovalOutcome.take(
                combatId,
                revision,
                target.combatantId(),
                target.statuses(),
                status -> StatusCatalog.isBuff(status, catalog)
                        && StatusCatalog.isDispellable(status, effect.trueEffect()),
                effect.value(),
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

        // Pavillon : dissiper un buff encadré rapporte de la Force, comme le voler ou le
        // transférer. Un buff encadré ne tombe que sous un effet qui ignore les résistances.
        List<fr.insastronaute.webapi.sts.combat.event.CombatEvent> events =
                new java.util.ArrayList<>(result.events());
        List<CombatantState> touched = StatusRemovalOutcome.rewardPavillon(
                List.of(updated), combatants, sourceId, result.tookFramed(),
                combatId, revision, events);
        return new ResolutionStep(touched, timeline, events, random);
    }
}
