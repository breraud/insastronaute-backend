package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LoseHpHandler implements EffectHandler {

        private final RelicEngine relicEngine;

        public LoseHpHandler(RelicEngine relicEngine) {
                this.relicEngine = relicEngine;
        }

        public LoseHpHandler() {
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
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        // A loss of -1 is not a negative loss but the whole of it: the card takes everything.
        int hpLoss = effect.value() == -1 ? target.maxHp() : effect.value();
        fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.HpLossVerdict verdict =
                fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.validateHpLoss(
                        target.statuses(), Math.max(0, hpLoss), target.hp(), combatants);
        int actualLoss = Math.min(target.hp(), verdict.hpLost());
        int remainingHp = target.hp() - actualLoss;
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
                verdict.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        HpLost event = new HpLost(
                combatId,
                revision,
                sourceId,
                target.combatantId(),
                hpLoss,
                actualLoss,
                remainingHp
        );
                List<CombatantState> updatedCombatants = new ArrayList<>(combatants);
                for (int index = 0; index < updatedCombatants.size(); index++) {
                        if (updatedCombatants.get(index).combatantId().equals(updated.combatantId())) {
                                updatedCombatants.set(index, updated);
                                break;
                        }
                }
                List<CombatEvent> events = new ArrayList<>(List.of(event));
                if (relicEngine == null || actualLoss <= 0) {
                        return new ResolutionStep(updatedCombatants, timeline, events, random);
                }
                return relicEngine.processDamageTaken(
                                updated, actualLoss, updatedCombatants, timeline, random, combatId, revision, events);
    }
}
