package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que les statuts répondent à une carte qui vient d'être jouée.
 *
 * <p>{@link StatusReaction} répond à un coup, dont il ne voit que les deux combattants ; ici on
 * répond à la carte entière, une fois qu'elle a fini de se résoudre et qu'on sait ce qu'elle a
 * fait de chacune de ses cibles. Domination a besoin de savoir que <em>toutes</em> les cibles ont
 * tout bloqué, Adaptation de quelle famille était la carte, et il n'y a qu'ici que ces deux
 * choses soient connues en même temps.
 */
public final class CardPlayReactions {

    private static final String ADAPTATION = "Adaptation";
    private static final String MOMENTUM = "Momentum";
    private static final String OVERPOWER = "Overpower";
    private static final String TIME_COMPRESSION = "TimeCompression";
    private static final int PERMANENT = -1;

    private CardPlayReactions() {
    }

    /**
     * @param combatants      la table telle que les réactions la laissent
     * @param repeatTargetIds les cibles qu'une Domination veut voir frapper une fois de plus
     * @param turnsToDelete   les combattants dont le prochain tour doit disparaître
     */
    public record Outcome(
            List<CombatantState> combatants,
            List<String> repeatTargetIds,
            List<String> turnsToDelete
    ) {

        public Outcome {
            combatants = List.copyOf(combatants);
            repeatTargetIds = List.copyOf(repeatTargetIds);
            turnsToDelete = List.copyOf(turnsToDelete);
        }
    }

    /**
     * Fait répondre les statuts à la carte que {@code casterId} vient de jouer.
     *
     * @param cardEvents ce que la carte a produit, d'où se lisent les coups et leur sort
     */
    public static Outcome toCardPlayed(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            String casterId,
            CardDefinition definition,
            List<String> targetIds,
            List<CombatEvent> cardEvents,
            List<CombatEvent> events
    ) {
        List<CombatantState> current = new ArrayList<>(combatants);
        CombatantState caster = find(current, casterId);
        if (caster == null) {
            return new Outcome(current, List.of(), List.of());
        }

        adapt(combatId, revision, current, caster, definition, events);
        advanceMomentum(combatId, revision, current, caster, definition, events);
        payForSeptiemeVitesse(combatId, revision, current, caster, definition, events);

        List<DamageApplied> blows = cardEvents.stream()
                .filter(DamageApplied.class::isInstance)
                .map(DamageApplied.class::cast)
                .filter(blow -> blow.sourceId().equals(casterId))
                .toList();

        return new Outcome(
                current,
                overpowerTargets(caster, definition, targetIds, blows),
                compressedTurns(caster, blows));
    }

    /**
     * Adaptation lit ce que l'autre bord vient de jouer et s'y règle : une Compétence lui donne
     * de la Force, une Attaque de la Dextérité, une Puissance de la Vitesse.
     *
     * <p>Elle répond à ce que jouent les <em>autres</em>, jamais à ce que joue son porteur : un
     * porteur qui s'adapterait à lui-même s'offrirait trois caractéristiques par tour sans que
     * personne n'y soit pour rien.
     */
    private static void adapt(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            CombatantState caster,
            CardDefinition definition,
            List<CombatEvent> events
    ) {
        String gained = adaptationStat(definition.type());
        if (gained == null || caster.teamId() == null) {
            return;
        }
        for (int index = 0; index < combatants.size(); index++) {
            CombatantState watcher = combatants.get(index);
            if (watcher.hp() <= 0
                    || caster.teamId().equals(watcher.teamId())
                    || watcher.statuses().stream()
                            .noneMatch(status -> ADAPTATION.equals(status.statusType()))) {
                continue;
            }
            List<StatusInstance> statuses = new ArrayList<>(watcher.statuses());
            StatusInstance applied = StatusOperations.insert(statuses,
                    new StatusInstance(gained, 1, PERMANENT, null, 0));
            combatants.set(index, withStatuses(watcher, statuses));
            events.add(new StatusApplied(combatId, revision, watcher.combatantId(),
                    watcher.combatantId(), applied.statusType(), applied.value(),
                    applied.duration(), false));
        }
    }

    /** @return la caractéristique qu'Adaptation gagne face à cette famille de carte, ou null */
    private static String adaptationStat(CardType type) {
        return switch (type) {
            case SKILL -> "Strength";
            case ATTACK -> "Dexterity";
            case POWER -> "Speed";
            default -> null;
        };
    }

