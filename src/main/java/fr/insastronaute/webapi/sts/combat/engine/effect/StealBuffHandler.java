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
 * Takes buffs off the target and puts them on the caster.
 *
 * <p>What comes off follows the same rules as a dispel, so a buff that resists removal is only
 * taken by an effect authored to ignore resistance, and the effect's duration is how much of each
 * buff moves rather than all of it.
 *
 * <p>Authored with {@code targetSelf}, it takes the target's debuffs instead: still from the
 * card's target, still onto the caster.
 */
public final class StealBuffHandler implements EffectHandler {

        private final CardCatalog catalog;

        public StealBuffHandler(CardCatalog catalog) {
                this.catalog = catalog;
        }

        public StealBuffHandler() {
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

        // Coché « sur soi », l'effet vole l'inverse : les debuffs de la cible, qui passent au
        // lanceur. La cible reste celle de la carte (EffectResolver.targetsOf).
        boolean stealsDebuffs = effect.targetSelf();
        StatusRemovalOutcome.Result stolen = StatusRemovalOutcome.take(
                combatId,
                revision,
                target.combatantId(),
                target.statuses(),
                status -> (stealsDebuffs
                                ? StatusCatalog.isDebuff(status, catalog)
                                : StatusCatalog.isBuff(status, catalog))
                        && StatusCatalog.isDispellable(status, effect.trueEffect()),
                effect.value(),
                effect.duration(),
                "steal");

        List<CombatEvent> events = new ArrayList<>(stolen.events());
        List<StatusInstance> thief = new ArrayList<>(source.statuses());
        for (StatusInstance status : stolen.taken()) {
            StatusInstance carried = StatusOperations.insert(thief, status);
            events.add(new StatusApplied(combatId, revision, target.combatantId(),
                    source.combatantId(), carried.statusType(), carried.value(), carried.duration(),
                    true));
        }

        List<CombatantState> allyUpdates = new ArrayList<>();
        int armorFromAmeArtiste = 0;
        List<StatusInstance> grants = new ArrayList<>();
        if (!stolen.taken().isEmpty()) {
            StatusInstance pavillon = StatusRemovalOutcome.pavillonGrant(
                    combatants, source.combatantId(), stolen.tookFramed());
            if (pavillon != null) {
                grants.add(pavillon);
            }
            // Âme d'artiste et Parc naturel parlent d'un effet positif volé : un debuff pris à
            // la cible ne les réveille pas.
            for (StatusInstance status : source.statuses()) {
                if ("AmeArtiste".equals(status.statusType()) && !stealsDebuffs) {
                    armorFromAmeArtiste += status.value();
                }
            }
            // Parc naturel partage le vol avec toute l'équipe, la cible volée exceptée : ce
            // qu'elle a perdu ne lui revient pas en plus par ce chemin.
            boolean hasParcNaturel = !stealsDebuffs && source.statuses().stream()
                    .anyMatch(status -> "ParcNaturel".equals(status.statusType()));
            if (hasParcNaturel && source.teamId() != null) {
                for (CombatantState combatant : combatants) {
                    if (combatant.hp() <= 0
                            || combatant.combatantId().equals(source.combatantId())
                            || combatant.combatantId().equals(target.combatantId())
                            || !source.teamId().equals(combatant.teamId())) {
                        continue;
                    }
                    List<StatusInstance> allyStatuses = new ArrayList<>(combatant.statuses());
                    for (StatusInstance status : stolen.taken()) {
                        StatusInstance applied = StatusOperations.insert(allyStatuses,
                                new StatusInstance(status.statusType(), status.value(),
                                        status.duration(), status.cardId(), status.index()));
                        events.add(new StatusApplied(combatId, revision, target.combatantId(),
                                combatant.combatantId(), applied.statusType(), applied.value(),
                                applied.duration(), true));
                    }
                    allyUpdates.add(withStatuses(combatant, allyStatuses));
                }
            }
        }
        for (StatusInstance grant : grants) {
            StatusInstance applied = StatusOperations.insert(thief, grant);
            events.add(new StatusApplied(combatId, revision, source.combatantId(),
                    source.combatantId(), applied.statusType(), applied.value(),
                    applied.duration(), false));
        }
        CombatantState updatedSource = withStatuses(source, thief);
        if (armorFromAmeArtiste > 0) {
            int armor = updatedSource.armor() + armorFromAmeArtiste;
            updatedSource = withArmor(updatedSource, armor);
            events.add(new fr.insastronaute.webapi.sts.combat.event.ArmorGained(
                    combatId, revision, source.combatantId(), source.combatantId(),
                    armorFromAmeArtiste, armorFromAmeArtiste, armor));
        }

        List<CombatantState> resultCombatants = new ArrayList<>();
        resultCombatants.add(withStatuses(target, stolen.remaining()));
        resultCombatants.add(updatedSource);
        resultCombatants.addAll(allyUpdates);

        return new ResolutionStep(
                resultCombatants,
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

    private static CombatantState withArmor(CombatantState combatant, int armor) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), armor, combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(),
                combatant.statuses(), combatant.relics(), combatant.intentCardId(), combatant.patternIndex(), combatant.relicMemory());
    }
}
