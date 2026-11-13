package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
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
 * Moves debuffs off the caster and onto the target.
 *
 * <p>The mirror of stealing a buff: the caster hands over what is hurting it. What can be handed
 * over follows the same rules as a dispel, and the effect's duration is how much of each debuff
 * moves rather than all of it.
 *
 * <p>Authored with {@code targetSelf}, it hands over the caster's buffs instead: still from the
 * caster, still onto the card's target.
 */
public final class TransferDebuffHandler implements EffectHandler {

        private final CardCatalog catalog;

        public TransferDebuffHandler(CardCatalog catalog) {
                this.catalog = catalog;
        }

        public TransferDebuffHandler() {
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
        CombatantState source = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElse(null);
        if (source == null || source.combatantId().equals(target.combatantId())) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        // Coché « sur soi », l'effet transfère l'inverse : un buff du lanceur, donné à la cible
        // de la carte (EffectResolver.targetsOf la garde).
        boolean givesBuffs = effect.targetSelf();
        StatusRemovalOutcome.Result given = StatusRemovalOutcome.take(
                combatId,
                revision,
                source.combatantId(),
                source.statuses(),
                // Follow-ups inherit their category from the card they will play. Without the
                // catalog, a normal-card follow-up looked neutral and Architecture transferred it.
                status -> (givesBuffs
                                ? StatusCatalog.isBuff(status, catalog)
                                : !StatusCatalog.isBuff(status, catalog))
                        && StatusCatalog.isDispellable(status, effect.trueEffect()),
                effect.value(),
                effect.duration(),
                "transfer");

        List<CombatEvent> events = new ArrayList<>(given.events());
        List<StatusInstance> receiver = new ArrayList<>(target.statuses());
        for (StatusInstance status : given.taken()) {
            StatusInstance carried = StatusOperations.insert(receiver, status);
            events.add(new StatusApplied(combatId, revision, source.combatantId(),
                    target.combatantId(), carried.statusType(), carried.value(), carried.duration(),
                    true));
        }

        List<StatusInstance> giver = new ArrayList<>(given.remaining());
        if (!given.taken().isEmpty()) {
            List<StatusInstance> grants = new ArrayList<>();
            for (StatusInstance status : source.statuses()) {
                if ("GrandeOeuvre".equals(status.statusType())) {
                    grants.add(new StatusInstance("Vigor", status.value(), -1, null, 0));
                }
            }
            StatusInstance pavillon = StatusRemovalOutcome.pavillonGrant(
                    combatants, source.combatantId(), given.tookFramed());
            if (pavillon != null) {
                grants.add(pavillon);
            }
            for (StatusInstance grant : grants) {
                StatusInstance applied = StatusOperations.insert(giver, grant);
                events.add(new StatusApplied(combatId, revision, source.combatantId(),
                        source.combatantId(), applied.statusType(), applied.value(),
                        applied.duration(), false));
            }
        }

        return new ResolutionStep(
                List.of(withStatuses(source, giver), withStatuses(target, receiver)),
                timeline,
                events,
                random);
    }

    private static CombatantState withStatuses(CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(),
                statuses, combatant.relics(), combatant.intentCardId(), combatant.patternIndex(), combatant.relicMemory());
    }
}