    /** Non-attack cards build the stored damage that the next attack spends. */
    private static void advanceMomentum(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            CombatantState caster,
            CardDefinition definition,
            List<CombatEvent> events
    ) {
        if (definition.type() == CardType.ATTACK) {
            return;
        }
        int position = -1;
        for (int index = 0; index < combatants.size(); index++) {
            if (combatants.get(index).combatantId().equals(caster.combatantId())) {
                position = index;
                break;
            }
        }
        if (position < 0) {
            return;
        }
        CombatantState holder = combatants.get(position);
        List<StatusInstance> statuses = new ArrayList<>(holder.statuses());
        for (int index = 0; index < statuses.size(); index++) {
            StatusInstance status = statuses.get(index);
            if (!MOMENTUM.equals(status.statusType())) {
                continue;
            }
            StatusInstance advanced = new StatusInstance(status.statusType(),
                    Math.min(99, status.value() + 1), status.duration(), status.cardId(),
                    status.index(), status.removable(), status.resistsEverything(), status.progress());
            statuses.set(index, advanced);
            combatants.set(position, withStatuses(holder, statuses));
            events.add(new StatusApplied(combatId, revision, holder.combatantId(),
                    holder.combatantId(), advanced.statusType(), advanced.value(),
                    advanced.duration(), true));
            return;
        }
    }

    /**
     * Septième vitesse prend son prix sur chaque Attaque jouée, l'armure n'y coupant rien : ce
     * qu'elle rend en échange est un bonus de dégâts, pas une esquive.
     */
    private static void payForSeptiemeVitesse(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            CombatantState caster,
            CardDefinition definition,
            List<CombatEvent> events
    ) {
        if (definition.type() != CardType.ATTACK) {
            return;
        }
        int position = -1;
        for (int index = 0; index < combatants.size(); index++) {
            if (combatants.get(index).combatantId().equals(caster.combatantId())) {
                position = index;
                break;
            }
        }
        if (position < 0) {
            return;
        }
        CombatantState holder = combatants.get(position);
        int cost = holder.statuses().stream()
                .filter(status -> "SeptiemeVitesse".equals(status.statusType()))
                .mapToInt(StatusInstance::value)
                .sum();
        if (cost <= 0 || holder.hp() <= 0) {
            return;
        }
        int loss = Math.min(holder.hp(), cost);
        CombatantState wounded = new CombatantState(
                holder.combatantId(), holder.teamId(), holder.controllerType(), holder.maxHp(),
                holder.hp() - loss, holder.armor(), holder.energy(), holder.baseEnergy(),
                holder.turnDelayTicks(), holder.piles(), holder.statuses(), holder.relics(),
                holder.intentCardId(), holder.patternIndex(), holder.relicMemory());
        combatants.set(position, wounded);
        events.add(new fr.insastronaute.webapi.sts.combat.event.HpLost(combatId, revision,
                holder.combatantId(), holder.combatantId(), cost, loss, wounded.hp()));
    }

    /**
     * Domination : une attaque que toutes ses cibles ont entièrement bloquée repart une fois.
     *
     * <p>« Entièrement bloquée » veut dire que l'armure a tout pris et qu'aucun point de vie n'est
     * tombé — pas qu'il ne s'est rien passé. Une attaque qui n'a touché personne, ou qui a fait
     * ne serait-ce qu'un point à l'une de ses cibles, ne rejoue pas.
     */
    private static List<String> overpowerTargets(
            CombatantState caster,
            CardDefinition definition,
            List<String> targetIds,
            List<DamageApplied> blows
    ) {
        if (definition.type() != CardType.ATTACK
                || blows.isEmpty()
                || caster.statuses().stream()
                        .noneMatch(status -> OVERPOWER.equals(status.statusType()))) {
            return List.of();
        }
        Set<String> struck = new LinkedHashSet<>();
        for (DamageApplied blow : blows) {
            if (blow.hpLost() > 0 || blow.armorAbsorbed() <= 0) {
                return List.of();
            }
            struck.add(blow.targetId());
        }
        // Toutes les cibles visées doivent avoir bloqué, pas seulement celles qui ont été
        // touchées : une cible épargnée par le calcul n'a rien bloqué du tout.
        return struck.containsAll(targetIds) ? List.copyOf(struck) : List.of();
    }

    /**
     * Compression temporelle : chaque coup qui passe l'armure efface le prochain tour de qui l'a
     * pris.
     *
     * <p>« Non bloqué » se lit sur les points de vie perdus : c'est ce qui a traversé, quelle que
     * soit l'armure qu'il a fallu manger d'abord.
     */
    private static List<String> compressedTurns(CombatantState caster, List<DamageApplied> blows) {
        if (caster.statuses().stream()
                .noneMatch(status -> TIME_COMPRESSION.equals(status.statusType()))) {
            return List.of();
        }
        Set<String> deleted = new LinkedHashSet<>();
        for (DamageApplied blow : blows) {
            if (blow.hpLost() > 0) {
                deleted.add(blow.targetId());
            }
        }
        return List.copyOf(deleted);
    }

    private static CombatantState find(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElse(null);
    }

    private static CombatantState withStatuses(
            CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(), statuses,
                combatant.relics(), combatant.intentCardId(), combatant.patternIndex(),
                combatant.relicMemory());
    }
}
