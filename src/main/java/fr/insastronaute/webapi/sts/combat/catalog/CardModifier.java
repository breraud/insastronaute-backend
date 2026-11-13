package fr.insastronaute.webapi.sts.combat.catalog;

import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.Objects;

/**
 * An authored rule that changes what one of a card's numbers comes out as.
 *
 * <p>Where an effect says what a card does, a modifier says how much: "one more damage per card
 * discarded this combat" is a modifier on the damage, not a second effect.
 *
 * @param stat  which of the card's numbers it changes
 * @param kind  what it scales on
 * @param value how much per unit of whatever it scales on
 * @param info  the extra argument some kinds need, such as a card family or a status name
 * @param temporary whether it lasts only until the turn that granted it ends, which is never
 *                  true of an authored modifier and only of one a card granted mid-combat
 */
public record CardModifier(
        StatType stat,
        ModifierKind kind,
        int value,
        String info,
        boolean temporary
) {

    public CardModifier(StatType stat, ModifierKind kind, int value, String info) {
        this(stat, kind, value, info, false);
    }

    public CardModifier {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(kind, "kind");
        info = info == null ? "" : info;
    }
}
