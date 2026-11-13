package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.engine.GeneratedCardId;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.StolenCardNaming;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Processeur transcendant: everything thrown at its bearer comes back once.
 *
 * <p>Every card the other side plays leaves a copy in the bearer's hand -- the same effects under
 * a name built from them (see {@link StolenCardNaming}), ethereal so it is gone at the end of the
 * bearer's next turn, and exhausted rather than discarded if it is played. The copy is a card
 * instance over the played card's own definition rather than a definition of its own: the effects
 * are identical, so inventing a second definition for them would put a card in the catalog that
 * nothing declared and that the client could not resolve.
 *
 * <p>Nothing is declared through {@link #generatedCardReferences()} for the same reason. The only
 * definition this relic ever names is one the other side has just played, which is in the catalog
 * already -- it had to be, for the card to be playable at all.
 */
@Component
public class ITIRelicHandler implements RelicHandler {

    /** A given card played at a given revision is torn off exactly once. */
    private static final int SOLE_COPY = 0;

    @Override
    public String id() {
        return "ITIRelic";
    }

    @Override
    public ResolutionStep onOpponentCardPlayed(
            CombatantState owner,
            CombatantState caster,
            CardDefinition card,
            CombatCounters counters,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (card == null || owner == null) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        StolenCardNaming.StolenCard stolen = StolenCardNaming.describe(card);
        CardInstance copy = new CardInstance(
                GeneratedCardId.fromContext(id(), revision, owner.combatantId(), SOLE_COPY),
                card.id(),
                List.of(),
                stolen.name(),
                stolen.targeting(),
                // Ethereal takes it away at the end of the turn it was not spent on; Exhaust
                // takes it away when it is. Either way the bearer gets it once.
                List.of(CardTag.ETHEREAL, CardTag.EXHAUST, CardTag.CREATED),
                List.of(),
                List.of(),
                stolen.type(),
                stolen.cost());

        return RelicActions.addToHand(
                owner, copy, combatants, timeline, random, combatId, revision, events);
    }

}
