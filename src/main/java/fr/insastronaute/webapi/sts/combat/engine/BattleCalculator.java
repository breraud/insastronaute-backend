package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierType;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BattleCalculator {

    /** Le statut dont les attaques passent outre l'armure et les réductions de la cible. */
    private static final String LUNA = "Luna";

    private BattleCalculator() {
        // Utility class
    }

    /**
     * Whether this combatant's blows go through what the other side puts in their way.
     *
     * <p>Les deux défenses qu'une attaque rencontre ne sont pas au même endroit : l'armure est
     * soustraite dans {@code DamageHandler}, une fois le nombre arrêté, tandis que les réductions
     * agissent sur le nombre lui-même, ici. Lunaire les écarte toutes les deux, et la question se
     * pose donc des deux côtés -- d'où cette réponse en un seul endroit.
     */
    public static boolean piercesDefences(CombatantState source) {
        return source != null
                && source.statuses().stream().anyMatch(s -> LUNA.equals(s.statusType()));
    }

    public static int getModifiedValue(int baseValue, StatType type, EffectContext ctx) {
        int value = baseValue;

        List<StatModifier> applyingModifiers = new ArrayList<>();
        
        // The card speaks first. Both engines sort by kind with a stable sort, so within one kind
        // the collection order decides, and Unity collects the card's own modifiers ahead of the
        // statuses (BattleCalculator.cs:9-31).
        if (ctx.cardModifiers() != null) {
            for (StatModifier mod : ctx.cardModifiers()) {
                if (mod.appliesTo(type, ctx)) {
                    applyingModifiers.add(mod);
                }
            }
        }

        if (ctx.source() != null) {
            for (StatusInstance instance : ctx.source().statuses()) {
                StatModifier effect = createStatusEffect(instance, ctx.source());
                if (effect != null && effect.appliesTo(type, ctx)) {
                    applyingModifiers.add(effect);
                }
            }
        }
        
        if (ctx.target() != null && (ctx.source() == null || !ctx.target().combatantId().equals(ctx.source().combatantId()))) {
            // Lunaire écarte ce qui, chez la cible, amortirait le coup. Reconnu à ce qu'il fait
            // plutôt qu'à son nom : un modificateur qui rend moins que ce qu'on lui donne est une
            // réduction, quel que soit le statut qui le porte. Ce qui aggrave le coup (Vulnérable)
            // n'est pas une défense et reste en place -- « ignore les réductions » ne dit pas
            // « ignore la cible ».
            boolean piercing = type == StatType.DAMAGE && piercesDefences(ctx.source());
            for (StatusInstance instance : ctx.target().statuses()) {
                StatModifier effect = createStatusEffect(instance, ctx.target());
                if (effect != null && effect.appliesTo(type, ctx)) {
                    if (piercing && effect.modify(baseValue, ctx) < baseValue) {
                        continue;
                    }
                    applyingModifiers.add(effect);
                }
            }
        }
        
        // A relic's modifiers reach the calculator with the action's own, gathered by the
        // resolver from the caster's relics. They used to be asked of a switch statement with no
        // cases in it, so no relic could ever change a number.

        applyingModifiers.sort(Comparator.comparing(StatModifier::modifierType));

        for (StatModifier mod : applyingModifiers) {
            if (mod.appliesTo(type, ctx)) {
                value = mod.modify(value, ctx);
            }
        }

        return value;
    }

    private static StatModifier createStatusEffect(StatusInstance instance, CombatantState owner) {
        return fr.insastronaute.webapi.sts.combat.engine.status.StatusModifier.of(instance, owner);
    }
}
