package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * Restriction applied to the candidates of a {@code CardSelection}.
 *
 * <p>Some filters read a card's authored data ({@link #ATTACK}, {@link #ATOM}, {@link #COST_1}),
 * others read the state of the instance in the combat ({@link #UPGRADED}, {@link #EXHAUSTED}).
 * Keeping both in one vocabulary matches the data; telling them apart is the resolver's job.
 */
public enum CardFilterTag {

    ATTACK("Attack"),
    SKILL("Skill"),
    POWER("Power"),
    RETAIN("Retain"),
    EXHAUSTED("Exhausted"),
    UPGRADED("Upgraded"),
    UNUPGRADED("Unupgraded"),
    COST_0("Cost0"),
    COST_1("Cost1"),
    COST_2("Cost2"),
    COST_3_PLUS("Cost3Plus"),
    ATOM("Atom"),
    MOLECULE("Molecule"),
    NORM("Norm"),
    EXHAUST("Exhaust"),
    CREATED("Created"),
    ETHEREAL("Ethereal");

    private final String jsonValue;

    CardFilterTag(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static Optional<CardFilterTag> fromJson(String value) {
        for (CardFilterTag tag : values()) {
            if (tag.jsonValue.equals(value)) {
                return Optional.of(tag);
            }
        }
        return Optional.empty();
    }
}
