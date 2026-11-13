package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.engine.GeneratedCardId;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class MRIERelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "MRIERelic";
    }

    // The catalog's own ids, not the cards' display names. The catalog resolves either -- it
    // indexes by name as well as by id -- so a display name here failed nowhere on this side, and
    // that is precisely how it survived. The client is where it cost: it decides whether it
    // already holds a card by comparing the definitionId it was sent against the card's own id,
    // ordinally. "Météorite" never equals "meteorite", so these two cards, alone in the deck, were
    // rebuilt from scratch on every state sync and their views left behind.
    private static final String METEORITE_CARD_ID = "meteorite";
    private static final String GALAXIE_CARD_ID = "galaxie";
    private static final String NONE_PILE = "NONE";
    private static final String DRAW_PILE = "DRAW";
    private static final String DISCARD_PILE = "DISCARD";

    @Override
    public List<String> generatedCardReferences() {
        return List.of(METEORITE_CARD_ID, GALAXIE_CARD_ID);
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        long revision = state == null ? 0 : state.revision();
        CardInstance meteorite = new CardInstance(
                GeneratedCardId.fromContext("MRIERelic", revision, owner.combatantId(), 0),
                METEORITE_CARD_ID);
        CardInstance galaxie = new CardInstance(
                GeneratedCardId.fromContext("MRIERelic", revision, owner.combatantId(), 1),
                GALAXIE_CARD_ID);

        List<CardInstance> draw = new ArrayList<>(owner.piles().draw());
        draw.add(meteorite);

        List<CardInstance> discard = new ArrayList<>(owner.piles().discard());
        discard.add(galaxie);

        if (events != null && state != null) {
            events.add(new CardMoved(
                    state.combatId(),
                    state.revision(),
                    owner.combatantId(),
                    meteorite.instanceId(),
                    meteorite.definitionId(),
                    NONE_PILE,
                    DRAW_PILE,
                    draw.size() - 1
            ));
            events.add(new CardMoved(
                    state.combatId(),
                    state.revision(),
                    owner.combatantId(),
                    galaxie.instanceId(),
                    galaxie.definitionId(),
                    NONE_PILE,
                    DISCARD_PILE,
                    discard.size() - 1
            ));
        }

        CardPiles updatedPiles = new CardPiles(
                draw,
                owner.piles().hand(),
                discard,
                owner.piles().exhaust()
        );

        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor(),
                owner.energy(),
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                updatedPiles,
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }
}
