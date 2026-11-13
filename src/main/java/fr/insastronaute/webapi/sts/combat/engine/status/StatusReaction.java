package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;

/**
 * What statuses do in answer to a blow that has just landed.
 *
 * <p>These are the statuses that only speak when something happens to their holder or to whoever
 * they struck: thorns that bite back, armour that springs up when hit, strength that grows from
 * drawing blood. None of them did anything before.
 *
 * <p>A reaction is described as changes to make rather than as code to run, so the handler that
 * applies a blow stays the only place that knows how to change a combatant.
 */
public final class StatusReaction {

    /**
     * @param casterHpDelta   what happens to whoever struck, which is how thorns bite back
     * @param recipientArmor  armour the one who was struck gains
     * @param casterStatuses  statuses whoever struck comes away with
     * @param recipientStatuses statuses the one who was struck comes away with
     */
    public record Outcome(
            int casterHpDelta,
            int recipientArmor,
            List<StatusInstance> casterStatuses,
            List<StatusInstance> recipientStatuses,
            List<StatusInstance> recipientStatusUpdates
    ) {

        public Outcome {
            casterStatuses = List.copyOf(casterStatuses);
            recipientStatuses = List.copyOf(recipientStatuses);
            recipientStatusUpdates = List.copyOf(recipientStatusUpdates);
        }

        public boolean isNothing() {
            return casterHpDelta == 0 && recipientArmor == 0
                    && casterStatuses.isEmpty() && recipientStatuses.isEmpty()
                    && recipientStatusUpdates.isEmpty();
        }
    }

    private StatusReaction() {
    }

    /**
     * Answers a blow that actually landed.
     *
     * <p>Ce à quoi elles répondent est le coup porté, et non la blessure qu'il laisse : une cible
     * cuirassée fait quand même mordre les Épines, avancer la Cristallisation et descendre un
     * Étourdissement retardé. C'est la règle d'Unity, où {@code OnDamageTaken} et
     * {@code OnDamageDealt} reçoivent tous deux {@code dmg} sans jamais voir l'armure
     * (EffectResolver) -- ici on leur passait ce qui avait traversé, si bien qu'un coup entièrement
     * bu ne provoquait rien du tout.
     *
     * @param casterStatuses    what the one who struck carries
     * @param recipientStatuses what the one who was struck carries
     * @param damage            le coup porté, armure comprise
     */
    public static Outcome toDamage(
            List<StatusInstance> casterStatuses,
            List<StatusInstance> recipientStatuses,
            int damage
    ) {
        return toDamage(casterStatuses, recipientStatuses, damage, true);
    }

    /**
     * @param feedsCrystallize false for the blow Recristallisation itself deals: Unity's
     *                         CrystallizeStatus ignores it, so the count starts over from the
     *                         reset rather than from one hit further along
     */
    public static Outcome toDamage(
            List<StatusInstance> casterStatuses,
            List<StatusInstance> recipientStatuses,
            int damage,
            boolean feedsCrystallize
    ) {
        if (damage <= 0) {
            return new Outcome(0, 0, List.of(), List.of(), List.of());
        }
        int casterHpDelta = 0;
        int recipientArmor = 0;
        List<StatusInstance> onCaster = new ArrayList<>();
        List<StatusInstance> onRecipient = new ArrayList<>();
        List<StatusInstance> updatedRecipientStatuses = new ArrayList<>();
        boolean megacristal = recipientStatuses.stream()
            .anyMatch(status -> "Megacristal".equals(status.statusType()));
        // Sublimation lit combien de Brûlure la cible porte déjà, pas ce que le coup vient d'y
        // ajouter : un coup qui poserait la dixième Brûlure ne soigne pas encore, seul le suivant
        // le fera.
        boolean recipientBurningTenOrMore = recipientStatuses.stream()
            .filter(status -> "Burn".equals(status.statusType()))
            .mapToInt(StatusInstance::duration)
            .sum() >= 10;

        for (StatusInstance status : recipientStatuses) {
            switch (status.statusType()) {
                case "Thorns" -> casterHpDelta -= status.value();
                case "Elastic", "Hide" -> recipientArmor += status.value();
                // Plein régime ne répond pas ici : il parle des PV perdus pendant le tour de son
                // porteur, quelle qu'en soit la cause, et c'est EffectResolver qui le sait.
                case "Crystallize" -> {
                    if (feedsCrystallize) {
                        int threshold = crystallizeThreshold(recipientStatuses);
                        boolean arms = status.value() >= threshold;
                        updatedRecipientStatuses.add(new StatusInstance(
                                status.statusType(), arms ? status.value() : status.value() + 1,
                                status.duration(),
                                status.cardId(), status.index(), status.removable(),
                                megacristal || Boolean.TRUE.equals(status.resistsEverything()),
                                arms ? 1 : status.progress()));
                    }
                }
                case "DelayedStun" -> updatedRecipientStatuses.add(new StatusInstance(
                    status.statusType(), status.value(), Math.max(0, status.duration() - 1),
                    status.cardId(), status.index(), status.removable(),
                    status.resistsEverything(), status.progress()));
                default -> {
                }
            }
        }

        for (StatusInstance status : casterStatuses) {
            switch (status.statusType()) {
                // Pleine lune est FIXED au catalogue, sa valeur authorée tombe donc à 0 : elle
                // donnait une Force de 0 et une Perte de Force qui ne retirait rien. Unity la
                // fixe à 1 dans son constructeur (FullMoonStatus.cs), comme Fatigue plus bas.
                case "FullMoon" -> {
                    int moon = Math.max(1, status.value());
                    onCaster.add(new StatusInstance(
                        "Strength", moon, -1, status.cardId(), 0));
                    onCaster.add(new StatusInstance(
                        "FullMoonTemp", moon, 1, status.cardId(), 0));
                }
                case "Fatigue" -> onCaster.add(
                        // Fatigue est FIXED au catalogue : sa valeur authorée est toujours 0
                        // (StatusCatalog.instantiate), comme sa durée. Unity fixe la sienne à 1
                        // dans son constructeur (FatigueStatus.cs:7) plutôt que de la lire.
                        new StatusInstance("Speed", -1, -1, status.cardId(), 0));
                case "Vulnerabilize" -> onRecipient.add(
                        new StatusInstance("Vuln", 0, status.value(), status.cardId(), 0));
                // Lames ardentes ne distingue pas les coups : chaque blessure qui passe pose sa
                // Brûlure, y compris celle qu'un statut de la cible vient de renvoyer.
                case "LamesArdentes" -> onRecipient.add(
                        new StatusInstance("Burn", 0, status.value(), status.cardId(), 0));
                case "Sublimation" -> {
                    if (recipientBurningTenOrMore) {
                        casterHpDelta += status.value();
                    }
                }
                default -> {
                }
            }
        }

        return new Outcome(casterHpDelta, recipientArmor, onCaster, onRecipient,
            updatedRecipientStatuses);
    }

