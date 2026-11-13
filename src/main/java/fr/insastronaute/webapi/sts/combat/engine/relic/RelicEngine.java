package fr.insastronaute.webapi.sts.combat.engine.relic;

import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelicEngine {

    private final Map<String, RelicHandler> handlers;

    public RelicEngine(List<RelicHandler> handlerList) {
        Map<String, RelicHandler> map = new HashMap<>();
        if (handlerList != null) {
            for (RelicHandler handler : handlerList) {
                if (handler != null && handler.id() != null) {
                    map.put(handler.id(), handler);
                }
            }
        }
        this.handlers = Map.copyOf(map);
    }

    public RelicEngine(Map<String, RelicHandler> handlers) {
        this.handlers = handlers != null ? Map.copyOf(handlers) : Map.of();
    }

    /**
     * Every card the given relics can conjure, so the catalog can be built to hold them.
     *
     * @param relicIds the relic ids a run carries; unknown ones contribute nothing
     */
    public List<String> generatedCardReferences(java.util.Collection<String> relicIds) {
        if (relicIds == null) {
            return List.of();
        }
        java.util.LinkedHashSet<String> references = new java.util.LinkedHashSet<>();
        for (String relicId : relicIds) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                references.addAll(handler.generatedCardReferences());
            }
        }
        return List.copyOf(references);
    }

    /**
     * Ce que cette relique transporte d'un combat à l'autre, et sa valeur de départ.
     *
     * <p>Vide pour une relique inconnue du serveur, comme pour la plupart de celles qu'il
     * connaît : seul ce qui s'accumule au long d'une run est nommé ici.
     */
    public java.util.Map<String, Integer> runScopedMemory(String relicId) {
        RelicHandler handler = handlers.get(relicId);
        return handler == null ? java.util.Map.of() : handler.runScopedMemory();
    }

    /**
     * Fait passer chaque relique de la run par le feu de camp, dans l'ordre où elle
     * les porte, chacune voyant ce que la précédente a laissé.
     *
     * <p>Une relique inconnue du serveur ne fait rien plutôt que d'interrompre :
     * arriver sur un feu de camp inerte serait pire que perdre un effet.
     */
    public RelicHandler.RestOutcome processEnterRestSite(
            java.util.Collection<String> relicIds,
            RelicHandler.RestContext context
    ) {
        RelicHandler.RestOutcome outcome = new RelicHandler.RestOutcome(context.currentHp(), context.charges());
        if (relicIds == null) {
            return outcome;
        }
        for (String relicId : relicIds) {
            RelicHandler handler = handlers.get(relicId);
            if (handler == null) {
                continue;
            }
            outcome = handler.onEnterRestSite(
                    new RelicHandler.RestContext(outcome.currentHp(), context.maxHp(), outcome.charges()));
        }
        return outcome;
    }

    public CombatantState processCombatStart(CombatantState combatant, CombatState state, List<CombatEvent> events) {
        CombatantState current = combatant;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onCombatStart(state, current, events);
            }
        }
        return current;
    }

    /**
     * The same opening pass, with the catalog on hand for the rare relic whose bonus depends on
     * what kind of card each instance in the owner's piles is.
     */
    public CombatantState processCombatStart(
            CombatantState combatant,
            CombatState state,
            fr.insastronaute.webapi.sts.combat.catalog.CardCatalog catalog,
            List<CombatEvent> events
    ) {
        CombatantState current = combatant;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onCombatStart(state, current, catalog, events);
            }
        }
        return current;
    }

    /**
     * Every turn-start hook of {@code combatant}, in order, each handed the randomness the one
     * before it left.
     */
    public RelicHandler.TurnStart processTurnStart(
            CombatantState combatant,
            CombatState state,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            List<CombatEvent> events
    ) {
        CombatantState current = combatant;
        fr.insastronaute.webapi.sts.combat.model.RandomState currentRandom = random;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                RelicHandler.TurnStart step = handler.onTurnStart(state, current, currentRandom, events);
                current = step.combatant();
                currentRandom = step.random();
            }
        }
        return new RelicHandler.TurnStart(current, currentRandom);
    }


    public CombatantState processTurnEnd(CombatantState combatant, CombatState state, List<CombatEvent> events) {
        CombatantState current = combatant;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onTurnEnd(state, current, events);
            }
        }
        return current;
    }

    /**
     * Tells every relic on the table that a turn just closed, whoever's it was.
     *
     * <p>Unlike {@link #processTurnEnd}, which only ever reaches the relics of the combatant whose
     * turn ended, this walks every bearer: a relic answering the field's clock rather than its own
     * bearer's is unreachable from the narrow hook. The dead are asked nothing.
     *
     * @param endingActorId whose turn closed, passed on so a handler can tell its own from others'
     */
    public List<CombatantState> processFieldTurnEnd(
            CombatState state,
            String endingActorId,
            List<CombatantState> combatants,
            List<CombatEvent> events) {
        List<CombatantState> current = new ArrayList<>(combatants);
        for (int index = 0; index < current.size(); index++) {
            CombatantState bearer = current.get(index);
            if (bearer == null || bearer.hp() <= 0) {
                continue;
            }
            CombatantState updated = bearer;
            for (String relicId : bearer.relics()) {
                RelicHandler handler = handlers.get(relicId);
                if (handler != null) {
                    updated = handler.onFieldTurnEnd(state, updated, endingActorId, events);
                }
            }
            current.set(index, updated);
        }
        return List.copyOf(current);
    }

    public fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep processDamageTaken(CombatantState combatant, int amount, List<CombatantState> combatants, List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> timeline, fr.insastronaute.webapi.sts.combat.model.RandomState random, java.util.UUID combatId, long revision, List<CombatEvent> events) {
        // La table entière, et non le seul blessé. Le pas partait de lui seul, si bien qu'une
        // relique qui répond à un coup ne voyait personne d'autre : Carapace miroir n'avait
        // personne à qui renvoyer la moitié du coup, et la revanche de MECA n'avait aucun ennemi
        // à frapper. Le roster était pourtant déjà passé en argument, et simplement écarté.
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep step =
                new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                        combatants == null || combatants.isEmpty()
                                ? List.of(combatant)
                                : combatants,
                        timeline, events, random);
        for (String relicId : combatant.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                // Fetch the latest state of the owner
                CombatantState currentOwner = step.combatants().stream()
                        .filter(c -> c.combatantId().equals(combatant.combatantId()))
                        .findFirst()
                        .orElse(combatant);
                        
                List<CombatantState> roster = step.combatants();
                step = handler.onDamageTaken(currentOwner, amount, roster, step.timeline(), step.random(), combatId, revision, new java.util.ArrayList<>(step.events()));
                // Un handler qui n'a rien à dire rend, par défaut, un pas qui ne porte que son
                // propriétaire. Pris tel quel, ce pas faisait disparaître tous les autres
                // combattants : un appelant qui recopie la table entière (Milieu continu, qui se
                // frappe lui-même) ne voyait plus qu'une équipe debout, et le combat se terminait
                // sur-le-champ dès que le porteur avait une relique.
                step = new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                        overlaid(roster, step.combatants()), step.timeline(), step.events(),
                        step.random(), step.pendingCardPlays(), step.endsTurn());
            }
        }
        return step;
    }

    /** The whole roster, with whatever a handler handed back laid over it by id. */
    private static List<CombatantState> overlaid(
            List<CombatantState> roster, List<CombatantState> changed) {
        List<CombatantState> merged = new ArrayList<>(roster);
        for (CombatantState combatant : changed) {
            boolean replaced = false;
            for (int index = 0; index < merged.size(); index++) {
                if (merged.get(index).combatantId().equals(combatant.combatantId())) {
                    merged.set(index, combatant);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                merged.add(combatant);
            }
        }
        return merged;
    }

    /**
     * Lets every relic its owner carries answer a card that has just been played, in the order
     * the owner carries them.
     */
    public fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep processCardPlayed(
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.catalog.CardDefinition card,
            fr.insastronaute.webapi.sts.combat.model.CombatCounters counters,
            List<CombatantState> combatants,
            List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> timeline,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep step =
                new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                        combatants, timeline, events, random);
        for (String relicId : owner.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler == null) {
                continue;
            }
            CombatantState current = step.combatants().stream()
                    .filter(combatant -> combatant.combatantId().equals(owner.combatantId()))
                    .findFirst()
                    .orElse(owner);
            step = handler.onCardPlayed(current, card, counters, step.combatants(), step.timeline(),
                    step.random(), combatId, revision, new java.util.ArrayList<>(step.events()));
        }
        return step;
    }

    /**
     * Lets everybody fighting the caster answer the card it has just played.
     *
     * <p>Sides are told apart by team rather than by controller: a duel is two humans, and a run
     * is a human against policies, but in both the question is the same one -- who was this
     * thrown at. A caster with no team answers to nobody and is skipped, rather than having every
     * combatant on the table treat it as an opponent.
     */
    public fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep processOpponentCardPlayed(
            CombatantState caster,
            fr.insastronaute.webapi.sts.combat.catalog.CardDefinition card,
            fr.insastronaute.webapi.sts.combat.model.CombatCounters counters,
            List<CombatantState> combatants,
            List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> timeline,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep step =
                new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                        combatants, timeline, events, random);
        if (caster == null || caster.teamId() == null) {
            return step;
        }
        // The watchers are read once, up front: a relic that adds a card to its own hand rewrites
        // the list this loop walks, and iterating the live one would let a later watcher see a
        // hand that changed under it -- or miss itself entirely.
        List<String> watcherIds = combatants.stream()
                .filter(combatant -> !caster.teamId().equals(combatant.teamId()))
                .map(CombatantState::combatantId)
                .toList();
        for (String watcherId : watcherIds) {
            CombatantState watcher = step.combatants().stream()
                    .filter(combatant -> combatant.combatantId().equals(watcherId))
                    .findFirst()
                    .orElse(null);
            if (watcher == null) {
                continue;
            }
            for (String relicId : watcher.relics()) {
                RelicHandler handler = handlers.get(relicId);
                if (handler == null) {
                    continue;
                }
                CombatantState current = step.combatants().stream()
                        .filter(combatant -> combatant.combatantId().equals(watcherId))
                        .findFirst()
                        .orElse(watcher);
                step = handler.onOpponentCardPlayed(current, caster, card, counters,
                        step.combatants(), step.timeline(), step.random(), combatId, revision,
                        new java.util.ArrayList<>(step.events()));
            }
        }
        return step;
    }

    /** Every modifier the relics one combatant carries bring to a value being worked out. */
    public List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> statModifiers(
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.model.CombatCounters counters
    ) {
        if (owner == null) {
            return List.of();
        }
        List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> modifiers =
                new java.util.ArrayList<>();
        for (String relicId : owner.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                modifiers.addAll(handler.statModifiers(owner, counters));
            }
        }
        return List.copyOf(modifiers);
    }

    /** Asks the target's relics whether a status may land on them. */
    public RelicHandler.StatusGate processStatusGate(
            CombatantState target,
            fr.insastronaute.webapi.sts.combat.model.StatusInstance incoming
    ) {
        if (target == null) {
            return new RelicHandler.StatusGate(true, null);
        }
        CombatantState current = target;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler == null) {
                continue;
            }
            RelicHandler.StatusGate gate = handler.canApplyStatus(current, incoming);
            current = gate.owner() == null ? current : gate.owner();
            if (!gate.allowed()) {
                return new RelicHandler.StatusGate(false, current);
            }
        }
        return new RelicHandler.StatusGate(true, current);
    }

    /** Lets the striker's relics answer a blow that landed. */
    public CombatantState processDamageDealt(
            CombatantState striker, CombatantState target, int amount) {
        if (striker == null) {
            return null;
        }
        CombatantState current = striker;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onDamageDealt(current, target, amount);
            }
        }
        return current;
    }

    /** Whether a combatant will let their turn be moved in the order. */
    public boolean canTurnBeMoved(CombatantState owner, boolean advancing, boolean cuttingIn) {
        if (owner == null) {
            return true;
        }
        for (String relicId : owner.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null && !handler.canTurnBeMoved(owner, advancing, cuttingIn)) {
                return false;
            }
        }
        return true;
    }

    public boolean shouldClearArmor(
            CombatantState combatant, CombatState state, String previousActorId) {
        for (String relicId : combatant.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                if (!handler.shouldClearArmor(state, combatant, previousActorId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** For a caller with no previous turn to name; every relic that reads one will clear. */
    public boolean shouldClearArmor(CombatantState combatant, CombatState state) {
        return shouldClearArmor(combatant, state, null);
    }

    /**
     * Heal interception for a caller that has no combat state to offer.
     *
     * <p>Effect handlers only see the combatants and the timeline, and fabricating a combat state
     * to fill the parameter produced an invalid one. No heal relic reads the state today; the
     * real view arrives with the status hook engine.
     */
    public RelicHandler.HealInterceptionResult processHeal(
            CombatantState combatant,
            int amount,
            List<CombatEvent> events
    ) {
        return processHeal(combatant, null, amount, events);
    }

    public RelicHandler.HealInterceptionResult processHeal(CombatantState combatant, CombatState state, int amount, List<CombatEvent> events) {
        CombatantState current = combatant;
        int remainingAmount = amount;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                RelicHandler.HealInterceptionResult result = handler.onHeal(state, current, remainingAmount, events);
                current = result.owner();
                remainingAmount = result.remainingHeal();
                if (remainingAmount <= 0) {
                    break;
                }
            }
        }
        return new RelicHandler.HealInterceptionResult(remainingAmount, current);
    }

    public fr.insastronaute.webapi.sts.combat.model.StatusInstance processStatusApply(CombatantState source, CombatantState target, fr.insastronaute.webapi.sts.combat.model.StatusInstance status) {
        if (source == null) return status;
        fr.insastronaute.webapi.sts.combat.model.StatusInstance current = status;
        for (String relicId : source.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onStatusApply(source, target, current);
            }
        }
        return current;
    }

    /**
     * Lets every relic on the table answer armour that has just gone up.
     *
     * <p>The dispatch used to walk only {@code target.relics()}, which made the hook unreachable
     * for any relic watching somebody <em>else</em>'s armour: the combatant gaining it is not the
     * one carrying the relic. Every bearer is asked now, each told who gained the armour, and a
     * handler that only wants its own decides that for itself.
     *
     * <p>The bearers are read once, up front, for the reason {@code processOpponentCardPlayed}
     * reads its watchers up front: a handler rewrites the list this loop walks.
     */
    public List<CombatantState> processArmorGain(CombatantState target, int amount, List<CombatantState> combatants, List<CombatEvent> events) {
        if (target == null) return combatants;
        List<CombatantState> current = combatants;
        List<String> bearerIds = combatants.stream()
                .filter(combatant -> !combatant.relics().isEmpty())
                .map(CombatantState::combatantId)
                .toList();
        for (String bearerId : bearerIds) {
            CombatantState bearer = current.stream()
                    .filter(combatant -> combatant.combatantId().equals(bearerId))
                    .findFirst()
                    .orElse(null);
            if (bearer == null) {
                continue;
            }
            for (String relicId : bearer.relics()) {
                RelicHandler handler = handlers.get(relicId);
                if (handler == null) {
                    continue;
                }
                CombatantState owner = current.stream()
                        .filter(combatant -> combatant.combatantId().equals(bearerId))
                        .findFirst()
                        .orElse(bearer);
                CombatantState gainer = current.stream()
                        .filter(combatant -> combatant.combatantId().equals(target.combatantId()))
                        .findFirst()
                        .orElse(target);
                current = handler.onArmorGain(owner, gainer, amount, current, events);
            }
        }
        return current;
    }

    /** Runs the striker's own relics over the armour it just broke. */
    public CombatantState processTargetArmorBroken(CombatantState source, CombatantState target) {
        if (source == null || target == null) {
            return source;
        }
        CombatantState current = source;
        for (String relicId : current.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onTargetArmorBroken(current, target);
            }
        }
        return current;
    }

    public List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> processArmorBroken(CombatantState source, CombatantState target, List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> timeline, List<CombatEvent> events) {
        if (target == null) return timeline;
        List<fr.insastronaute.webapi.sts.combat.model.TimelineEntry> current = timeline;
        for (String relicId : target.relics()) {
            RelicHandler handler = handlers.get(relicId);
            if (handler != null) {
                current = handler.onArmorBroken(source, target, current, events);
            }
        }
        if (source != null) {
            for (String relicId : source.relics()) {
                RelicHandler handler = handlers.get(relicId);
                if (handler != null) {
                    current = handler.onTargetArmorBroken(source, target, current, events);
                }
            }
        }
        return current;
    }
}
