package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierType;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * How a status changes the numbers of whoever it touches.
 *
 * <p>All of these live together rather than one class per status, because they are variations on
 * one idea and reading them side by side is how their interactions become obvious: Weakness and
 * Vuln both scale damage but from opposite ends of the blow, Dexterity and Fragile do the same to
 * armour, and Haste, Slow and Speed all pull on the same turn delay.
 *
 * <p>A status only acts from the side it sits on. Strength helps whoever throws the blow, Vuln
 * hurts whoever takes it, and neither does anything when the fight is looked at from the other
 * end.
 */
public final class StatusModifier implements StatModifier {

    /** Combien de fois de plus une carte repart sous Stellaire. */
    public static final int ASTRA_EXTRA_REPLAYS = 4;

    /** The stats Unity's {@code StatTypeChecker} keeps a generic status away from. */
    private static final Set<StatType> OUT_OF_A_GENERIC_STATUS_REACH = EnumSet.of(
            StatType.ANY, StatType.COST, StatType.TURN_DELAY, StatType.REPLAY_COUNT);

    /** Which end of a blow this status speaks for. */
    private enum Side {
        CASTER,
        RECIPIENT
    }

    private final StatusInstance status;
    private final CombatantState owner;
    private final StatType stat;
    private final Side side;
    private final ModifierType modifierType;

    private StatusModifier(
            StatusInstance status,
            CombatantState owner,
            StatType stat,
            Side side,
            ModifierType modifierType
    ) {
        this.status = Objects.requireNonNull(status);
        this.owner = Objects.requireNonNull(owner);
        this.stat = stat;
        this.side = side;
        this.modifierType = modifierType;
    }

