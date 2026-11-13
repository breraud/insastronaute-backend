package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/** What an effect can be made to depend on, mirroring Unity's {@code ConditionType}. */
public enum ConditionType {

    KILLING_BLOW("KillingBlow"),
    ARMOR_BREAK("ArmorBreak"),
    FIRST_TIME_PLAYING_THIS_CARD_THIS_TURN("FirstTimePlayingThisCardThisTurn"),
    FIRST_TIME_PLAYING_THIS_CARD_THIS_COMBAT("FirstTimePlayingThisCardThisCombat"),
    TARGET_HAS_STATUS("TargetHasStatus"),
    TARGET_HAS_NO_STATUS("TargetHasNoStatus"),
    SELF_ARMOR_THRESHOLD("SelfArmorThreshold"),
    TARGET_ARMOR_THRESHOLD("TargetArmorThreshold"),
    ENERGY_GAINED_THRESHOLD("EnergyGainedThreshold"),
    ENERGY_SPENT_THRESHOLD("EnergySpentThreshold"),
    TARGET_WILL_ATTACK("TargetWillAttack"),
    TARGET_WILL_NOT_ATTACK("TargetWillNotAttack"),
    TARGET_HP_HIGHER_THAN_SELF("TargetHpHigherThanSelf"),
    TARGET_HP_LOWER_THAN_SELF("TargetHpLowerThanSelf"),
    SELF_BUFF_COUNT_THRESHOLD("SelfBuffCountThreshold"),
    TARGET_BUFF_COUNT_THRESHOLD("TargetBuffCountThreshold"),
    SELF_DEBUFF_COUNT_THRESHOLD("SelfDebuffCountThreshold"),
    TARGET_DEBUFF_COUNT_THRESHOLD("TargetDebuffCountThreshold"),
    SELF_HP_MULTIPLE("SelfHpMultiple"),
    TARGET_HP_MULTIPLE("TargetHpMultiple"),
    SELF_ARMOR_MULTIPLE("SelfArmorMultiple"),
    TARGET_ARMOR_MULTIPLE("TargetArmorMultiple"),
    SELF_TURNS_BEFORE_TARGET("SelfTurnsBeforeTarget"),

    /**
     * Le lanceur n'a plus que {@code value} points de vie, ou moins.
     *
     * <p>Écrit comme un plafond et non comme une égalité pour que « à 1 PV » couvre aussi le cas
     * où l'on y arrive par en dessous : une carte de dernier recours qui ne se déclencherait
     * qu'à 1 exactement resterait muette au moment précis où l'on en a besoin.
     */
    SELF_HP_AT_MOST("SelfHpAtMost"),

    /** La cible n'a plus que {@code value} points de vie, ou moins. */
    TARGET_HP_AT_MOST("TargetHpAtMost");

    private final String jsonValue;

    ConditionType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static Optional<ConditionType> fromJson(String value) {
        for (ConditionType type : values()) {
            if (type.jsonValue.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
