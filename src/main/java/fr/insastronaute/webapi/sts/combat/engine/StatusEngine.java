package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.engine.status.StatusBehaviours;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusTurnEffect;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.HealApplied;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.event.StatusRemoved;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Runs the statuses a combatant carries across the two moments of a turn.
 *
 * <p>Each status keeps its own timing. Poison bites as the turn opens, Burn as it closes, armour
 * from Shielding arrives before the player acts and armour from Plating after. Counting them all
 * down on one schedule, which is what happened before, quietly changed how long every status in
 * the game lasted.
 */
public final class StatusEngine {

    private static final String NO_PILE = "NONE";
    private static final String HAND_PILE = "HAND";
    private static final String DISCARD_PILE = "DISCARD";

    private StatusEngine() {
    }

    /**
     * @param random the combat's random stream as the tick left it, which a reshuffle advances
     */
    public record StatusTickResult(
            CombatantState combatant,
            List<CombatEvent> events,
            RandomState random,
            int expiredBuffs) {

        /** The shape every caller that does not count expiries still uses. */
        public StatusTickResult(
                CombatantState combatant, List<CombatEvent> events, RandomState random) {
            this(combatant, events, random, 0);
        }
    }

    /**
     * Opens a turn: armour is dropped unless something keeps it, then every status acts.
     */
    /**
     * Opens a turn's statuses, and decides that turn's armour once.
     *
     * <p>{@code relicsAllowArmorToClear} is the verdict of the owner's relics, which this class
     * cannot see. It is a parameter and not a second gate in the caller because it used to be
     * exactly that: the engine cleared the armour or kept it according to the relics, and then
     * this method cleared it again according to the statuses alone. The later word won every
     * time, so a relic that keeps armour across turns -- GC's "l'Armure n'est pas perdue quand
     * vous jouez plusieurs tours à la suite" -- never kept any. One decision, made here, reading
     * both.
     *
     * @param relicsAllowArmorToClear false when a relic keeps the armour whatever the statuses say
     */
    public static StatusTickResult processTurnStart(
            CombatantState target,
            long revision,
            UUID combatId,
            RandomState random,
            boolean relicsAllowArmorToClear
    ) {
        boolean keepsArmor = !relicsAllowArmorToClear
                || target.statuses().stream().anyMatch(StatusBehaviours::keepsArmor);
        CombatantState opening = keepsArmor ? target : withArmor(target, 0);
        return run(snapshotFreinMoteur(opening), revision, combatId, true, random);
    }

    /** A turn start with no relic to consult: the statuses alone decide. */
    public static StatusTickResult processTurnStart(
            CombatantState target,
            long revision,
            UUID combatId,
            RandomState random
    ) {
        return processTurnStart(target, revision, combatId, random, true);
    }

    /** Closes a turn: every status that acts on the way out does so, then the countdowns run. */
    public static StatusTickResult processTurnEnd(
            CombatantState target,
            long revision,
            UUID combatId,
            RandomState random
    ) {
        return applyFreinMoteur(run(target, revision, combatId, false, random), revision, combatId);
    }

    /**
     * Note les PV du porteur au tout début de son tour, pour que Frein moteur sache à la fin
     * combien il en a coûté.
     */
    private static CombatantState snapshotFreinMoteur(CombatantState target) {
        if (target.statuses().stream().noneMatch(status -> "FreinMoteur".equals(status.statusType()))) {
            return target;
        }
        List<StatusInstance> statuses = new ArrayList<>(target.statuses());
        for (int index = 0; index < statuses.size(); index++) {
            if ("FreinMoteur".equals(statuses.get(index).statusType())) {
                statuses.set(index, statuses.get(index).withProgress(target.hp()));
            }
        }
        return withStatuses(target, statuses);
    }

    /** Rend la moitié des PV perdus depuis le relevé pris en début de tour. */
    private static StatusTickResult applyFreinMoteur(
            StatusTickResult result, long revision, UUID combatId) {
        CombatantState combatant = result.combatant();
        StatusInstance frein = combatant.statuses().stream()
                .filter(status -> "FreinMoteur".equals(status.statusType()))
                .findFirst()
                .orElse(null);
        if (frein == null || frein.progress() <= combatant.hp()) {
            return result;
        }
        int healed = (frein.progress() - combatant.hp()) / 2;
        if (healed <= 0) {
            return result;
        }
        List<CombatEvent> events = new ArrayList<>(result.events());
        Applied applied = apply(combatant, StatusTurnEffect.heal(healed).withoutTick(),
                revision, combatId, events, new ArrayList<>(), result.random());
        return new StatusTickResult(
                applied.combatant(), events, applied.random(), result.expiredBuffs());
    }

