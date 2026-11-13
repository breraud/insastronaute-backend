package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts the cards a {@code CardFollowUp} or {@code AnyCardFollowUp} status watches for, and says
 * when one has seen enough of them to play its card.
 *
 * <p>Unity keeps two numbers apart on these statuses: {@code maxValue}, the authored threshold,
 * and {@code Value}, a counter that starts fresh at one and resets every time the status fires.
 * {@link StatusInstance#value()} is the former; {@link StatusInstance#progress()} is the latter.
 */
public final class FollowUpEngine {

    public static final String CARD_FOLLOW_UP = "CardFollowUp";
    public static final String ANY_CARD_FOLLOW_UP = "AnyCardFollowUp";
    public static final String FIELD_TURN_FOLLOW_UP = "FieldTurnFollowUp";

    /** {@code index} meanings for {@code FieldTurnFollowUp}: any field turn, its holder's own end,
     *  its holder's own start (FieldTurnFollowUpStatus.cs). */
    public static final int ANY_FIELD_TURN_END = 0;
    public static final int OWN_TURN_END = 1;
    public static final int OWN_TURN_START = 2;

    private FollowUpEngine() {
    }

    /** A follow-up status that just reached its threshold, naming the card it plays. */
    public record Trigger(String holderCombatantId, String statusType, String cardId, int index) {
    }

    public record Advance(List<StatusInstance> statuses, List<Trigger> triggers) {
    }

    /**
     * Advances every status of {@code statusType} a combatant carries against the card just
     * played, and reports the ones that fired.
     *
     * <p>A card itself born of a follow-up never counts, the same way Unity's {@code FollowUp}
     * tag keeps a triggered card from triggering another.
     */
    public static Advance advance(
            String holderCombatantId,
            List<StatusInstance> statuses,
            String statusType,
            CardType playedType,
            boolean playedCardIsFollowUp
    ) {
        if (playedCardIsFollowUp) {
            return new Advance(statuses, List.of());
        }
        List<StatusInstance> updated = new ArrayList<>(statuses.size());
        List<Trigger> triggers = new ArrayList<>();
        for (StatusInstance status : statuses) {
            if (!statusType.equals(status.statusType()) || !watches(status.index(), playedType)) {
                updated.add(status);
                continue;
            }
            int threshold = Math.max(1, status.value());
            int progress = status.progress() + 1;
            if (progress < threshold) {
                updated.add(status.withProgress(progress));
                continue;
            }
            int duration = status.duration() > 0 ? status.duration() - 1 : status.duration();
            if (duration != 0) {
                updated.add(new StatusInstance(status.statusType(), status.value(), duration,
                        status.cardId(), status.index()).withResistanceOf(status));
            }
            triggers.add(new Trigger(holderCombatantId, statusType, status.cardId(), status.index()));
        }
        return new Advance(updated, triggers);
    }

    /**
     * Advances every {@code FieldTurnFollowUp} a combatant carries that watches {@code reason}
     * (0=any field turn end, 1=this holder's own turn end, 2=this holder's own turn start), and
     * reports the ones that fired.
     */
    public static Advance advanceFieldTurnFollowUp(
            String holderCombatantId,
            List<StatusInstance> statuses,
            int reason
    ) {
        List<StatusInstance> updated = new ArrayList<>(statuses.size());
        List<Trigger> triggers = new ArrayList<>();
        for (StatusInstance status : statuses) {
            if (!FIELD_TURN_FOLLOW_UP.equals(status.statusType()) || status.index() != reason) {
                updated.add(status);
                continue;
            }
            int threshold = Math.max(1, status.value());
            int progress = status.progress() + 1;
            if (progress < threshold) {
                updated.add(status.withProgress(progress));
                continue;
            }
            int duration = status.duration() > 0 ? status.duration() - 1 : status.duration();
            if (duration != 0) {
                updated.add(new StatusInstance(status.statusType(), status.value(), duration,
                        status.cardId(), status.index()).withResistanceOf(status));
            }
            triggers.add(new Trigger(holderCombatantId, status.statusType(), status.cardId(), status.index()));
        }
        return new Advance(updated, triggers);
    }

    /** @return whether a follow-up restricted to one card family watches this card too */
    private static boolean watches(int index, CardType playedType) {
        return switch (index) {
            case 1 -> playedType == CardType.ATTACK;
            case 2 -> playedType == CardType.SKILL;
            case 3 -> playedType == CardType.POWER;
            default -> true;
        };
    }
}
