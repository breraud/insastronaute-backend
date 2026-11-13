package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusRemovalOutcome;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DispelDebuffsIntoDamageHandler implements EffectHandler {


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
        CombatantState source = combatants.stream()
                .filter(c -> c.combatantId().equals(sourceId))
                .findFirst()
                .orElse(null);

        EffectContext ctx = new EffectContext(null, source, target, List.of(target), card, false, cardModifiers, timeline, combatants);

        // Conversion reaches a debuff a card could not otherwise strip; only true resistance
        // stops it, and the duration says how much of each debuff is taken rather than all.
        StatusRemovalOutcome.Result dispelled = StatusRemovalOutcome.take(
                combatId,
                revision,
                target.combatantId(),
                target.statuses(),
                status -> StatusCatalog.isDebuff(status) && !StatusCatalog.resistsEverything(status),
                -1,
                effect.duration(),
                // Unity never counts this conversion toward ActionDispelled (EffectResolver.cs,
                // DispelDebuffsIntoDamage has no effectsDispelled++).
                "dispelIntoDamage");

        int totalDamage = 0;
        for (StatusInstance debuff : dispelled.taken()) {
            int converted = effect.value()
                    * (Math.max(0, debuff.value()) + Math.max(0, debuff.duration()));
            totalDamage += BattleCalculator.getModifiedValue(converted, StatType.DAMAGE, ctx);
        }
        List<StatusInstance> remainingStatuses = dispelled.remaining();

        int armorAbsorbed = Math.min(target.armor(), totalDamage);
        int hpLost = Math.min(target.hp(), totalDamage - armorAbsorbed);
        int remainingArmor = target.armor() - armorAbsorbed;
        int remainingHp = target.hp() - hpLost;

        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                remainingHp,
                remainingArmor,
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                remainingStatuses,
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        List<fr.insastronaute.webapi.sts.combat.event.CombatEvent> events =
                new ArrayList<>(dispelled.events());
        if (totalDamage > 0) {
            events.add(new DamageApplied(
                    combatId,
                    revision,
                    sourceId,
                    target.combatantId(),
                    totalDamage,
                    armorAbsorbed,
                    hpLost,
                    remainingArmor,
                    remainingHp
            ));
        }

        // Pavillon : la conversion emporte bien les debuffs, cadre compris.
        List<CombatantState> touched = StatusRemovalOutcome.rewardPavillon(
                List.of(updated), combatants, sourceId, dispelled.tookFramed(),
                combatId, revision, events);
        return new ResolutionStep(touched, timeline, events, random);
    }
}
