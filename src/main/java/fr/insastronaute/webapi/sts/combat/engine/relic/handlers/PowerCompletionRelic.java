package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Dossier complet: playing every Power the deck holds, over the course of one combat, grants
 * Speed equal to how many that was ({@code PowerCompletionRelic.cs}).
 *
 * <p>It used to hand out +2 base energy at every combat start instead, unconditionally and with
 * no relation to Powers at all -- the one thing its description never mentions. The deck's own
 * count of Powers exists only once, at the opening of a combat: a Power that has been played
 * leaves every pile behind (it lives on as a status, not a card), so that count is remembered
 * rather than recounted mid-combat.
 */
@Component
public class PowerCompletionRelic implements RelicHandler {

    private static final String TOTAL = "total";
    private static final String PLAYED = "played";
    private static final String TRIGGERED = "triggered";

    @Override
    public String id() { return "PowerCompletionRelic"; }

    @Override
    public CombatantState onCombatStart(
            CombatState state, CombatantState owner, CardCatalog catalog, List<CombatEvent> events) {
        int total = (int) Stream.of(
                        owner.piles().draw(), owner.piles().hand(),
                        owner.piles().discard(), owner.piles().exhaust())
                .flatMap(List::stream)
                .filter(card -> isPower(card, catalog))
                .count();
        return owner.remembering(id(), TOTAL, total);
    }

    @Override
    public ResolutionStep onCardPlayed(
            CombatantState owner,
            CardDefinition card,
            CombatCounters counters,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        int total = owner.recall(id(), TOTAL);
        if (total <= 0 || owner.recall(id(), TRIGGERED) != 0 || card.type() != CardType.POWER) {
            return new ResolutionStep(combatants, timeline, events, random);
        }
        int played = owner.recall(id(), PLAYED) + 1;
        if (played < total) {
            return replaceOnly(owner.remembering(id(), PLAYED, played), combatants, timeline, random, events);
        }
        CombatantState remembered = owner
                .remembering(id(), PLAYED, played)
                .remembering(id(), TRIGGERED, 1);
        return RelicActions.grant(
                remembered, new StatusInstance("Speed", total, -1, "", 0),
                combatants, timeline, random, combatId, revision, events);
    }

    private static ResolutionStep replaceOnly(
            CombatantState updated,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<CombatEvent> events
    ) {
        List<CombatantState> merged = combatants.stream()
                .map(combatant -> combatant.combatantId().equals(updated.combatantId()) ? updated : combatant)
                .toList();
        return new ResolutionStep(merged, timeline, events, random);
    }

    private static boolean isPower(CardInstance card, CardCatalog catalog) {
        CardDefinition definition = catalog == null ? null : catalog.find(card.definitionId());
        return card.typeOr(definition == null ? CardType.NONE : definition.type()) == CardType.POWER;
    }
}