    private static StatusTickResult run(
            CombatantState target,
            long revision,
            UUID combatId,
            boolean turnStart,
            RandomState random
    ) {
        CombatantState current = target;
        RandomState currentRandom = Objects.requireNonNull(random, "random must not be null");
        List<CombatEvent> events = new ArrayList<>();
        List<StatusInstance> surviving = new ArrayList<>();
        List<Integer> survivingPositions = new ArrayList<>();
        // Statuses a status handed out, such as Strengthen turning into Strength. They are kept
        // apart from the countdown so a status born this turn is not judged as if it had been
        // there all along.
        List<StatusInstance> granted = new ArrayList<>();
        // Fumigène ne peut pas frapper d'ici : ce moteur ne voit qu'un combattant et n'a
        // personne à choisir. Il compte, et CombatEngine, qui a la table sous les yeux, frappe.
        int expiredBuffs = 0;
        // The statuses as the interceptors leave them: a status that bites goes through them like
        // any other blow, so a Time Stop swallows a Poison and a Regulator counts it.
        List<StatusInstance> intercepting = new ArrayList<>(target.statuses());
        if (turnStart) {
            intercepting.replaceAll(status -> "Regulator".equals(status.statusType())
                    ? status.withProgress(0)
                    : status);
        }

        for (int position = 0; position < target.statuses().size(); position++) {
            StatusInstance status = target.statuses().get(position);
            StatusTurnEffect effect;
            if (turnStart) {
                effect = StatusBehaviours.atTurnStart(status);
            } else {
                // Turn-end effects may roll: "Statuts" picks one of three afflictions. The roll
                // is taken from the combat's own stream so a replayed combat inflicts the same
                // one.
                StatusBehaviours.Rolled rolled = StatusBehaviours.atTurnEnd(status, currentRandom);
                effect = rolled.effect();
                currentRandom = rolled.random();
            }
            if (effect.hpDelta() < 0) {
                int incoming = -effect.hpDelta();
                // Armour answers before anything else, exactly as it does for a blow from a card
                // (DamageHandler): it soaks what it can, and only what gets past it is offered to
                // the intercepting statuses and then to HP.
                if (!effect.piercesArmor()) {
                    int absorbed = Math.min(current.armor(), incoming);
                    if (absorbed > 0) {
                        current = withArmor(current, current.armor() - absorbed);
                        incoming -= absorbed;
                        // No event announces the dent. ArmorGained refuses a negative amount, and
                        // ArmorBroken would tell the counters that armour was broken -- which
                        // satisfies the ArmorBreak condition on whatever is played next, for a
                        // break that never happened. The client reads armour off the snapshot
                        // that follows, so the number is right either way.
                    }
                }
                fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.HpLossVerdict verdict =
                        fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.validateHpLoss(
                        intercepting, incoming, current.hp(), List.of());
                intercepting = new ArrayList<>(verdict.statuses());
                effect = effect.withHpDelta(-verdict.hpLost());
            }
            Applied applied = apply(current, effect, revision, combatId, events, granted, currentRandom);
            current = applied.combatant();
            currentRandom = applied.random();

            if (effect.clearsValue()) {
                // Le compteur d'Absorption est remis à zéro là où les interceptions écrivent,
                // pour que carrying() le reporte sur le statut qui survit.
                intercepting.set(position, withValue(intercepting.get(position), 0));
            }

            StatusInstance advanced = countDown(status, effect, target.statuses());
            if (!StatusBehaviours.hasExpired(advanced)) {
                surviving.add(advanced);
                survivingPositions.add(position);
                continue;
            }
            if (StatusCatalog.isBuff(advanced)) {
                expiredBuffs++;
            }
            // A Time Stop hands back what it swallowed, this tick's bites included.
            Applied expired = apply(current,
                    StatusBehaviours.onExpiry(carrying(advanced, intercepting.get(position))),
                    revision, combatId, events, granted, currentRandom);
            current = expired.combatant();
            currentRandom = expired.random();
            events.add(new StatusRemoved(combatId, revision, target.combatantId(),
                    advanced.statusType(), advanced.value(), 0, 0, "expiry"));
        }

        List<StatusInstance> resulting = new ArrayList<>(surviving);
        // What the interceptors took on is written back last, so a Time Stop swallowing a bite
        // dealt after its own place in the list keeps it all the same.
        for (int index = 0; index < resulting.size(); index++) {
            resulting.set(index, carrying(
                    resulting.get(index), intercepting.get(survivingPositions.get(index))));
        }
        List<StatusInstance> totals = new ArrayList<>();
        granted.forEach(status -> totals.add(StatusOperations.insert(resulting, status)));
        announceTotals(events, totals);
        return new StatusTickResult(
                withStatuses(current, resulting), events, currentRandom, expiredBuffs);
    }

