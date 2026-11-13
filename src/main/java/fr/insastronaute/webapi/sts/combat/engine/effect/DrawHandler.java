package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.DrawEventFactory;
import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;

public final class DrawHandler implements EffectHandler {

    private final fr.insastronaute.webapi.sts.combat.catalog.CardCatalog catalog;

    public DrawHandler(fr.insastronaute.webapi.sts.combat.catalog.CardCatalog catalog) {
        this.catalog = catalog;
    }

    /** For a caller with no catalog at hand: a draw that finds nothing simply draws nothing. */
    public DrawHandler() {
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
        int count = effect.value();
        // Le Désespoir n'est donné que si ce combat en connaît la carte : voir
        // PileOperations.draw.
        String despair = catalog != null
                && target.controllerType()
                        == fr.insastronaute.webapi.sts.combat.model.ControllerType.HUMAN
                && catalog.find(PileOperations.DESPAIR_CARD_ID) != null
                ? PileOperations.DESPAIR_CARD_ID
                : null;
        PileOperations.DrawResult result =
                PileOperations.draw(target.piles(), count, random, despair);
        List<CombatEvent> events = DrawEventFactory.create(
                combatId,
                revision,
                target.combatantId(),
                target.piles().hand().size(),
                result
        );
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
                result.piles(),
                target.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        return new ResolutionStep(updated, timeline, events, result.random());
    }
}
