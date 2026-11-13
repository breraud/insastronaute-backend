package fr.insastronaute.webapi.sts.combat.engine.selection;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import java.util.List;

/** One of the four piles a card selection reads from or writes to. */
public enum SelectionPile {

    DRAW,
    HAND,
    DISCARD,
    EXHAUST;

    public List<CardInstance> of(CardPiles piles) {
        return switch (this) {
            case DRAW -> piles.draw();
            case HAND -> piles.hand();
            case DISCARD -> piles.discard();
            case EXHAUST -> piles.exhaust();
        };
    }
}
