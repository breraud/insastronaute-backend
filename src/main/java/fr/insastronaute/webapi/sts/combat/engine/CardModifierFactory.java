package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.modifier.ArmorModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.ArmorOnSelfModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.ArmorOnTargetModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.FlatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.LostHpModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.SpecificStatusModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatusCountModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.TargetLostHpModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.TargetNumberModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.TimeUntilNextTurnModifier;
import java.util.List;

/**
 * Turns an authored modifier into something that can actually change a number.
 *
 * <p>Modifiers split in two. Some depend on who is being hit and are worked out while the effect
 * resolves. Others depend only on what the combat has done so far, and those are settled here,
 * once, into a flat amount: the tally cannot change halfway through resolving one card, and
 * settling them early keeps the running total out of every modifier's reach.
 */
public final class CardModifierFactory {

    private CardModifierFactory() {
    }

    public static List<StatModifier> create(
            List<CardModifier> modifiers,
            CombatCounters counters,
            String sourceId,
            CardCatalog catalog
    ) {
        return modifiers.stream()
                .map(modifier -> create(modifier, counters, sourceId, catalog))
                .toList();
    }

    private static StatModifier create(
            CardModifier modifier,
            CombatCounters counters,
            String sourceId,
            CardCatalog catalog
    ) {
        int amount = modifier.value();
        return switch (modifier.kind()) {
            case FLAT -> new FlatModifier(modifier.stat(), amount);
            case ARMOR_SCALING -> new ArmorModifier(modifier.stat(), amount);
            case TARGET_NUMBER -> new TargetNumberModifier(modifier.stat(), amount);
            case ARMOR_ON_SELF -> new ArmorOnSelfModifier(modifier.stat(), amount);
            case ARMOR_ON_TARGET -> new ArmorOnTargetModifier(modifier.stat(), amount);
            case LOST_HP -> new LostHpModifier(modifier.stat(), amount);
            case TARGET_LOST_HP -> new TargetLostHpModifier(modifier.stat(), amount);
            case TIME_UNTIL_NEXT_TURN -> new TimeUntilNextTurnModifier(modifier.stat(), amount);
            case DEBUFF_ON_SELF -> new StatusCountModifier(
                    modifier.stat(), amount, StatusCountModifier.Side.NOT_BUFF, false);
            case BUFF_ON_SELF -> new StatusCountModifier(
                    modifier.stat(), amount, StatusCountModifier.Side.BUFF, false);
            case DEBUFF_ON_TARGET -> new StatusCountModifier(
                    modifier.stat(), amount, StatusCountModifier.Side.NOT_BUFF, true);
            case BUFF_ON_TARGET -> new StatusCountModifier(
                    modifier.stat(), amount, StatusCountModifier.Side.BUFF, true);
            case SPECIFIC_STATUS_ON_SELF -> new SpecificStatusModifier(
                    modifier.stat(), amount, modifier.info(), false);
            case SPECIFIC_STATUS_ON_TARGET -> new SpecificStatusModifier(
                    modifier.stat(), amount, modifier.info(), true);
            case DISCARD -> new FlatModifier(
                    modifier.stat(), amount * counters.cardsDiscardedThisCombat());
            case PLAYED -> new FlatModifier(
                    modifier.stat(), amount * playedThisTurn(counters, modifier.info(), catalog));
            case ENERGY_SPENT -> new FlatModifier(
                    modifier.stat(), amount * counters.energySpentThisTurn());
            case ENERGY_GAINED -> new FlatModifier(
                    modifier.stat(), amount * counters.energyGainedThisTurn());
            case ACTION_EXHAUSTED -> new FlatModifier(
                    modifier.stat(), amount * counters.cardsExhaustedThisAction());
            case ACTION_DISPELLED -> new FlatModifier(
                    modifier.stat(), amount * counters.effectsDispelledThisAction());
            // These two add the tally itself rather than a multiple of it.
            case DAMAGE_DEALT_WITH_LAST_ACTION -> new FlatModifier(
                    modifier.stat(), counters.damageDealtThisAction());
            case HP_LOST_SINCE_LAST_TURN -> new FlatModifier(
                    modifier.stat(), counters.hpLostSinceLastTurn(sourceId));
        };
    }

    /**
     * @param family the card family to count, empty meaning every card played this turn
     */
    private static int playedThisTurn(CombatCounters counters, String family, CardCatalog catalog) {
        if (family == null || family.isBlank() || catalog == null) {
            return counters.cardsPlayedThisTurn().size();
        }
        return (int) counters.definitionsPlayedThisTurn().stream()
                .map(catalog::find)
                .filter(java.util.Objects::nonNull)
                .map(CardDefinition::type)
                .filter(type -> type.jsonValue().equalsIgnoreCase(family) || type.name().equalsIgnoreCase(family))
                .count();
    }
}