    /**
     * How far a Crystallize has to count before the next hit arms it: three, or two once its
     * holder carries Mégacristal ("demande 1 coup de moins").
     */
    public static int crystallizeThreshold(List<StatusInstance> holderStatuses) {
        return holderStatuses.stream()
                .anyMatch(status -> "Megacristal".equals(status.statusType())) ? 2 : 3;
    }

    /**
     * Ce qu'un soin devient une fois passé devant Ferveur.
     *
     * @param remainingHeal ce qui touche encore les points de vie
     * @param deferred      ce que Ferveur a retenu pour le rendre en s'en allant
     * @param statuses      les statuts du soigné, Ferveur ayant noté ce qu'elle garde
     */
    public record HealDeferral(
            int remainingHeal, int deferred, List<StatusInstance> statuses) {

        public HealDeferral {
            statuses = List.copyOf(statuses);
        }
    }

    /**
     * Fait passer un soin devant Ferveur, qui l'avale et l'inscrit sur sa propre valeur.
     *
     * <p>Le miroir de ce que {@link #validateHpLoss} fait pour l'Arrêt du temps : l'un retient
     * les coups, l'autre les soins, et tous deux rendent d'un coup ce qu'ils ont gardé.
     */
    public static HealDeferral deferHealing(List<StatusInstance> holderStatuses, int heal) {
        if (heal <= 0) {
            return new HealDeferral(heal, 0, holderStatuses);
        }
        List<StatusInstance> statuses = new ArrayList<>(holderStatuses);
        for (int index = 0; index < statuses.size(); index++) {
            StatusInstance status = statuses.get(index);
            if (!"Fervor".equals(status.statusType())) {
                continue;
            }
            statuses.set(index, accumulating(status, heal));
            return new HealDeferral(0, heal, statuses);
        }
        return new HealDeferral(heal, 0, holderStatuses);
    }

    /** @return true when healing this combatant wounds it instead */
    public static boolean invertsHealing(List<StatusInstance> holderStatuses) {
        return holderStatuses.stream()
                .anyMatch(status -> "Undead".equals(status.statusType()));
    }

    /**
     * What a blow costs its target once the statuses standing between the two have spoken.
     *
     * @param hpLost   what stays on the target, the interception having spent or charged itself
     * @param statuses the holder's statuses as the interception left them
     */
    public record HpLossVerdict(int hpLost, List<StatusInstance> statuses) {

        public HpLossVerdict {
            statuses = List.copyOf(statuses);
        }
    }

