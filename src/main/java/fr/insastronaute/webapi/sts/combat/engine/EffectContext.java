package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.List;

/**
 * Everything a value needs to be worked out: who is acting, on whom, with what card, and where
 * the fight currently stands.
 *
 * <p>{@code timeline} is here because some cards pay more for hitting something whose turn is far
 * away, which cannot be answered from the combatants alone. {@code combatants} is the other half
 * of that same reasoning: the timeline says in what order everyone would act, but only the
 * combatants say who among them is still alive to actually take a turn — a corpse keeps its place
 * in the timeline.
 *
 * <p>{@code combat} is the component most sites do <em>not</em> have: only the three
 * {@link CombatEngine} sites that work out a cost or a turn delay pass one. The effect resolver
 * and the per-effect handlers (damage, armour, dispel-into-damage) only ever hold the bare
 * combatant list they were given, never a {@code CombatState} to wrap it in — which is exactly why
 * {@code combatants} is its own component rather than something read off {@code combat}.
 */
public record EffectContext(
        CombatState combat,
        CombatantState source,
        CombatantState target,
        List<CombatantState> targets,
        CardDefinition card,
        boolean isPreview,
        List<StatModifier> cardModifiers,
        List<TimelineEntry> timeline,
        List<CombatantState> combatants
) {

    public EffectContext {
        combatants = combatants == null ? List.of() : combatants;
    }

    /**
     * A context with neither timeline nor roster, for a value that depends on neither.
     *
     * <p>Only tests reach for this. A production site that needs a value read off the roster —
     * anything counting who is still alive — must use the canonical constructor and pass the
     * combatants it was given: an empty roster reads as <em>nobody is alive</em>, and a modifier
     * that falls back on that answers with silence rather than an error. The overload that took a
     * timeline but no roster was removed for exactly that reason, so a site that has a timeline to
     * pass now has to say what it knows about the living too.
     */
    public EffectContext(
            CombatState combat,
            CombatantState source,
            CombatantState target,
            List<CombatantState> targets,
            CardDefinition card,
            boolean isPreview,
            List<StatModifier> cardModifiers
    ) {
        this(combat, source, target, targets, card, isPreview, cardModifiers, List.of(), List.of());
    }
}
