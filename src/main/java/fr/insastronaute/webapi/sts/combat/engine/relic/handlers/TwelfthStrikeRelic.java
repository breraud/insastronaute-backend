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
 * Twelfth hour: every twelfth Attack of the combat hits twice as hard.
 *
 * <p>The handler used to hand out twelve Armour at the start of the combat, which is not what the
 * relic does in the game at all ({@code TwelfthStrikeRelic.cs}).
 */
@Component
public class TwelfthStrikeRelic implements RelicHandler {

    @Override
    public String id() {
        return "TwelfthStrikeRelic";
    }

    @Override
    public List<StatModifier> statModifiers(CombatantState owner, CombatCounters counters) {
        return List.of(new TwelfthStrikeDamage(owner, counters));
    }

    private record TwelfthStrikeDamage(CombatantState owner, CombatCounters counters)
            implements StatModifier {

        @Override
        public StatType type() {
            return StatType.DAMAGE;
        }

        @Override
        public ModifierType modifierType() {
            return ModifierType.MULTIPLICATIVE;
        }

        @Override
        public boolean appliesTo(StatType asked, EffectContext ctx) {
            // The attack being thrown is the next one along, so it is the twelfth when eleven
            // came before it.
            return asked == StatType.DAMAGE
                    && ctx.source() != null
                    && ctx.source().combatantId().equals(owner.combatantId())
                    && ctx.card() != null
                    && ctx.card().type() == CardType.ATTACK
                    && (counters.attacksPlayedThisCombat() + 1) % 12 == 0;
        }

        @Override
        public int modify(int value, EffectContext ctx) {
            return value * 2;
        }
    }
}
