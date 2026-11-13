package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;

/**
 * Gravite : retire a la cible un pourcentage de ses points de vie, sans que l'armure y puisse
 * quelque chose.
 *
 * <p>Le coup se portait sans que personne ne previenne les reliques de qui le prend. Tout ce qui
 * repond a « quand vous subissez des degats » restait donc muet sous une Gravite : PERF ne jouait
 * pas sa carte, l'Autoreparation ne relevait pas son porteur, la Carapace miroir ne renvoyait
 * rien. Unity, lui, appelle {@code OnDamageTaken} apres le coup comme pour n'importe quel autre
 * (EffectResolver.cs).
 */
public final class GravityHandler implements EffectHandler {

    private final fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine;

    public GravityHandler(fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine) {
        this.relicEngine = relicEngine;
    }

    public GravityHandler() {
        this.relicEngine = null;
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
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        int damage = target.hp() * effect.value() / 100;
        int hpLost = Math.min(target.hp(), damage);
        int remainingHp = target.hp() - hpLost;
        
        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                remainingHp,
                target.armor(),
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                target.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        
        DamageApplied event = new DamageApplied(
                combatId,
                revision,
                sourceId,
                target.combatantId(),
                damage,
                0, // Gravity ignores armor
                hpLost,
                target.armor(), // Armor remains unchanged
                remainingHp
        );

        java.util.List<fr.insastronaute.webapi.sts.combat.event.CombatEvent> events =
                new java.util.ArrayList<>();
        events.add(event);

        if (relicEngine == null || damage <= 0) {
            return new ResolutionStep(updated, timeline, events, random);
        }

        java.util.List<CombatantState> updatedCombatants = new java.util.ArrayList<>(combatants);
        for (int index = 0; index < updatedCombatants.size(); index++) {
            if (updatedCombatants.get(index).combatantId().equals(updated.combatantId())) {
                updatedCombatants.set(index, updated);
            }
        }
        return relicEngine.processDamageTaken(updated, damage, updatedCombatants, timeline,
                random, combatId, revision, events);
    }
}