    /**
     * @return the modifier this status contributes, or null when it changes no number
     */
    public static StatusModifier of(StatusInstance status, CombatantState owner) {
        return switch (status.statusType()) {
            case "Strength" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            case "Weakness" -> caster(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "Blind" -> caster(status, owner, StatType.DAMAGE, ModifierType.OVERRIDE);
            // Chaos : son porteur ne blesse plus personne. Comme Aveuglé, la remise à zéro est
            // calée sur le lanceur, ce qui la fait porter sur ce qu'il joue -- c'est-à-dire sur
            // son tour, le seul moment où il joue quelque chose.
            case "Chaotic" -> caster(status, owner, StatType.DAMAGE, ModifierType.OVERRIDE);
            case "Sadism" -> caster(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            // Les stades mûrs de Sadisme grossissent aussi l'armure que leur porteur se donne :
            // déclarés sur tout, ils ne retiennent que ces deux-là dans appliesTo.
            case "SadismII", "SadismIII" ->
                    caster(status, owner, StatType.ANY, ModifierType.MULTIPLICATIVE);
            case "Signal" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            // "Vigueur" : le prochain effet de dégâts est augmenté, puis elle se dissipe. Elle
            // était déclarée au catalogue et implémentée nulle part, donc elle n'ajoutait rien
            // et, n'ajoutant rien, ne partait jamais non plus. Sa dissipation est décidée par
            // CombatEngine, à la fin de la carte qui l'a consommée.
            case "Vigor" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            case "Momentum" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            // Rage ne frappe plus fort que ce qui n'est pas protégé : le test de l'armure est
            // dans appliesTo, pour que la cible soit lue au moment du coup et pas avant.
            case "Rage" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            case "MesuresEnvergure" -> caster(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "Freeze" -> caster(status, owner, StatType.ANY, ModifierType.MULTIPLICATIVE);
            // Galvanisation grossit tout ce que son porteur envoie, du pourcentage de PV qu'il
            // a perdu : elle parle donc de la même place que le Gel, sur tout ce qu'un statut
            // générique peut atteindre.
            case "Galvanisation" -> caster(status, owner, StatType.ANY, ModifierType.MULTIPLICATIVE);
            // Ténèbres ne frappe plus fort que ce que ses PV ne pèsent pas assez pour intimider.
            case "Tenebres" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            // Le Mur offensif lit l'armure de son porteur au moment du coup, jamais celle de la
            // cible : c'est ce que ADDITIVE calé sur le lanceur donne gratuitement.
            case "MurOffensif" -> caster(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            case "SeptiemeVitesse" -> caster(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "SuiteArithmetique" -> caster(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "Dexterity" -> caster(status, owner, StatType.ARMOR, ModifierType.ADDITIVE);
            case "Fragile" -> caster(status, owner, StatType.ARMOR, ModifierType.MULTIPLICATIVE);
            case "ArmorBlock" -> caster(status, owner, StatType.ARMOR, ModifierType.OVERRIDE);
            case "Haste", "Slow", "Speed" ->
                    caster(status, owner, StatType.TURN_DELAY, ModifierType.ADDITIVE);
                case "Echo" -> caster(status, owner, StatType.REPLAY_COUNT, ModifierType.ADDITIVE);
            // Stellaire fait repartir la carte quatre fois de plus. Ce qu'elle vaut alors est
            // ramené à 1 ailleurs, dans le résolveur : ici on ne compte que les redites.
            case "Astra" -> caster(status, owner, StatType.REPLAY_COUNT, ModifierType.ADDITIVE);
            // Critique double le coup. Ses charges se dépensent dans CombatEngine, une par carte
            // qui a effectivement frappé, comme la Vigueur.
            case "Critical" -> caster(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            // "Coût nul": the next cards its holder plays are free. Declared in the catalog and
            // implemented nowhere, so it neither zeroed a cost nor counted itself down.
            case "CostNullify" -> caster(status, owner, StatType.COST, ModifierType.OVERRIDE);

            case "Vuln" -> recipient(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "Exposure" -> recipient(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "Brand" -> recipient(status, owner, StatType.DAMAGE, ModifierType.ADDITIVE);
            case "DamageReduction", "AreaDmgReduction" ->
                    recipient(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
                case "Adrenaline" -> recipient(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE);
            case "RiskManagement" -> recipient(status, owner, StatType.DAMAGE, ModifierType.OVERRIDE);
            case "Jump", "Propulsion" -> recipient(status, owner, StatType.DAMAGE, ModifierType.OVERRIDE);
            case "FullBreak" -> recipient(status, owner, StatType.ANY, ModifierType.MULTIPLICATIVE);
            case "AdvanceImmunity" -> recipient(
                    status, owner, StatType.TURN_MANIPULATION_ADVANCE, ModifierType.OVERRIDE);
            case "DelayImmunity" -> recipient(
                    status, owner, StatType.TURN_MANIPULATION_DELAY, ModifierType.OVERRIDE);
                case "PowerPassive" -> "beton_precontraint".equals(status.cardId())
                    ? recipient(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE)
                        : "liaison_instable".equals(status.cardId())
                            ? recipient(status, owner, StatType.DAMAGE, ModifierType.MULTIPLICATIVE)
                    : null;
            default -> null;
        };
    }

    private static StatusModifier caster(
            StatusInstance status, CombatantState owner, StatType stat, ModifierType type) {
        return new StatusModifier(status, owner, stat, Side.CASTER, type);
    }

    private static StatusModifier recipient(
            StatusInstance status, CombatantState owner, StatType stat, ModifierType type) {
        return new StatusModifier(status, owner, stat, Side.RECIPIENT, type);
    }

    @Override
    public StatType type() {
        return stat;
    }

    @Override
    public ModifierType modifierType() {
        return modifierType;
    }

    @Override
    public boolean appliesTo(StatType asked, EffectContext ctx) {
        if (stat != StatType.ANY && stat != asked) {
            return false;
        }
        // A status declared over every stat still may not reach the ones Unity keeps out of its
        // reach (StatTypeChecker.cs:5): the cost of a card, the delay of a turn and the replay
        // count. Freeze halving a card's cost turns the debuff into the strongest buff in the
        // game.
        if (stat == StatType.ANY && OUT_OF_A_GENERIC_STATUS_REACH.contains(asked)) {
            return false;
        }
        CombatantState speaker = side == Side.CASTER ? ctx.source() : ctx.target();
        if (speaker == null || !speaker.combatantId().equals(owner.combatantId())) {
            return false;
        }
        return switch (status.statusType()) {
            case "Echo" -> ctx.card() != null
                    && !ctx.card().hasTag(CardTag.FOLLOW_UP)
                    && (status.index() == 0 || matchesType(status.index(), ctx.card().type()));
            // Reducing area damage only means anything when the blow is spread over several.
            case "AreaDmgReduction" -> ctx.targets() != null && ctx.targets().size() > 1;
                case "MesuresEnvergure" -> ctx.card() != null
                    && ctx.card().type() == CardType.ATTACK
                    && (ctx.card().targetingMode()
                        == fr.insastronaute.webapi.sts.combat.catalog.TargetingMode.ALL_ENEMIES
                    || ctx.card().targetingMode()
                        == fr.insastronaute.webapi.sts.combat.catalog.TargetingMode.ALL_CHARACTERS);
            // A follow-up is not the card Vigor was saved for (VigorStatus.cs:15).
            case "Vigor" -> ctx.card() == null || !ctx.card().hasTag(CardTag.FOLLOW_UP);
            case "Rage" -> ctx.target() != null && ctx.target().armor() <= 0;
            case "SadismII", "SadismIII" -> asked == StatType.DAMAGE || asked == StatType.ARMOR;
            case "Tenebres" -> ctx.source() != null && ctx.target() != null
                    && ctx.target().hp() > ctx.source().hp();
            case "MurOffensif", "SeptiemeVitesse" -> ctx.card() != null && ctx.card().type() == CardType.ATTACK;
                case "Exposure" -> ctx.card() != null
                    && (status.index() == 0 || matchesType(status.index(), ctx.card().type()));
                case "PowerPassive" -> "beton_precontraint".equals(status.cardId())
                        ? owner.armor() > 0
                    : ("liaison_instable".equals(status.cardId())
                        && ctx.source() != null && ctx.source().statuses().stream()
                            .anyMatch(other -> "Brand".equals(other.statusType())));
            default -> true;
        };
    }

    private static boolean matchesType(int index, CardType type) {
        return switch (index) {
            case 1 -> type == CardType.ATTACK;
            case 2 -> type == CardType.SKILL;
            case 3 -> type == CardType.POWER;
            default -> true;
        };
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        return switch (status.statusType()) {
            case "Strength", "Dexterity", "Vigor", "Momentum", "Rage" -> value + status.value();
            case "Echo" -> value + status.value();
            // Stellaire ne porte aucun nombre : les quatre redites sont les siennes, toujours.
            case "Astra" -> value + ASTRA_EXTRA_REPLAYS;
            case "Critical" -> Math.multiplyExact(value, 2);
            case "Weakness" -> (int) Math.floor(value * 0.75);
            case "Fragile" -> value * 75 / 100;
            case "Vuln" -> (int) Math.ceil(value * 1.5);
            case "Exposure" -> (int) Math.ceil(value * (100 + status.value()) / 100.0);
            case "Freeze" -> (int) Math.ceil(value * 0.5);
            case "Blind", "ArmorBlock", "AdvanceImmunity", "DelayImmunity", "CostNullify", "Jump", "Propulsion",
                 "Chaotic" -> 0;
            case "Sadism" -> (int) Math.floor(value + (value * 10) / 100.0);
            case "SadismII" -> (int) Math.floor(value + (value * 20) / 100.0);
            case "SadismIII" -> (int) Math.floor(value + (value * 30) / 100.0);
            case "Haste" -> value * 80 / 100;
            case "Slow" -> value * 120 / 100;
            case "Speed" -> value * (100 - speedReduction()) / 100;
            // A blow can only ever be this hard against whoever manages their risk.
            case "RiskManagement" -> Math.min(value, Math.max(5, 30 - status.value()));
            case "DamageReduction", "AreaDmgReduction" -> value - (value * status.value() / 100);
            case "Adrenaline" -> value - (value * adrenalineReductionPercent() / 100);
            case "MesuresEnvergure" -> (int) Math.ceil(value * 1.25);
            case "Tenebres" -> value + status.value();
            // 20% de l'armure telle qu'elle est au moment du coup, jamais celle promise plus tôt.
            case "MurOffensif" -> value + (int) Math.ceil(owner.armor() * 0.2);
            case "SeptiemeVitesse" -> value + (value * status.value() * 20 / 100);
            case "SuiteArithmetique" -> value + (value * status.value() / 100);
            case "Galvanisation" -> owner.maxHp() <= 0 ? value : value + (value
                    * Math.max(0, (owner.maxHp() - owner.hp()) * 100 / owner.maxHp()) / 100);
            case "FullBreak" -> (int) Math.ceil(value * 1.2);
                case "PowerPassive" -> "beton_precontraint".equals(status.cardId())
                    ? (int) Math.ceil(value * 0.85)
                        : "liaison_instable".equals(status.cardId())
                            ? (int) Math.floor(value * 0.85)
                    : value;
            // Brand counts the other debuffs on its holder, never itself: a target carrying
            // nothing but Brand takes no extra damage for it (BrandStatus.cs:22).
            case "Brand" -> value
                    + (value * status.value() * distinctDebuffsOn(ctx.target(), "Brand")) / 100;
            case "Signal" -> value + distinctDebuffsOn(ctx.target(), null) / 2;
            default -> value;
        };
    }

    /**
     * Speed climbs steeply at first and then flattens, so stacking it keeps helping without ever
     * removing the wait entirely.
     */
    private int speedReduction() {
        if (status.value() < 0) {
            return 0;
        }
        return Math.clamp(Math.round(33f * (float) Math.log10(status.value() + 1f)), 0, 99);
    }

    /** Prevention is half the bearer's missing-health percentage: 25% at half health, 50% at zero. */
    private int adrenalineReductionPercent() {
        if (owner.maxHp() <= 0) {
            return 0;
        }
        int missingPercent = Math.max(0, (owner.maxHp() - owner.hp()) * 100 / owner.maxHp());
        return missingPercent / 2;
    }

    private static int distinctDebuffsOn(CombatantState combatant, String excluded) {
        if (combatant == null) {
            return 0;
        }
        return (int) combatant.statuses().stream()
                .filter(StatusCatalog::isDebuff)
                .map(StatusInstance::statusType)
                .filter(statusType -> !statusType.equals(excluded))
                .distinct()
                .count();
    }
}
