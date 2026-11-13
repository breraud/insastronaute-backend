package fr.insastronaute.webapi.sts.combat.engine.selection;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import java.util.Objects;

/**
 * An instance a card selection may pick, together with the pile it currently sits in.
 *
 * <p>A selection over combined sources has to move each card out of its own pile, so the origin
 * travels with the candidate rather than being re-derived after the fact.
 */
public record CardSelectionCandidate(CardInstance instance, SelectionPile pile) {

    public CardSelectionCandidate {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(pile, "pile");
    }

    public String instanceId() {
        return instance.instanceId();
    }
}
