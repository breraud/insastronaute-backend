package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusDefinition;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.event.StatusBlocked;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.UUID;

/**
 * Applies an authored status to a combatant.
 *
 * <p>A status only carries the numbers it is made of: applying Poison reads the duration and
 * ignores the value, applying Strength does the opposite. Storing the unread number would show
 * the player a figure the game never uses.
 */
public final class StatusHandler implements EffectHandler {

    private final RelicEngine relicEngine;
    private final CardCatalog catalog;

    public StatusHandler(RelicEngine relicEngine) {
        this(relicEngine, null);
    }

    public StatusHandler(RelicEngine relicEngine, CardCatalog catalog) {
        this.relicEngine = relicEngine;
        this.catalog = catalog;
    }

    public StatusHandler() {
        this(null, null);
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
        String statusType = effect.statusType();
        if (statusType == null || statusType.isBlank()) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }
        StatusDefinition definition = StatusCatalog.of(statusType);
        CombatantState source = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .orElse(null);
        int duration = BattleCalculator.getModifiedValue(
            effect.duration(),
            StatType.STATUS_DURATION,
            new EffectContext(null, source, target, List.of(target), card, false,
                cardModifiers, timeline, combatants));

        StatusInstance incoming = StatusCatalog.instantiate(
            statusType, effect.value(), duration, effect.cardId(),
                effect.index());
        if ("Link".equals(statusType)) {
            incoming = anchored(incoming, combatants, sourceId);
        }
        if ("Crystallize".equals(statusType) && target.statuses().stream()
                .anyMatch(status -> "Megacristal".equals(status.statusType()))) {
            incoming = new StatusInstance(incoming.statusType(), incoming.value(), incoming.duration(),
                    incoming.cardId(), incoming.index(), false, true, incoming.progress());
        }
        if (relicEngine != null) {
            incoming = relicEngine.processStatusApply(source, target, incoming);
        }

        // A relic the target carries may refuse the status outright, and may charge them for it:
        // one heals its owner for eating the first debuff of a combat.
        CombatantState gated = target;
        if (relicEngine != null) {
            fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler.StatusGate gate =
                    relicEngine.processStatusGate(target, incoming);
            gated = gate.owner() == null ? target : gate.owner();
            if (!gate.allowed()) {
                List<CombatantState> refused = new java.util.ArrayList<>(combatants);
                for (int index = 0; index < refused.size(); index++) {
                    if (refused.get(index).combatantId().equals(gated.combatantId())) {
                        refused.set(index, gated);
                    }
                }
                return new ResolutionStep(refused, timeline, List.of(new StatusBlocked(
                        combatId, revision, target.combatantId(), statusType, "relic")), random);
            }
        }
        final CombatantState holder = gated;

        StatusOperations.Application application = StatusOperations.apply(
            holder.statuses(), incoming, source == null ? List.of() : source.statuses(), catalog);

        CombatEvent event = application.applied()
                ? new StatusApplied(combatId, revision, sourceId, target.combatantId(),
                        statusType, application.result().value(), application.result().duration(),
                        application.stacked())
                : new StatusBlocked(combatId, revision, target.combatantId(), statusType,
                        StatusCatalog.isDebuff(incoming, catalog) ? "Artifact" : "Filter");

        List<StatusInstance> statuses = new java.util.ArrayList<>(application.statuses());
        int instabilityDamage = 0;
        if (application.applied() && !"Instability".equals(statusType)) {
            for (int index = statuses.size() - 1; index >= 0; index--) {
                StatusInstance status = statuses.get(index);
                if (!"Instability".equals(status.statusType())) {
                    continue;
                }
                int remaining = status.value() - 1;
                if (remaining <= 0) {
                    statuses.remove(index);
                    instabilityDamage += 5;
                } else {
                    statuses.set(index, new StatusInstance(status.statusType(), remaining,
                            status.duration(), status.cardId(), status.index(), status.removable(),
                            status.resistsEverything(), status.progress()));
                }
            }
        }
        int hp = Math.max(0, holder.hp() - instabilityDamage);

        CombatantState updated = new CombatantState(
                holder.combatantId(),
                holder.teamId(),
                holder.controllerType(),
                holder.maxHp(),
                hp,
                holder.armor(),
                holder.energy(),
                holder.baseEnergy(),
                holder.turnDelayTicks(),
                holder.piles(),
                statuses,
                holder.relics(),
                holder.intentCardId(),
                holder.patternIndex(), holder.relicMemory());

        List<CombatEvent> events = new java.util.ArrayList<>(List.of(event));
        if (instabilityDamage > 0) {
            events.add(new fr.insastronaute.webapi.sts.combat.event.HpLost(
                combatId, revision, sourceId, holder.combatantId(), instabilityDamage,
                Math.min(holder.hp(), instabilityDamage), hp));
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                combatId, revision, holder.combatantId(), "Instability", 0, 0, 0, "expiry"));
        }
        return new ResolutionStep(updated, timeline, events, random);
    }

    /**
     * Points a Link at the character it ties its holder to.
     *
     * <p>It is authored with that character's name, which the client shows and draws its icon
     * from, and the server knows nobody by name. The anchor is settled here instead, once: the
     * combatant the name happens to identify, failing that whoever cast the card, since a Link is
     * a character tying somebody's fate to its own. It is kept as a place in the roster, on the
     * status' otherwise unread index, so the authored name reaches the client untouched.
     */
    private static StatusInstance anchored(
            StatusInstance link,
            List<CombatantState> combatants,
            String sourceId
    ) {
        int position = -1;
        for (int index = 0; index < combatants.size(); index++) {
            CombatantState combatant = combatants.get(index);
            String combatantId = combatant.combatantId();
            if (combatantId.equals(link.cardId())) {
                position = index;
                break;
            }
            String intentCardId = combatant.intentCardId();
            if (intentCardId != null && intentCardId.startsWith("enemy-move:")
                    && intentCardId.substring("enemy-move:".length())
                    .startsWith(link.cardId() + ":")) {
                position = index;
                break;
            }
            if (combatantId.equals(sourceId)) {
                position = index;
            }
        }
        return new StatusInstance(link.statusType(), link.value(), link.duration(), link.cardId(),
                position + 1, link.removable(), link.resistsEverything(), link.progress());
    }
}