    /**
     * Rewrites what the tick announced it granted with what the holder now carries.
     *
     * <p>{@code apply} announces a granted status the moment it is handed out, before it is
     * stacked onto what the holder already has, so the event said "2 Strength" for a holder that
     * ends up with 5. A {@link StatusApplied} says what the status holds after the application,
     * and the client shows that number: announcing the grant alone showed the wrong one.
     *
     * <p>The tick announces nothing else as a StatusApplied, so the n-th one it wrote is the n-th
     * grant.
     */
    private static void announceTotals(List<CombatEvent> events, List<StatusInstance> totals) {
        int next = 0;
        for (int index = 0; index < events.size() && next < totals.size(); index++) {
            if (events.get(index) instanceof StatusApplied applied) {
                StatusInstance total = totals.get(next++);
                events.set(index, new StatusApplied(applied.combatId(), applied.revision(),
                        applied.sourceId(), applied.combatantId(), total.statusType(),
                        total.value(), total.duration(), applied.stacked()));
            }
        }
    }

    private static StatusInstance withValue(StatusInstance status, int value) {
        return new StatusInstance(status.statusType(), value, status.duration(), status.cardId(),
                status.index(), status.removable(), status.resistsEverything(), status.progress());
    }

    /** @return the status as the tick left it, carrying what the interception wrote on it */
    private static StatusInstance carrying(StatusInstance ticked, StatusInstance intercepted) {
        if (ticked.value() == intercepted.value() && ticked.progress() == intercepted.progress()) {
            return ticked;
        }
        return new StatusInstance(ticked.statusType(), intercepted.value(), ticked.duration(),
                ticked.cardId(), ticked.index(), ticked.removable(), ticked.resistsEverything(),
                intercepted.progress());
    }

    private static StatusInstance countDown(
            StatusInstance status,
            StatusTurnEffect effect,
            List<StatusInstance> holderStatuses
    ) {
        if (!effect.ticks() || !StatusBehaviours.countsDown(status, holderStatuses)) {
            return status;
        }
        if (status.duration() <= 0) {
            return status;
        }
        return new StatusInstance(status.statusType(), status.value(), status.duration() - 1,
                status.cardId(), status.index());
    }

    private record Applied(CombatantState combatant, RandomState random) {
    }

    private static Applied apply(
            CombatantState combatant,
            StatusTurnEffect effect,
            long revision,
            UUID combatId,
            List<CombatEvent> events,
            List<StatusInstance> granted,
            RandomState random
    ) {
        CombatantState current = combatant;
        RandomState currentRandom = random;
        if (effect.lethal()) {
            events.add(new HpLost(combatId, revision, current.combatantId(),
                    current.combatantId(), current.hp(), current.hp(), 0));
            return new Applied(withHp(current, 0), currentRandom);
        }
        if (effect.hpDelta() < 0) {
            int loss = Math.min(current.hp(), -effect.hpDelta());
            current = withHp(current, current.hp() - loss);
            events.add(new HpLost(combatId, revision, current.combatantId(),
                    current.combatantId(), -effect.hpDelta(), loss, current.hp()));
        } else if (effect.hpDelta() > 0) {
            int healed = Math.min(current.maxHp() - current.hp(), effect.hpDelta());
            current = withHp(current, current.hp() + healed);
            events.add(new HealApplied(combatId, revision, current.combatantId(),
                    current.combatantId(), effect.hpDelta(), healed, current.hp()));
        }
        if (effect.armorDelta() != 0) {
            int armor = Math.max(0, current.armor() + effect.armorDelta());
            current = withArmor(current, armor);
            events.add(new ArmorGained(combatId, revision, current.combatantId(),
                    current.combatantId(), effect.armorDelta(), effect.armorDelta(), armor));
        }
        if (effect.energyDelta() != 0) {
            int previousEnergy = current.energy();
            int energy = Math.max(0, current.energy() + effect.energyDelta());
            current = withEnergy(current, energy);
            if (effect.energyDelta() > 0) {
                events.add(new EnergyGained(combatId, revision, current.combatantId(),
                        effect.energyDelta(), energy));
            } else {
                events.add(new EnergySpent(combatId, revision, current.combatantId(),
                        previousEnergy - energy, energy));
            }
        }
        if (effect.cardsToDraw() > 0) {
            Drawn drawn = draw(current, effect.cardsToDraw(), revision, combatId, events,
                    currentRandom);
            current = drawn.combatant();
            currentRandom = drawn.random();
        } else if (effect.cardsToDraw() < 0) {
            current = discard(current, -effect.cardsToDraw(), revision, combatId, events);
        }
        if (effect.appliedStatus() != null) {
            granted.add(effect.appliedStatus());
            events.add(new StatusApplied(combatId, revision, current.combatantId(),
                    current.combatantId(), effect.appliedStatus().statusType(),
                    effect.appliedStatus().value(), effect.appliedStatus().duration(), false));
        }
        return new Applied(current, currentRandom);
    }

