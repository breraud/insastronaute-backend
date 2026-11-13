package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

public enum EffectType {
    DAMAGE,
    ARMOR,
    HEAL,
    STATUS,
    DELETE_NEXT_TURN,
    ADVANCE_TURN,
    DELAY_TURN,
    MULTIHIT,
    DRAW,
    DISCARD,
    EXHAUST,
    LOSE_HP,
    GAIN_ENERGY,
    ADD_CARD_TO_HAND,
    STEAL_BUFF,
    TRANSFER_DEBUFF,
    DISPEL_BUFF,
    DISPEL_DEBUFF,
    END_TURN,
    GRAVITY,
    BREAK,
    ADD_RANDOM_CARD,
    ADD_CARD_TO_DRAW_PILE,
    ADD_CARD_TO_DISCARD_PILE,
    CARD_SELECTION,
    FORCE_NEXT_CARD,
    DOUBLE_DEBUFFS,
    SET_STATUS_TO_MAX_VALUE,
    EXTEND_STATUSES,
    DISPEL_DEBUFFS_INTO_DAMAGE,
    DISPEL_BUFFS_INTO_STATUS,
    DISPEL_SPECIFIC_STATUS,
    ADD_COPY_OF_CARD,
    CUT_IN_TURN,
    REPLAY_CARD,

    /**
     * Rend {@code value} buffs de la cible difficiles à retirer.
     *
     * <p>Encadrer un statut, c'est le mettre hors de portée des cartes ordinaires ;
     * {@code trueEffect} l'encadre d'or, c'est-à-dire hors de portée de tout. Le pendant exact
     * des effets de dissipation, qui lisent déjà ces deux cadres pour décider ce qu'ils peuvent
     * emporter.
     */
    FRAME_BUFFS,

    /** Rend {@code value} debuffs de la cible difficiles à retirer. */
    FRAME_DEBUFFS;

    public static Optional<EffectType> fromJson(String value) {
        return Optional.ofNullable(switch (value == null ? "" : value) {
            case "Damage" -> DAMAGE;
            case "Armor" -> ARMOR;
            case "Heal" -> HEAL;
            case "Status" -> STATUS;
            case "DeleteNextTurn" -> DELETE_NEXT_TURN;
            case "AdvanceTurn" -> ADVANCE_TURN;
            case "DelayTurn" -> DELAY_TURN;
            case "Multihit" -> MULTIHIT;
            case "Draw" -> DRAW;
            case "Discard" -> DISCARD;
            case "Exhaust" -> EXHAUST;
            case "LoseHP" -> LOSE_HP;
            case "GainEnergy" -> GAIN_ENERGY;
            case "AddCardToHand" -> ADD_CARD_TO_HAND;
            case "StealBuff" -> STEAL_BUFF;
            case "TransferDebuff" -> TRANSFER_DEBUFF;
            case "DispelBuff" -> DISPEL_BUFF;
            case "DispelDebuff" -> DISPEL_DEBUFF;
            case "EndTurn" -> END_TURN;
            case "Gravity" -> GRAVITY;
            case "Break" -> BREAK;
            case "AddRandomCard" -> ADD_RANDOM_CARD;
            case "AddCardToDrawPile" -> ADD_CARD_TO_DRAW_PILE;
            case "AddCardToDiscardPile" -> ADD_CARD_TO_DISCARD_PILE;
            case "CardSelection" -> CARD_SELECTION;
            case "ForceNextCard" -> FORCE_NEXT_CARD;
            case "DoubleDebuffs" -> DOUBLE_DEBUFFS;
            case "SetStatusToMaxValue" -> SET_STATUS_TO_MAX_VALUE;
            case "ExtendStatuses" -> EXTEND_STATUSES;
            case "DispelDebuffsIntoDamage" -> DISPEL_DEBUFFS_INTO_DAMAGE;
            case "DispelBuffsIntoStatus" -> DISPEL_BUFFS_INTO_STATUS;
            case "DispelSpecificStatus" -> DISPEL_SPECIFIC_STATUS;
            case "AddCopyOfCard" -> ADD_COPY_OF_CARD;
            case "ReplayCard" -> REPLAY_CARD;
            case "CutInTurn" -> CUT_IN_TURN;
            case "FrameBuffs" -> FRAME_BUFFS;
            case "FrameDebuffs" -> FRAME_DEBUFFS;
            default -> null;
        });
    }

    /**
     * Whether an authored value makes sense for this effect.
     *
     * <p>Negative numbers are meaningful in two ways. A status can be applied in reverse, which is
     * how a card takes Strength away. The effects that take statuses off a combatant read -1 as
     * "as many as there are", as does a card selection.
     */
    public boolean acceptsValue(int value) {
        if (value >= 0) {
            return true;
        }
        return switch (this) {
            case STATUS, CARD_SELECTION -> true;
            case DISPEL_BUFF, DISPEL_DEBUFF, STEAL_BUFF, TRANSFER_DEBUFF -> value == -1;
            // Comme pour la dissipation, -1 veut dire « tous ceux qui s'y trouvent ».
            case FRAME_BUFFS, FRAME_DEBUFFS -> value == -1;
            // Losing -1 hit points means losing all of them.
            case LOSE_HP -> value == -1;
            default -> false;
        };
    }
}
