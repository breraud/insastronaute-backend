package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectCondition;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.OptionalInt;

/**
 * Decides whether a conditional effect resolves.
 *
 * <p>A condition that cannot be answered is false, never true: an effect gated on something the
 * engine does not know yet must stay silent rather than fire for free.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /**
     * @param sourceCardInstanceId the instance being played, for the "first time this turn" gates
     * @param intentCardId         what the target has announced it will do, for the attack gates
     */
    public static boolean holds(
            EffectCondition condition,
            CombatCounters counters,
            CombatantState source,
            CombatantState target,
            String sourceCardInstanceId,
            String intentCardId,
            CardCatalog catalog,
            List<TimelineEntry> timeline
    ) {
        return switch (condition.type()) {
            case KILLING_BLOW -> counters.killingBlow();
            case ARMOR_BREAK -> counters.armorBroken();
            case FIRST_TIME_PLAYING_THIS_CARD_THIS_TURN ->
                    sourceCardInstanceId != null && !counters.playedThisTurn(sourceCardInstanceId);
            case FIRST_TIME_PLAYING_THIS_CARD_THIS_COMBAT ->
                    sourceCardInstanceId != null && !counters.playedThisCombat(sourceCardInstanceId);
            case TARGET_HAS_STATUS -> target != null && hasStatus(target.statuses(), condition.value());
            case TARGET_HAS_NO_STATUS -> target != null && !hasStatus(target.statuses(), condition.value());
            case SELF_ARMOR_THRESHOLD -> source != null && atLeast(source.armor(), condition.threshold());
            case TARGET_ARMOR_THRESHOLD -> target != null && atLeast(target.armor(), condition.threshold());
            case ENERGY_GAINED_THRESHOLD -> atLeast(counters.energyGainedThisTurn(), condition.threshold());
            case ENERGY_SPENT_THRESHOLD -> atLeast(counters.energySpentThisTurn(), condition.threshold());
            case TARGET_WILL_ATTACK -> willAttack(intentCardId, catalog);
            case TARGET_WILL_NOT_ATTACK -> !willAttack(intentCardId, catalog);
            case TARGET_HP_HIGHER_THAN_SELF -> source != null && target != null && target.hp() > source.hp();
            case TARGET_HP_LOWER_THAN_SELF -> source != null && target != null && target.hp() < source.hp();
            case SELF_BUFF_COUNT_THRESHOLD -> source != null && atLeast(countBuffs(source), condition.threshold());
            case TARGET_BUFF_COUNT_THRESHOLD -> target != null && atLeast(countBuffs(target), condition.threshold());
            case SELF_DEBUFF_COUNT_THRESHOLD -> source != null && atLeast(countDebuffs(source), condition.threshold());
            case TARGET_DEBUFF_COUNT_THRESHOLD -> target != null && atLeast(countDebuffs(target), condition.threshold());
            case SELF_HP_MULTIPLE -> source != null && isMultipleOrPrime(source.hp(), condition.value());
            case TARGET_HP_MULTIPLE -> target != null && isMultipleOrPrime(target.hp(), condition.value());
            case SELF_ARMOR_MULTIPLE -> source != null && isMultipleOrPrime(source.armor(), condition.value());
            case TARGET_ARMOR_MULTIPLE -> target != null && isMultipleOrPrime(target.armor(), condition.value());
            case SELF_TURNS_BEFORE_TARGET -> hasTurnsBeforeTarget(source, target, condition.threshold(), timeline);
            case SELF_HP_AT_MOST -> source != null && atMost(source.hp(), condition.threshold());
            case TARGET_HP_AT_MOST -> target != null && atMost(target.hp(), condition.threshold());
        };
    }

    private static boolean hasStatus(List<StatusInstance> statuses, String statusType) {
        return statuses.stream().anyMatch(status -> status.statusType().equals(statusType));
    }

    private static boolean atMost(int actual, OptionalInt threshold) {
        return threshold.isPresent() && actual <= threshold.getAsInt();
    }

    private static boolean atLeast(int actual, OptionalInt threshold) {
        return threshold.isPresent() && actual >= threshold.getAsInt();
    }

    private static int countBuffs(CombatantState combatant) {
        return (int) combatant.statuses().stream()
                .filter(status -> StatusCatalog.of(status.statusType()).isBuff(status.value()))
                .count();
    }

    private static int countDebuffs(CombatantState combatant) {
        return (int) combatant.statuses().stream()
                .filter(status -> StatusCatalog.of(status.statusType()).isDebuff(status.value()))
                .count();
    }

    private static boolean isMultipleOrPrime(int actual, String divisorText) {
        if (actual <= 0) {
            return false;
        }
        OptionalInt divisor = parsePositive(divisorText);
        if (divisor.isEmpty()) {
            return false;
        }
        if (divisor.getAsInt() == 1) {
            if (actual < 2) {
                return false;
            }
            for (int candidate = 2; (long) candidate * candidate <= actual; candidate++) {
                if (actual % candidate == 0) {
                    return false;
                }
            }
            return true;
        }
        return actual % divisor.getAsInt() == 0;
    }

    private static boolean hasTurnsBeforeTarget(
            CombatantState source,
            CombatantState target,
            OptionalInt requiredTurns,
            List<TimelineEntry> timeline
    ) {
        if (source == null || target == null || source.combatantId().equals(target.combatantId()) || requiredTurns.isEmpty()
                || requiredTurns.getAsInt() <= 0 || timeline == null) {
            return false;
        }
        TimelineEntry sourceEntry = timeline.stream()
                .filter(entry -> entry.combatantId().equals(source.combatantId()))
                .findFirst()
                .orElse(null);
        TimelineEntry targetEntry = timeline.stream()
                .filter(entry -> entry.combatantId().equals(target.combatantId()))
                .findFirst()
                .orElse(null);
        if (sourceEntry == null || targetEntry == null) {
            return false;
        }
        long sourceDelay = Math.max(1, source.turnDelayTicks());
        long nextSourceTurn = sourceEntry.readyAtTick() + sourceDelay;
        int turnsBeforeTarget = 0;
        while (nextSourceTurn < targetEntry.readyAtTick() && turnsBeforeTarget < requiredTurns.getAsInt()) {
            turnsBeforeTarget++;
            nextSourceTurn += sourceDelay;
        }
        return turnsBeforeTarget >= requiredTurns.getAsInt();
    }

    private static OptionalInt parsePositive(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    private static boolean willAttack(String intentCardId, CardCatalog catalog) {
        if (intentCardId == null || catalog == null) {
            return false;
        }
        CardDefinition intent = catalog.find(intentCardId);
        return intent != null && intent.type() == CardType.ATTACK;
    }
}
