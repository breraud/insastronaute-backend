package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierType;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Counter-offensive stance: a Skill played right after an Attack gives two extra Armour.
 *
 * <p>What "right after" means is a fact about the combat, so it is read from the counters. The
 * handler used to keep it in a marker status on its owner, which a card that dispels debuffs
 * stripped and a turn boundary erased.
 */
@Component
public class CounterStanceRelicHandler implements RelicHandler {

    @Override
    public String id() {
        return "CounterStanceRelic";
    }

    @Override
    public List<StatModifier> statModifiers(CombatantState owner, CombatCounters counters) {
        return List.of(new CounterStanceArmor(owner, counters));
    }

    private record CounterStanceArmor(CombatantState owner, CombatCounters counters)
            implements StatModifier {

        @Override
        public StatType type() {
            return StatType.ARMOR;
        }

        @Override
        public ModifierType modifierType() {
            return ModifierType.ADDITIVE;
        }

        @Override
        public boolean appliesTo(StatType asked, EffectContext ctx) {
            return asked == StatType.ARMOR
                    && ctx.source() != null
                    && ctx.source().combatantId().equals(owner.combatantId())
                    && ctx.card() != null
                    && ctx.card().type() == CardType.SKILL
                    && counters.lastCardTypePlayed() == CardType.ATTACK;
        }

        @Override
        public int modify(int value, EffectContext ctx) {
            return value + 2;
        }
    }
}
