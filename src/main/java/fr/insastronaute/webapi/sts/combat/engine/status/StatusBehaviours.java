package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.Set;

/**
 * What each status does as a turn begins and ends.
 *
 * <p>Only Poison did anything before, at the wrong moment, and every other status counted down on
 * the same schedule whether or not that was its own. Here each status keeps its own timing: some
 * act and count down as the turn opens, some as it closes, and some never count down at all.
 *
 * <p>The default, for the many statuses that only sit there and expire, is to count down at the
 * end of the turn.
 */
public final class StatusBehaviours {

    /** Suppresses the countdown of every debuff on whoever carries it. */
    private static final String DEBUFF_HALT = "DebuffHalt";

    /**
     * Ce que Ferveur rend pour ce qu'elle a retenu.
     *
     * <p>Plus haut que le 1,5 de l'Arrêt du temps : le soin retenu est un pari, pas une punition,
     * et il faut qu'il vaille la peine d'attendre.
     */
    private static final int FERVOR_MULTIPLIER = 2;

    /** La part d'une perte de PV qu'Absorption reporte à la fin du tour. */
    public static final int ABSORPTION_SHARE = 2;

    /** These act when the turn opens; anything not named here does not. */
    private static final Set<String> ACTS_AT_TURN_START = Set.of(
            "Jump", "Propulsion", "Poison", "Regen", "Shielding", "Production", "Sap", "Energy",
            "Thorns", "Vuln", "ConserveArmor", "DelayedDeath", "EnergyUp", "Hide", "Regulator",
            "DamageReduction", "AreaDmgReduction", "Provocation", "Adrenaline", "Miracle",
            "Analyse", "Compilation");

    /** These have their own idea of the turn's end; anything else simply counts down. */
    private static final Set<String> ACTS_AT_TURN_END = Set.of(
            "Burn", "Plating", "After", "Trap", "Accelerate", "Strengthen",
            "CardFollowUp", "AnyCardFollowUp", "Echo", "Continuous", "Absorption");

    private StatusBehaviours() {
    }

    public static StatusTurnEffect atTurnStart(StatusInstance status) {
        return switch (status.statusType()) {
                        case "Avenir" -> StatusTurnEffect.armor(status.value() / 4).withoutTick().clearingValue();
                        case "Propulsion" -> StatusTurnEffect.tick();
            case "Jump" -> StatusTurnEffect.tick();
            case "Poison" -> StatusTurnEffect.damage(status.duration());
            case "Regen" -> StatusTurnEffect.heal(status.duration());
            case "Shielding" -> StatusTurnEffect.armor(status.duration());
            // Production se lit comme Énergie : une valeur signée, sans fin. Positive, elle
            // pioche ; négative, elle défausse d'autant.
            case "Production" -> StatusTurnEffect.draw(status.value());
            // La Sape ne mord plus au tour : elle mord à chaque carte jouée, par qui que ce soit
            // (CombatEngine.biteWithSap). Elle reste nommée ici pour ne rien faire en fin de tour.
            case "Sap" -> StatusTurnEffect.nothing();
            case "Energy" -> StatusTurnEffect.energy(status.value());
            case "Thorns", "Vuln", "ConserveArmor", "DelayedDeath", "EnergyUp" ->
                    StatusTurnEffect.tick();
            case "DamageReduction", "AreaDmgReduction" -> StatusTurnEffect.tick();
            default -> StatusTurnEffect.nothing();
        };
    }

    /** The three afflictions "Statuts" rolls between, in Unity's own order. */
    private static final List<String> RANDOM_AFFLICTIONS =
            List.of("Stun", "Blind", "FullBreak");

    /** A turn-end effect and the randomness it spent getting there. */
    public record Rolled(StatusTurnEffect effect, RandomState random) {
    }

    /**
     * What a status does as the turn closes, for a caller that can pay for a die roll.
     *
     * <p>Only "Statuts" needs one -- it inflicts one of three afflictions at random -- and it is
     * the reason this overload exists: the pure {@link #atTurnEnd(StatusInstance)} had no way to
     * express a choice, so the status sat in the catalog doing nothing at all. Everything else
     * answers the same as it always did and hands the randomness straight back unspent.
     */
    public static Rolled atTurnEnd(StatusInstance status, RandomState random) {
        if (!"Status".equals(status.statusType())) {
            return new Rolled(atTurnEnd(status), random);
        }
        RandomState.RandomStep roll = random.nextInt(RANDOM_AFFLICTIONS.size());
        return new Rolled(
                StatusTurnEffect.applying(new StatusInstance(
                        RANDOM_AFFLICTIONS.get(roll.value()), 0, 1, "", 0)).withTick(),
                roll.next());
    }