    /**
     * Runs a loss of hit points past the statuses that intercept it, mirroring Unity's
     * {@code Character.LoseHP}: each speaks in turn, on what the one before it left.
     *
     * <p>Time Stop swallows the loss whole and remembers it on its own value, to hand back half
     * again as much when it expires. A Regulator lets only so much through in one turn and keeps
     * its own tally of what it has already let through. Link refuses the loss outright while the
     * character it ties its holder to is still standing.
     *
     * @param combatants the roster Link reads, empty when the caller holds none — Link then has
     *                   nobody to find and lets the loss through
     */
    public static HpLossVerdict validateHpLoss(
            List<StatusInstance> holderStatuses,
            int hpLoss,
            List<fr.insastronaute.webapi.sts.combat.model.CombatantState> combatants
    ) {
        return validateHpLoss(holderStatuses, hpLoss, Integer.MAX_VALUE, combatants);
        }

        /** Lets Miracle see whether this loss would be lethal to its holder. */
        public static HpLossVerdict validateHpLoss(
            List<StatusInstance> holderStatuses,
            int hpLoss,
            int currentHp,
            List<fr.insastronaute.webapi.sts.combat.model.CombatantState> combatants
        ) {
        if (hpLoss <= 0) {
            return new HpLossVerdict(hpLoss, holderStatuses);
        }
        int remaining = hpLoss;
        List<StatusInstance> statuses = new ArrayList<>(holderStatuses);
        for (int index = 0; index < statuses.size() && remaining > 0; index++) {
            StatusInstance status = statuses.get(index);
            switch (status.statusType()) {
                case "TimeStop" -> {
                    statuses.set(index, accumulating(status, remaining));
                    remaining = 0;
                }
                case "Regulator" ->
                        remaining = Math.min(remaining, Math.max(0,
                                regulatorCap(status) - status.progress()));
                case "Link" -> {
                    if (anchorStanding(combatants, status)) {
                        remaining = 0;
                    }
                }
                // Absorption laisse passer la moitié et garde l'autre pour la fin du tour, où
                // elle la rend d'un coup. Ce qu'elle retient s'accumule sur sa propre valeur,
                // comme l'Arrêt du temps, et se solde là-bas.
                case "Absorption" -> {
                    int deferred = remaining / StatusBehaviours.ABSORPTION_SHARE;
                    if (deferred > 0) {
                        statuses.set(index, accumulating(status, deferred));
                        remaining -= deferred;
                    }
                }
                case "Miracle" -> {
                    if (status.value() > 0 && remaining >= currentHp) {
                        remaining = Math.max(0, currentHp - 1);
                        if (status.value() == 1) {
                            statuses.remove(index--);
                        } else {
                            statuses.set(index, new StatusInstance(status.statusType(), status.value() - 1,
                                    status.duration(), status.cardId(), status.index(), status.removable(),
                                    status.resistsEverything(), status.progress()));
                        }
                    }
                }
                default -> {
                }
            }
        }
        return new HpLossVerdict(remaining, tallied(statuses, remaining));
    }

    /** How much a Regulator lets its holder lose in one turn (RegulatorStatus.maxDamage). */
    private static int regulatorCap(StatusInstance regulator) {
        return Math.max(5, 30 - regulator.value());
    }

    /** Charges every Regulator with what the blow ended up costing, as {@code OnHPLoss} does. */
    private static List<StatusInstance> tallied(List<StatusInstance> statuses, int lost) {
        if (lost <= 0) {
            return statuses;
        }
        List<StatusInstance> charged = new ArrayList<>(statuses);
        for (int index = 0; index < charged.size(); index++) {
            StatusInstance status = charged.get(index);
            if ("Regulator".equals(status.statusType())) {
                charged.set(index, status.withProgress(status.progress() + lost));
            }
        }
        return charged;
    }

    /** @return the same status, having swallowed {@code damage} more on top of what it held */
    public static StatusInstance accumulating(StatusInstance status, int damage) {
        return new StatusInstance(status.statusType(), status.value() + damage, status.duration(),
                status.cardId(), status.index(), status.removable(), status.resistsEverything(),
                status.progress());
    }

    /**
     * Whether the character a Link ties its holder to is still standing.
     *
     * <p>A Link is authored with a character's name, which the client shows and draws the icon
     * from; the server knows combatants by id alone, so the anchor is resolved once when the Link
     * is applied and kept as its place in the roster (see {@code StatusHandler}). The name is
     * still tried first, for a Link authored with an id outright.
     */
    private static boolean anchorStanding(
            List<fr.insastronaute.webapi.sts.combat.model.CombatantState> combatants,
            StatusInstance link
    ) {
        if (combatants == null || combatants.isEmpty()) {
            return false;
        }
        for (fr.insastronaute.webapi.sts.combat.model.CombatantState combatant : combatants) {
            if (combatant.combatantId().equals(link.cardId())) {
                return combatant.hp() > 0;
            }
        }
        int position = link.index() - 1;
        return position >= 0 && position < combatants.size()
                && combatants.get(position).hp() > 0;
    }
}
