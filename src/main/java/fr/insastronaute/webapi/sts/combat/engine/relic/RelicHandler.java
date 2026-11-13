package fr.insastronaute.webapi.sts.combat.engine.relic;

import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;

public interface RelicHandler {

    String id();
    
    /**
     * The cards this relic can conjure out of nothing, by id or by authored name.
     *
     * <p>A combat does not carry the whole game's catalog: {@code CardCatalogFactory} scopes it to
     * what the combat names up front -- the run's deck, the enemies' moves, and what those cards
     * themselves create. A card a relic invents is named by none of them, so it reached the piles
     * with no definition behind it and was refused the moment it was played, with
     * {@code UNSUPPORTED_EFFECT}. Declaring it here is what puts it in the catalog.
     *
     * <p>Whatever a handler returns here must be exactly what it writes as the {@code
     * definitionId} of the instances it creates; {@code MRIERelicHandlerTest} pins that.
     */
    default List<String> generatedCardReferences() {
        return List.of();
    }

    /**
     * Ce que cette relique retient d'un combat à l'autre, et ce qu'elle vaut au départ.
     *
     * <p>{@code RelicMemory} dure le temps d'un combat, ce qui est juste pour la plupart : un
     * drapeau « déjà servi ce combat » doit repartir à zéro au combat suivant, sans quoi la
     * relique ne servirait qu'une fois par run. Mais certaines accumulent au long cours —
     * l'Autoréparation emmagasine les PV soignés depuis le début de la run — et ce qu'elles ont
     * amassé ne doit pas se perdre entre deux combats.
     *
     * <p>Les clés nommées ici sont relues de l'inventaire de la run à l'ouverture d'un combat et
     * réécrites après chaque commande ; la valeur associée est ce que la clé vaut avant qu'un
     * premier combat n'y touche. Tout ce qui n'est pas nommé reste au combat.
     *
     * @return les clés que la run transporte, et leur valeur de départ
     */
    default java.util.Map<String, Integer> runScopedMemory() {
        return java.util.Map.of();
    }

    /**
     * Ce qu'une relique fait en arrivant au feu de camp.
     *
     * <p>Tous les autres crochets prennent un CombatState, un CombatantState, une
     * timeline — le feu de camp n'a rien de tout ça : le joueur y est un nœud JSON
     * d'état de run. D'où ce contexte à trois nombres, qui ne suppose aucun combat.
     *
     * <p>Il est {@code default} pour que les soixante-quatre handlers existants
     * n'aient pas à le connaître : seuls les trois qui agissent au repos l'ont
     * redéfini.
     */
    default RestOutcome onEnterRestSite(RestContext context) {
        return new RestOutcome(context.currentHp(), context.charges());
    }

    /** Le joueur à l'entrée d'un site de repos. */
    record RestContext(int currentHp, int maxHp, int charges) {
    }

    /** Ce qu'une relique laisse derrière elle en arrivant au feu de camp. */
    record RestOutcome(int currentHp, int charges) {
    }