    public static StatusTurnEffect atTurnEnd(StatusInstance status) {
        return switch (status.statusType()) {
            case "Burn" -> StatusTurnEffect.damage(status.duration());
            case "Plating" -> StatusTurnEffect.armor(status.duration());
            case "After", "Trap" -> StatusTurnEffect.damage(status.value());
            case "Accelerate" -> StatusTurnEffect.applying(
                    new StatusInstance("Speed", status.value(), -1, status.cardId(), status.index()));
            case "Strengthen" -> StatusTurnEffect.applying(
                    new StatusInstance("Strength", status.value(), -1, status.cardId(), status.index()));
            case "CardFollowUp", "AnyCardFollowUp" -> StatusTurnEffect.tick();
            // Its duration only moves when it actually fires, never on the turn clock alone.
            case "FieldTurnFollowUp" -> StatusTurnEffect.nothing();
            case "Echo", "Continuous" -> StatusTurnEffect.nothing();
            // La durée d'Aveuglé compte des coups annulés et non des tours : elle ne bouge que
            // quand un coup est effectivement réduit à néant (DamageHandler), ce qui la
            // distingue d'Étourdissement ou de Divination, qui s'écoulent au rythme des tours.
            case "Blind" -> StatusTurnEffect.nothing();
            // Absorption règle sa dette à chaque fin de tour et reste : elle vide donc son
            // compteur au passage, faute de quoi elle repaierait éternellement le même coup.
            case "Absorption" -> status.value() <= 0
                    ? StatusTurnEffect.nothing()
                    : StatusTurnEffect.damage(status.value()).withoutTick().clearingValue();
            // Statuses that already acted as the turn opened do not act again as it closes.
            default -> ACTS_AT_TURN_START.contains(status.statusType())
                    ? StatusTurnEffect.nothing()
                    : StatusTurnEffect.tick();
        };
    }

    /** What a status does on its way out. */
    public static StatusTurnEffect onExpiry(StatusInstance status) {
        return switch (status.statusType()) {
            case "Awakening" -> StatusTurnEffect.heal(status.value()).withoutTick();
            case "FullMoonTemp" -> StatusTurnEffect.applying(
                    new StatusInstance("Strength", -status.value(), -1, status.cardId(), status.index()))
                    .withoutTick();
            case "EnergyUp" -> StatusTurnEffect.energy(status.value());
            case "DelayedStun" -> StatusTurnEffect.applying(
                    new StatusInstance("Stun", 1, 1, status.cardId(), status.index()));
            case "DelayedDeath" -> new StatusTurnEffect(0, 0, 0, 0, null, false, true);
            // Time Stop hands back everything it swallowed, half again as hard. What it swallowed
            // is kept on its own value, which it is never authored with.
            case "TimeStop" -> status.value() <= 0
                    ? StatusTurnEffect.nothing()
                    : StatusTurnEffect.damage((int) Math.ceil(status.value() * 1.5)).withoutTick();
            // Ferveur est à l'Arrêt du temps ce que le soin est aux dégâts : elle retient ce qui
            // aurait été rendu et le restitue d'un coup, plus généreusement.
            case "Fervor" -> status.value() <= 0
                    ? StatusTurnEffect.nothing()
                    : StatusTurnEffect.heal(status.value() * FERVOR_MULTIPLIER).withoutTick();
            default -> StatusTurnEffect.nothing();
        };
    }

    /** @return true when this status lets its holder keep its armour into the new turn */
    public static boolean keepsArmor(StatusInstance status) {
        return "ConserveArmor".equals(status.statusType());
    }

    /**
     * A debuff stops counting down while its holder carries the status that halts debuffs, which
     * is what makes that status worth having.
     */
    public static boolean countsDown(StatusInstance status, List<StatusInstance> holderStatuses) {
        if (!StatusCatalog.isDebuff(status)) {
            return true;
        }
        return holderStatuses.stream()
                .noneMatch(other -> DEBUFF_HALT.equals(other.statusType()) && other != status);
    }

    /**
     * A status is gone once its countdown reaches zero, or once a signed status has nothing left
     * on it. A negative duration means it never runs out on its own.
     */
    public static boolean hasExpired(StatusInstance status) {
        return status.duration() == 0 || StatusCatalog.of(status.statusType()).expiresAt(status.value());
    }

    static Set<String> actsAtTurnEnd() {
        return ACTS_AT_TURN_END;
    }
}
