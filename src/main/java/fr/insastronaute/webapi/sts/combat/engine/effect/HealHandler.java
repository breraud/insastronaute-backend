package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.HealApplied;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;

public final class HealHandler implements EffectHandler {

    private final RelicEngine relicEngine;

    public HealHandler(RelicEngine relicEngine) {
        this.relicEngine = Objects.requireNonNull(relicEngine, "relicEngine");
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
            CardDefinition card, List<StatModifier> cardModifiers
    ) {
        CombatantState source = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElse(null);
        int healAmount = BattleCalculator.getModifiedValue(
                effect.value(),
                StatType.HEAL,
                new EffectContext(null, source, target, List.of(target), card, false,
                        cardModifiers, timeline, combatants));
        
        java.util.List<fr.insastronaute.webapi.sts.combat.event.CombatEvent> relicEvents = new java.util.ArrayList<>();
        fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler.HealInterceptionResult relicResult =
                relicEngine.processHeal(target, healAmount, relicEvents);
        
        int heal = relicResult.remainingHeal();
        CombatantState updated = relicResult.owner();
        
        // Ferveur retient le soin au lieu de le laisser passer, et le rendra d'un coup en
        // s'en allant. C'est l'exact pendant de l'Arrêt du temps, et elle intercepte au même
        // endroit : avant que le soin ne touche les points de vie.
        fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.HealDeferral deferral =
                fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction
                        .deferHealing(updated.statuses(), heal);
        if (deferral.deferred() > 0) {
            heal = deferral.remainingHeal();
            updated = withStatuses(updated, deferral.statuses());
        }

        // Undead turns care into harm: healing such a combatant wounds it for the same amount.
        boolean inverted = fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction
                .invertsHealing(updated.statuses());
        int missingHp = updated.maxHp() - updated.hp();
        int hpDelta = inverted
                ? -Math.min(heal, updated.hp())
                : Math.min(heal, missingHp);
        int remainingHp = updated.hp() + hpDelta;
        
        updated = new CombatantState(
                updated.combatantId(),
                updated.teamId(),
                updated.controllerType(),
                updated.maxHp(),
                remainingHp,
                updated.armor(),
                updated.energy(),
                updated.baseEnergy(),
                updated.turnDelayTicks(),
                updated.piles(),
                updated.statuses(),
                updated.relics(),
                updated.intentCardId(),
                updated.patternIndex(), updated.relicMemory());
        CombatEvent event = inverted
                ? new HpLost(combatId, revision, sourceId, target.combatantId(), heal,
                        -hpDelta, remainingHp)
                : new HealApplied(combatId, revision, sourceId, target.combatantId(), heal,
                        hpDelta, remainingHp);
        relicEvents.add(event);
        return new ResolutionStep(updated, timeline, relicEvents, random);
    }

    private static CombatantState withStatuses(
            CombatantState combatant,
            List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> statuses
    ) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(), statuses,
                combatant.relics(), combatant.intentCardId(), combatant.patternIndex(),
                combatant.relicMemory());
    }
}