    default CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        return owner;
    }

    /**
     * The same opening hook, for the rare relic that must know what kind of card each instance
     * in the owner's piles is (Dossier complet counting how many Powers the deck holds) rather
     * than only what the state and the owner already carry. Defaults to the catalog-less overload
     * so the other handlers are unaffected.
     */
    default CombatantState onCombatStart(
            CombatState state,
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.catalog.CardCatalog catalog,
            List<CombatEvent> events
    ) {
        return onCombatStart(state, owner, events);
    }

    /**
     * A turn start that may draw, shuffle, or otherwise spend randomness.
     *
     * <p>The three-argument form cannot hand back the random it advanced, so a relic that draws
     * through it -- EP does -- left the combat holding the seed as it was before the draw. Nothing
     * looked wrong: the draw itself is deterministic, and a draw off a non-empty pile spends no
     * randomness at all. It is the reshuffle that does, and the next reshuffle then started from
     * the very same state and dealt the very same order.
     *
     * @return the owner and the random as the handler leaves them
     */
    default TurnStart onTurnStart(
            CombatState state,
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            List<CombatEvent> events
    ) {
        return new TurnStart(onTurnStart(state, owner, events), random);
    }

    /** What a turn-start hook leaves behind: the combatant, and the randomness it did not spend. */
    record TurnStart(
            CombatantState combatant,
            fr.insastronaute.webapi.sts.combat.model.RandomState random) {
    }

    default CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        return owner;
    }
    
    default CombatantState onTurnEnd(CombatState state, CombatantState owner, List<CombatEvent> events) {
        return owner;
    }

    /**
     * The end of <em>anybody's</em> turn, offered to every relic on the table rather than only to
     * the one whose turn it was.
     *
     * <p>The counterpart of {@code CombatManager.FieldTurnEnd} on the Unity side, where a relic
     * hooking {@code OnFieldTurnEnd} answers every turn that closes. {@link #onTurnEnd} is the
     * narrow one: it reaches a relic only on its own bearer's turn.
     *
     * @param endingActorId whose turn just closed, which may be the owner itself
     */
    default CombatantState onFieldTurnEnd(
            CombatState state, CombatantState owner, String endingActorId, List<CombatEvent> events) {
        return owner;
    }
    
    default fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep onDamageTaken(CombatantState owner, int amount, List<CombatantState> combatants, List<TimelineEntry> timeline, fr.insastronaute.webapi.sts.combat.model.RandomState random, java.util.UUID combatId, long revision, List<CombatEvent> events) {
        return new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(owner, timeline, events, random);
    }

    /**
     * Answers a card its owner has just played, once the card's effects have resolved.
     *
     * <p>{@code counters} stand as they did <em>before</em> this card was recorded, which is what
     * Unity's relics read: one asking "was the card before this one an Attack?" wants the card
     * before, and one counting "the third card this turn" counts this one as the next.
     */
    default fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep onCardPlayed(
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.catalog.CardDefinition card,
            fr.insastronaute.webapi.sts.combat.model.CombatCounters counters,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        return new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                combatants, timeline, events, random);
    }

    /**
     * Answers a card somebody on the <em>other</em> side has just played, once it has resolved.
     *
     * <p>The counterpart of {@link #onCardPlayed}, and not a variant of it: that one is asked of
     * the caster's own relics, this one of everybody the caster is fighting. A relic that watches
     * the opposition -- ITI tearing a copy off every move thrown at it -- is unreachable through
     * the other hook, because the caster does not carry it.
     *
     * @param owner  the relic's bearer, on the side that did not play the card
     * @param caster whoever played it
     */
    default fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep onOpponentCardPlayed(
            CombatantState owner,
            CombatantState caster,
            fr.insastronaute.webapi.sts.combat.catalog.CardDefinition card,
            fr.insastronaute.webapi.sts.combat.model.CombatCounters counters,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            fr.insastronaute.webapi.sts.combat.model.RandomState random,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        return new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                combatants, timeline, events, random);
    }

    /**
     * The modifiers this relic contributes to a value being worked out.
     *
     * <p>Each one answers for itself whether it applies, through {@code appliesTo}. The counters
     * are handed over because what a relic scales on — how many attacks have been thrown, what
     * came before — belongs to the combat rather than to the handler.
     */
    default List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> statModifiers(
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.model.CombatCounters counters
    ) {
        return List.of();
    }

    /**
     * Whether a status may land on this relic's owner, and what answering costs the owner.
     *
     * @param allowed false to keep the status off entirely
     * @param owner   the owner as answering left them, since a relic may heal for refusing
     */
    record StatusGate(boolean allowed, CombatantState owner) {
    }

    /** Asked of the relics the <em>target</em> carries, before a status is applied to them. */
    default StatusGate canApplyStatus(CombatantState owner, StatusInstance incoming) {
        return new StatusGate(true, owner);
    }

    /** Answers a blow this relic's owner has landed on someone else. */
    /**
     * The owner has just broken somebody else's armour -- Unity's {@code OnTargetArmorBroken}.
     *
     * <p>Asked of the striker's relics, not the struck one's. {@link #onArmorBroken} is the other
     * side of the same moment and answers for whoever lost the armour; the two are not
     * interchangeable, and porting a striker's relic onto the wrong one makes it silently
     * unreachable -- the struck combatant does not carry it.
     *
     * @return the owner, changed or as given
     */
    default CombatantState onTargetArmorBroken(CombatantState owner, CombatantState target) {
        return owner;
    }

    /** Lets the striker move the target's next scheduled turn after breaking its armour. */
    default List<TimelineEntry> onTargetArmorBroken(
            CombatantState owner,
            CombatantState target,
            List<TimelineEntry> timeline,
            List<CombatEvent> events
    ) {
        return timeline;
    }

    default CombatantState onDamageDealt(CombatantState owner, CombatantState target, int amount) {
        return owner;
    }

    /** Whether this relic's owner will let their turn be moved in the order. */
    default boolean canTurnBeMoved(CombatantState owner, boolean advancing, boolean cuttingIn) {
        return true;
    }

    /**
     * Whether the armour of {@code owner} falls as its turn opens.
     *
     * @param previousActorId whoever took the turn before this one, or null when the turn before
     *                        was cut short rather than closed. GC keeps armour only across an
     *                        owner's own consecutive turns -- "l'Armure n'est pas perdue quand
     *                        vous jouez plusieurs tours à la suite" -- and that question cannot be
     *                        answered from the owner alone.
     */
    default boolean shouldClearArmor(CombatState state, CombatantState owner, String previousActorId) {
        return shouldClearArmor(state, owner);
    }

    default boolean shouldClearArmor(CombatState state, CombatantState owner) {
        return true;
    }
    
    public record HealInterceptionResult(int remainingHeal, CombatantState owner) {}

    /** {@code state} may be null: an effect handler intercepting a heal has no combat state yet. */
    default HealInterceptionResult onHeal(CombatState state, CombatantState owner, int amount, List<CombatEvent> events) {
        return new HealInterceptionResult(amount, owner);
    }

    default StatusInstance onStatusApply(CombatantState source, CombatantState target, StatusInstance status) {
        return status;
    }

    /**
     * Answers armour somebody has just gained -- Unity's {@code OnAnyArmorGain}.
     *
     * <p>Asked of <em>every</em> combatant carrying relics, not only of the one that gained the
     * armour, because a relic may watch the other side put armour up. The two are told apart by
     * the parameters rather than by the dispatch, so a handler that only cares about its bearer's
     * own armour has to say so: {@code owner.combatantId().equals(target.combatantId())}. Sides
     * are told apart by team, as in {@link #onOpponentCardPlayed} -- a duel is two humans, and
     * controller type cannot separate them.
     *
     * @param owner  the relic's bearer
     * @param target whoever gained the armour, possibly the owner themselves
     */
    default List<CombatantState> onArmorGain(
            CombatantState owner,
            CombatantState target,
            int amount,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        return combatants;
    }

    default List<TimelineEntry> onArmorBroken(CombatantState source, CombatantState target, List<TimelineEntry> timeline, List<CombatEvent> events) {
        return timeline;
    }
}