    /**
     * Se défausse du bas de la main, sans rien tirer au sort.
     *
     * <p>Le pendant négatif d'une pioche : Production se lit comme Énergie, et une valeur
     * négative retire ce qu'une valeur positive donne. Le choix de la carte est arrêté et non
     * tiré au hasard, pour que rejouer un combat défausse les mêmes cartes sans dépenser le flux
     * aléatoire, dont l'état est la seule chose qui rende la rediffusion fidèle.
     */
    private static CombatantState discard(
            CombatantState combatant,
            int count,
            long revision,
            UUID combatId,
            List<CombatEvent> events
    ) {
        List<CardInstance> hand = new ArrayList<>(combatant.piles().hand());
        if (hand.isEmpty()) {
            return combatant;
        }
        List<CardInstance> discard = new ArrayList<>(combatant.piles().discard());
        for (int taken = 0; taken < count && !hand.isEmpty(); taken++) {
            CardInstance card = hand.remove(hand.size() - 1);
            discard.add(card);
            events.add(new CardMoved(combatId, revision, combatant.combatantId(),
                    card.instanceId(), card.definitionId(), HAND_PILE, DISCARD_PILE,
                    discard.size() - 1));
        }
        return withPiles(combatant, new fr.insastronaute.webapi.sts.combat.model.CardPiles(
                combatant.piles().draw(), hand, discard, combatant.piles().exhaust()));
    }

    private record Drawn(CombatantState combatant, RandomState random) {
    }

    /**
     * Draws from the combat's own random stream. Seeding a throwaway generator on the revision
     * made a status-driven reshuffle a function of the revision number, and left the stream
     * standing still so the next shuffle repeated it.
     */
    private static Drawn draw(
            CombatantState combatant,
            int count,
            long revision,
            UUID combatId,
            List<CombatEvent> events,
            RandomState random
    ) {
        PileOperations.DrawResult drawn = PileOperations.draw(combatant.piles(), count, random);
        List<CardInstance> hand = drawn.piles().hand();
        for (int index = combatant.piles().hand().size(); index < hand.size(); index++) {
            events.add(new CardMoved(combatId, revision, combatant.combatantId(),
                    hand.get(index).instanceId(), hand.get(index).definitionId(), NO_PILE, HAND_PILE, index));
        }
        return new Drawn(withPiles(combatant, drawn.piles()), drawn.random());
    }

    private static CombatantState withHp(CombatantState combatant, int hp) {
        return rebuild(combatant, hp, combatant.armor(), combatant.energy(),
                combatant.piles(), combatant.statuses());
    }

    private static CombatantState withArmor(CombatantState combatant, int armor) {
        return rebuild(combatant, combatant.hp(), armor, combatant.energy(),
                combatant.piles(), combatant.statuses());
    }

    private static CombatantState withEnergy(CombatantState combatant, int energy) {
        return rebuild(combatant, combatant.hp(), combatant.armor(), energy,
                combatant.piles(), combatant.statuses());
    }

    private static CombatantState withPiles(CombatantState combatant, CardPiles piles) {
        return rebuild(combatant, combatant.hp(), combatant.armor(), combatant.energy(),
                piles, combatant.statuses());
    }

    private static CombatantState withStatuses(
            CombatantState combatant,
            List<StatusInstance> statuses
    ) {
        return rebuild(combatant, combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.piles(), statuses);
    }

    private static CombatantState rebuild(
            CombatantState combatant,
            int hp,
            int armor,
            int energy,
            CardPiles piles,
            List<StatusInstance> statuses
    ) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), hp, armor, energy, combatant.baseEnergy(),
                combatant.turnDelayTicks(), piles, statuses, combatant.relics(),
                combatant.intentCardId(), combatant.patternIndex(), combatant.relicMemory());
    }
}
