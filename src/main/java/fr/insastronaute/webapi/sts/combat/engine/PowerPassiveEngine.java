package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.engine.relic.StolenCardNaming;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.event.ArmorBroken;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves the permanent {@code PowerPassive} statuses carried by combatants.
 *
 * <p>The power id is deliberately the passive status' {@link StatusInstance#cardId()}, not the
 * name of a card currently in a pile. This keeps a played power independent from future catalog
 * changes and lets several powers of the same family retain independent per-turn state. The
 * {@code progress} field is reset at the owner's turn start and otherwise records whether a
 * first-per-turn trigger has fired.
 */
public final class PowerPassiveEngine {

    public record Result(List<CombatantState> combatants, List<CombatEvent> events) {
        public Result {
            combatants = List.copyOf(combatants);
            events = List.copyOf(events);
        }
    }

    private static final String POWER_PASSIVE = "PowerPassive";
    private final CardCatalog catalog;

    public PowerPassiveEngine(CardCatalog catalog) {
        this.catalog = catalog;
    }

    public Result onTurnStarted(UUID combatId, long revision, String combatantId,
            List<CombatantState> combatants) {
        CombatantState owner = find(combatants, combatantId);
        if (owner == null) {
            return unchanged(combatants);
        }
        List<CombatantState> current = replace(combatants, withResetProgress(owner));
        List<CombatEvent> events = new ArrayList<>();
        for (StatusInstance power : powersOf(owner)) {
            switch (power.cardId()) {
                case "mur_de_soutenement" -> current = applyStatus(
                        combatId, revision, combatantId, combatantId, "Shielding", 0, 4, current, events);
                case "ville_sous_cloche" -> {
                    int turns = power.value() + 1;
                    if (turns >= 3) {
                    current = applyStatus(combatId, revision, combatantId, combatantId,
                        "ConserveArmor", 1, 1, current, events);
                    turns = 0;
                    }
                    current = replace(current, replacePowerValue(
                        require(current, combatantId), power, turns));
                }
                case "plan_directeur" -> current = applyStatus(
                        combatId, revision, combatantId, combatantId, "Plating", 0, 2, current, events);
                case "second_souffle" -> {
                    CombatantState refreshed = require(current, combatantId);
                    if (refreshed.hp() * 2 <= refreshed.maxHp()) {
                        current = applyStatus(combatId, revision, combatantId, combatantId,
                                "Regen", 0, 2, current, events);
                    }
                }
                case "observation_continue" -> {
                    boolean enemyWillAttack = current.stream()
                            .filter(other -> !other.teamId().equals(owner.teamId()))
                            .map(CombatantState::intentCardId)
                            .map(catalog == null ? ignored -> null : catalog::find)
                            .anyMatch(intent -> intent != null && intent.type() == CardType.ATTACK);
                    if (enemyWillAttack) {
                        current = gainArmor(combatantId, 4, current);
                        events.add(new ArmorGained(combatId, revision, combatantId, combatantId,
                                4, 4, require(current, combatantId).armor()));
                    }
                }
                    case "preparation" -> {
                        if (power.value() == 0) {
                        current = applyStatus(combatId, revision, combatantId, combatantId,
                            "Artifact", 1, -1, current, events);
                        current = applyStatus(combatId, revision, combatantId, combatantId,
                            "Filter", 1, -1, current, events);
                        current = replace(current, replacePowerValue(
                            require(current, combatantId), power, 1));
                        }
                    }
                default -> { }
            }
        }
        return new Result(current, events);
    }

    public Result onTurnEnded(UUID combatId, long revision, String combatantId,
            List<CombatantState> combatants) {
        CombatantState owner = find(combatants, combatantId);
        if (owner == null) {
            return unchanged(combatants);
        }
        List<CombatantState> current = combatants;
        List<CombatEvent> events = new ArrayList<>();
        for (StatusInstance power : powersOf(owner)) {
            if ("contrepoids_pouvoir".equals(power.cardId()) && owner.armor() > 0) {
                CombatantState enemy = firstLivingEnemy(current, owner);
                if (enemy != null) {
                    current = applyStatus(combatId, revision, combatantId, enemy.combatantId(),
                            "Slow", 0, 1, current, events);
                }
            }
            if ("distillation_continue".equals(power.cardId()) && power.progress() >= 2) {
                current = applyStatus(combatId, revision, combatantId, combatantId,
                        "Regen", 0, 1, current, events);
            }
        }
        return new Result(current, events);
    }

    /**
     * Gives ITI's copy Powers the precise card-instance semantics of ITIRelic: the source
     * definition remains authoritative, while the copied instance is created, ethereal, and
     * exhausts itself when played. Contrefacon copies an opposing Attack once per owner turn;
     * Detournement copies opposing follow-up cards.
     */
    private List<CombatantState> copyOpponentCardForPowers(
            UUID combatId,
            long revision,
            CardPlayed played,
            CardDefinition card,
            boolean isFollowUp,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        CombatantState caster = find(combatants, played.actorId());
        if (caster == null || card == null) {
            return combatants;
        }
        List<CombatantState> current = combatants;
        for (CombatantState watcher : List.copyOf(combatants)) {
            if (watcher.hp() <= 0 || watcher.teamId().equals(caster.teamId())) {
                continue;
            }
            for (StatusInstance power : powersOf(watcher)) {
                boolean counterfeitsAttack = "contrefacon".equals(power.cardId())
                        && power.progress() == 0 && card.type() == CardType.ATTACK;
                boolean divertsFollowUp = "detournement".equals(power.cardId())
                    && (isFollowUp || card.hasTag(CardTag.FOLLOW_UP));
                if (!counterfeitsAttack && !divertsFollowUp) {
                    continue;
                }
                StatusInstance archive = powersOf(require(current, watcher.combatantId())).stream()
                    .filter(candidate -> "archivage".equals(candidate.cardId())
                        && candidate.progress() == 0)
                    .findFirst()
                    .orElse(null);
                current = copyToHand(combatId, revision, watcher.combatantId(), card,
                    power.cardId(), archive != null, current, events);
                if (archive != null) {
                    current = replace(current, markTriggered(
                        require(current, watcher.combatantId()), archive));
                }
                if (counterfeitsAttack) {
                    current = replace(current, markTriggered(
                            require(current, watcher.combatantId()), power));
                }
            }
        }
        return current;
    }

    private static List<CombatantState> copyToHand(
            UUID combatId,
            long revision,
            String ownerId,
            CardDefinition definition,
            String source,
            boolean discounted,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        CombatantState owner = require(combatants, ownerId);
        StolenCardNaming.StolenCard stolen = StolenCardNaming.describe(definition);
        CardInstance copy = new CardInstance(
                GeneratedCardId.fromContext(source, revision, ownerId, 0),
                definition.id(), List.of(), stolen.name(), stolen.targeting(),
                List.of(CardTag.ETHEREAL, CardTag.EXHAUST, CardTag.CREATED), List.of(), List.of(),
                stolen.type(), discounted ? Math.max(0, stolen.cost() - 1) : stolen.cost());
        List<CardInstance> hand = new ArrayList<>(owner.piles().hand());
        hand.add(copy);
        CardPiles piles = new CardPiles(owner.piles().draw(), hand, owner.piles().discard(),
                owner.piles().exhaust());
        CombatantState updated = withPiles(owner, piles);
        events.add(new CardMoved(combatId, revision, ownerId, copy.instanceId(), copy.definitionId(),
                "NONE", "HAND", hand.size() - 1, copy));
        return replace(combatants, updated);
    }

    public Result onCardPlayed(UUID combatId, long revision, CardPlayed played,
            CardDefinition definition, CardInstance instance, List<CombatantState> combatants) {
        CombatantState owner = find(combatants, played.actorId());
        if (owner == null) {
            return unchanged(combatants);
        }
        List<CombatantState> current = combatants;
        List<CombatEvent> events = new ArrayList<>();
        current = copyOpponentCardForPowers(combatId, revision, played, definition,
            instance != null && instance.tags().contains(CardTag.FOLLOW_UP), current, events);
        owner = find(current, played.actorId());
        if (owner == null) {
            return new Result(current, events);
        }
        for (StatusInstance power : powersOf(owner)) {
            if ("recurrence_pouvoir".equals(power.cardId())) {
                int familyBit = switch (definition.type()) {
                    case ATTACK -> 1;
                    case SKILL -> 2;
                    case POWER -> 4;
                    default -> 0;
                };
                if (familyBit != 0 && (power.progress() & familyBit) == 0) {
                    current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                            "Echo", 1, 1, current, events);
                    current = replace(current, replacePowerProgress(require(current, owner.combatantId()),
                            power, power.progress() | familyBit));
                }
                continue;
            }
            if ("suite_geometrique".equals(power.cardId())) {
                int attacks = definition.type() == CardType.ATTACK ? power.progress() + 1 : 0;
                if (definition.type() == CardType.ATTACK) {
                    current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                            "Vigor", 2, -1, current, events);
                }
                current = replace(current, replacePowerProgress(require(current, owner.combatantId()),
                        power, attacks));
                continue;
            }
            if (instance != null && instance.tags().contains(CardTag.CREATED)
                    && "bureau_des_methodes".equals(power.cardId())) {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                        "Vigor", 2, -1, current, events);
            }
            if (instance != null && instance.tags().contains(CardTag.CREATED)
                    && "mimetisme_actif".equals(power.cardId())) {
                String status = definition.type() == CardType.ATTACK ? "Vuln" : "Weakness";
                for (String targetId : played.targetIds()) {
                    current = applyStatus(combatId, revision, owner.combatantId(), targetId,
                            status, 0, 1, current, events);
                }
            }
            if (definition.type() == CardType.SKILL && "chaine_de_montage".equals(power.cardId())
                    && power.progress() == 0) {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                        "MechaArm", 1, -1, current, events);
                StatusInstance arm = firstStatus(require(current, owner.combatantId()), "MechaArm");
                if (arm != null && arm.value() >= 3) {
                    current = replaceStatus(current, owner.combatantId(), arm, 0);
                    current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                            "Strength", 2, -1, current, events);
                }
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
                continue;
            }
            if (definition.type() == CardType.SKILL && "fortification_mobile".equals(power.cardId())) {
                int skills = power.progress() + 1;
                if (skills >= 3) {
                    current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                            "ConserveArmor", 1, 1, current, events);
                    current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                            "Dexterity", 1, -1, current, events);
                    skills = 0;
                }
                current = replace(current, replacePowerProgress(
                        require(current, owner.combatantId()), power, skills));
                continue;
            }
            if ("memoire_musculaire".equals(power.cardId())) {
                int cards = power.progress() + 1;
                if (cards >= 4) {
                    current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                            "Dexterity", 1, -1, current, events);
                    cards = 0;
                }
                current = replace(current, replacePowerProgress(
                        require(current, owner.combatantId()), power, cards));
                continue;
            }
            if (power.progress() != 0) {
                continue;
            }
            String status = null;
            int value = 0;
            int duration = -1;
            if (definition.type() == CardType.ATTACK && "discipline".equals(power.cardId())) {
                status = "Vigor";
                value = 2;
            } else if (definition.type() == CardType.ATTACK
                    && "pont_suspendu".equals(power.cardId())) {
                status = "ConserveArmor";
                value = 1;
                duration = 1;
            } else if (definition.type() == CardType.SKILL && "permis_accelere".equals(power.cardId())) {
                status = "Haste";
                duration = 1;
            } else if (definition.type() == CardType.ATTACK && "milieu_reactif".equals(power.cardId())) {
                status = "Burn";
                duration = 1;
            }
            if (status == null) {
                continue;
            }
            if ("Burn".equals(status) && !played.targetIds().isEmpty()) {
                for (String targetId : played.targetIds()) {
                    current = applyStatus(combatId, revision, owner.combatantId(), targetId,
                            status, value, duration, current, events);
                }
            } else {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                        status, value, duration, current, events);
            }
            if (!"ConserveArmor".equals(status)) {
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
            }
        }
        return new Result(current, events);
    }

    /** Observes events emitted by all effect handlers after their immutable state changes. */
    public Result onEvents(UUID combatId, long revision, List<CombatEvent> observed,
            List<CombatantState> combatants) {
        List<CombatantState> current = combatants;
        List<CombatEvent> generated = new ArrayList<>();
        for (CombatEvent event : observed) {
            if (event instanceof ArmorGained armor && armor.appliedArmor() > 0) {
                current = reactToArmorGain(combatId, revision, armor, current, generated);
            } else if (event instanceof ArmorBroken broken) {
                current = reactToArmorBreak(combatId, revision, broken, current, generated);
            } else if (event instanceof EnergyGained energy && energy.amount() > 0) {
                current = reactToEnergyGain(combatId, revision, energy, current, generated);
            } else if (event instanceof EnergySpent spent && spent.amount() > 0) {
                current = reactToEnergySpent(combatId, revision, spent, current, generated);
            } else if (event instanceof DamageApplied damage && damage.hpLost() > 0) {
                current = reactToUnblockedDamage(combatId, revision, damage, current, generated);
            } else if (event instanceof CardMoved moved && "EXHAUST".equals(moved.toPile())) {
                current = reactToExhaust(combatId, revision, moved, current, generated);
            } else if (event instanceof StatusApplied applied) {
                current = reactToStatusApplied(combatId, revision, applied, current, generated);
                current = reactToEnemyBuffApplied(combatId, revision, applied, current, generated);
            }
        }
        return new Result(current, generated);
    }

    private List<CombatantState> reactToArmorGain(UUID combatId, long revision, ArmorGained event,
            List<CombatantState> current, List<CombatEvent> events) {
        CombatantState owner = find(current, event.targetId());
        if (owner == null) return current;
        for (StatusInstance power : powersOf(owner)) {
            if (power.progress() != 0) continue;
            String status = switch (power.cardId()) {
                case "fondations_profondes" -> "Steadfast";
                case "armature_vivante" -> "Strength";
                default -> null;
            };
            if (status != null) {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                    status, 1, "Steadfast".equals(status) ? 2 : 1, current, events);
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
            }
        }
        return current;
    }

    private List<CombatantState> reactToExhaust(UUID combatId, long revision, CardMoved event,
            List<CombatantState> current, List<CombatEvent> events) {
        CombatantState owner = find(current, event.combatantId());
        if (owner == null) {
            return current;
        }
        for (StatusInstance power : powersOf(owner)) {
            if ("dossier_classe".equals(power.cardId()) && power.progress() == 0) {
                current = gainEnergy(owner.combatantId(), 1, current);
                events.add(new EnergyGained(combatId, revision, owner.combatantId(), 1,
                        require(current, owner.combatantId()).energy()));
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
            }
        }
        return current;
    }

    private List<CombatantState> reactToArmorBreak(UUID combatId, long revision, ArmorBroken event,
            List<CombatantState> current, List<CombatEvent> events) {
        CombatantState owner = find(current, event.targetId());
        if (owner == null) return current;
        for (StatusInstance power : powersOf(owner)) {
            if (power.progress() != 0) continue;
            String status = switch (power.cardId()) {
                case "plan_directeur" -> "Strength";
                case "structure_sacrificielle", "procedure_d_urgence" -> "Artifact";
                default -> null;
            };
            if (status != null) {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                        status, 1, -1, current, events);
                if ("procedure_d_urgence".equals(power.cardId())) {
                    current = gainEnergy(owner.combatantId(), 1, current);
                    events.add(new EnergyGained(combatId, revision, owner.combatantId(), 1,
                            require(current, owner.combatantId()).energy()));
                }
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
            }
        }
        return current;
    }

    private List<CombatantState> reactToUnblockedDamage(UUID combatId, long revision,
            DamageApplied event, List<CombatantState> current, List<CombatEvent> events) {
        CombatantState owner = find(current, event.targetId());
        if (owner == null) {
            return current;
        }
        for (StatusInstance power : powersOf(owner)) {
            if ("armature_vivante".equals(power.cardId())) {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                        "Strength", -1, -1, current, events);
            }
            if (!"instinct_de_survie".equals(power.cardId()) || power.progress() != 0) {
                continue;
            }
            current = gainArmor(owner.combatantId(), 5, current);
            events.add(new ArmorGained(combatId, revision, owner.combatantId(), owner.combatantId(),
                    5, 5, require(current, owner.combatantId()).armor()));
            current = replace(current, markTriggered(require(current, owner.combatantId()), power));
        }
        return current;
    }

    private List<CombatantState> reactToEnergyGain(UUID combatId, long revision, EnergyGained event,
            List<CombatantState> current, List<CombatEvent> events) {
        CombatantState owner = find(current, event.combatantId());
        if (owner == null) return current;
        for (StatusInstance power : powersOf(owner)) {
                if (power.progress() == 0 && ("condensateur".equals(power.cardId())
                    || "condensateur_pouvoir".equals(power.cardId()))) {
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                        "Vigor", 2, -1, current, events);
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
            }
        }
        return current;
    }

    private List<CombatantState> reactToEnergySpent(UUID combatId, long revision, EnergySpent event,
            List<CombatantState> current, List<CombatEvent> events) {
        CombatantState owner = find(current, event.combatantId());
        if (owner == null) return current;
        for (StatusInstance power : powersOf(owner)) {
            if (event.remainingEnergy() == 0 && "circuit_de_secours".equals(power.cardId())
                    && power.progress() == 0) {
                current = gainArmor(owner.combatantId(), 4, current);
                events.add(new ArmorGained(combatId, revision, owner.combatantId(), owner.combatantId(),
                    4, 4, require(current, owner.combatantId()).armor()));
                current = replace(current, markTriggered(require(current, owner.combatantId()), power));
            }
            if ("surcharge_controlee".equals(power.cardId())) {
                int spent = power.progress() + event.amount();
                while (spent >= 3) {
                    CombatantState enemy = firstLivingEnemy(current, owner);
                    if (enemy != null) {
                        current = applyStatus(combatId, revision, owner.combatantId(),
                                enemy.combatantId(), "Weakness", 0, 1, current, events);
                    }
                    spent -= 3;
                }
                current = replace(current, replacePowerProgress(
                        require(current, owner.combatantId()), power, spent));
            }
                if (event.remainingEnergy() == 0 && "derniere_reserve".equals(power.cardId())
                    && power.value() == 0) {
                current = gainEnergy(owner.combatantId(), 2, current);
                events.add(new EnergyGained(combatId, revision, owner.combatantId(), 2,
                    require(current, owner.combatantId()).energy()));
                current = applyStatus(combatId, revision, owner.combatantId(), owner.combatantId(),
                    "Weakness", 0, 1, current, events);
                current = replace(current, replacePowerValue(
                    require(current, owner.combatantId()), power, 1));
                }
        }
        return current;
    }

    private List<CombatantState> reactToStatusApplied(UUID combatId, long revision, StatusApplied event,
            List<CombatantState> current, List<CombatEvent> events) {
        CombatantState source = find(current, event.sourceId());
        if (source == null) return current;
        StatusInstance incoming = StatusCatalog.instantiate(event.statusType(), event.value(), event.duration(), "", 0);
        for (StatusInstance power : powersOf(source)) {
            if ("catalyseur".equals(power.cardId()) && !"Brand".equals(event.statusType())
                && StatusCatalog.isDebuff(incoming, catalog)) {
                current = applyStatus(combatId, revision, source.combatantId(), event.combatantId(),
                        "Brand", 1, 1, current, events);
            }
            if ("distillation_continue".equals(power.cardId())
                && StatusCatalog.isDebuff(incoming, catalog)) {
            current = replace(current, replacePowerProgress(
                require(current, source.combatantId()), power, power.progress() + 1));
            }
        }
        if ("Crystallize".equals(event.statusType())) {
            CombatantState crystallized = find(current, event.combatantId());
            // Le maximum est celui du porteur : deux sous Mégacristal, trois sinon.
            if (crystallized != null && event.value()
                    >= fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction
                            .crystallizeThreshold(crystallized.statuses())) {
                for (CombatantState watcher : List.copyOf(current)) {
                    if (watcher.hp() <= 0 || watcher.teamId().equals(crystallized.teamId())
                            || powersOf(watcher).stream().noneMatch(candidate ->
                                    "reaction_en_chaine".equals(candidate.cardId()))) {
                        continue;
                    }
                    current = gainEnergy(watcher.combatantId(), 1, current);
                    events.add(new EnergyGained(combatId, revision, watcher.combatantId(), 1,
                            require(current, watcher.combatantId()).energy()));
                }
            }
        }
        return current;
    }

    /** Lets a Power holder answer buffs granted to the other team. */
    private List<CombatantState> reactToEnemyBuffApplied(UUID combatId, long revision, StatusApplied event,
            List<CombatantState> current, List<CombatEvent> events) {
        StatusInstance incoming = StatusCatalog.instantiate(
                event.statusType(), event.value(), event.duration(), "", 0);
        if (!StatusCatalog.isBuff(incoming, catalog)) {
            return current;
        }
        CombatantState recipient = find(current, event.combatantId());
        if (recipient == null) {
            return current;
        }
        for (CombatantState watcher : List.copyOf(current)) {
            if (watcher.hp() <= 0 || watcher.teamId().equals(recipient.teamId())) {
                continue;
            }
            for (StatusInstance power : powersOf(watcher)) {
                if ("brouillage_permanent".equals(power.cardId())) {
                    current = applyStatus(combatId, revision, watcher.combatantId(),
                            recipient.combatantId(), "DispelBlock", 0, 2, current, events);
                }
                if ("fenetre_d_intervention".equals(power.cardId()) && power.progress() == 0) {
                    current = applyStatus(combatId, revision, watcher.combatantId(),
                            recipient.combatantId(), "Slow", 0, 1, current, events);
                    current = applyStatus(combatId, revision, watcher.combatantId(),
                            recipient.combatantId(), "Vuln", 0, 1, current, events);
                    current = replace(current, markTriggered(
                            require(current, watcher.combatantId()), power));
                }
            }
        }
        return current;
    }

    private List<CombatantState> applyStatus(UUID combatId, long revision, String sourceId, String targetId,
            String statusType, int value, int duration, List<CombatantState> combatants, List<CombatEvent> events) {
        CombatantState source = find(combatants, sourceId);
        CombatantState target = find(combatants, targetId);
        if (target == null) return combatants;
        StatusInstance incoming = StatusCatalog.instantiate(statusType, value, duration, "", 0);
        StatusOperations.Application application = StatusOperations.apply(target.statuses(), incoming,
                source == null ? List.of() : source.statuses(), catalog);
        CombatantState updated = withStatuses(target, application.statuses());
        if (application.applied()) {
            StatusInstance result = Objects.requireNonNull(application.result());
            events.add(new StatusApplied(combatId, revision, sourceId, targetId, statusType,
                    result.value(), result.duration(), application.stacked()));
        }
        return replace(combatants, updated);
    }

    private static CombatantState withResetProgress(CombatantState combatant) {
        return withStatuses(combatant, combatant.statuses().stream()
            .map(status -> POWER_PASSIVE.equals(status.statusType())
                && !keepsProgressAcrossTurns(status.cardId())
                    ? status.withProgress(0)
                    : status)
                .toList());
    }

        /** Counters for these Powers span turns; all other progress is a once-per-turn marker. */
        private static boolean keepsProgressAcrossTurns(String powerId) {
        return "fortification_mobile".equals(powerId)
            || "memoire_musculaire".equals(powerId);
        }

    private static CombatantState markTriggered(CombatantState combatant, StatusInstance triggered) {
        return withStatuses(combatant, combatant.statuses().stream().map(status ->
                status == triggered ? status.withProgress(1) : status).toList());
    }

        private static CombatantState replacePowerProgress(
            CombatantState combatant, StatusInstance power, int progress) {
        return withStatuses(combatant, combatant.statuses().stream().map(status ->
            status == power ? status.withProgress(progress) : status).toList());
        }

        private static CombatantState replacePowerValue(
                CombatantState combatant, StatusInstance power, int value) {
            return withStatuses(combatant, combatant.statuses().stream().map(status ->
                    status == power ? new StatusInstance(status.statusType(), value, status.duration(),
                            status.cardId(), status.index(), status.removable(), status.resistsEverything(),
                            status.progress()) : status).toList());
        }

        private static StatusInstance firstStatus(CombatantState combatant, String statusType) {
        return combatant.statuses().stream()
            .filter(status -> statusType.equals(status.statusType()))
            .findFirst()
            .orElse(null);
        }

        private static List<CombatantState> replaceStatus(
            List<CombatantState> combatants, String combatantId, StatusInstance previous, int value) {
        CombatantState owner = require(combatants, combatantId);
        List<StatusInstance> statuses = owner.statuses().stream().map(status -> status == previous
            ? new StatusInstance(status.statusType(), value, status.duration(), status.cardId(),
                status.index(), status.removable(), status.resistsEverything(), status.progress())
            : status).toList();
        return replace(combatants, withStatuses(owner, statuses));
        }

    private static CombatantState withStatuses(CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(), combatant.baseEnergy(),
                combatant.turnDelayTicks(), combatant.piles(), statuses, combatant.relics(), combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }

    private static CombatantState withPiles(CombatantState combatant, CardPiles piles) {
        return new CombatantState(combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(), combatant.baseEnergy(),
                combatant.turnDelayTicks(), piles, combatant.statuses(), combatant.relics(),
                combatant.intentCardId(), combatant.patternIndex(), combatant.relicMemory());
    }

    private static List<StatusInstance> powersOf(CombatantState combatant) {
        return combatant.statuses().stream().filter(status -> POWER_PASSIVE.equals(status.statusType())).toList();
    }

    private static List<CombatantState> gainArmor(String combatantId, int amount, List<CombatantState> combatants) {
        CombatantState owner = require(combatants, combatantId);
        CombatantState updated = new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), Math.addExact(owner.armor(), amount), owner.energy(), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), owner.statuses(), owner.relics(), owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
        return replace(combatants, updated);
    }

    private static List<CombatantState> gainEnergy(String combatantId, int amount, List<CombatantState> combatants) {
        CombatantState owner = require(combatants, combatantId);
        CombatantState updated = new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), Math.addExact(owner.energy(), amount), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), owner.statuses(), owner.relics(), owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
        return replace(combatants, updated);
    }

    private static CombatantState firstLivingEnemy(List<CombatantState> combatants, CombatantState owner) {
        return combatants.stream().filter(candidate -> candidate.hp() > 0
                && !candidate.teamId().equals(owner.teamId())).findFirst().orElse(null);
    }

    private static CombatantState find(List<CombatantState> combatants, String combatantId) {
        return combatants.stream().filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst().orElse(null);
    }

    private static CombatantState require(List<CombatantState> combatants, String combatantId) {
        return Objects.requireNonNull(find(combatants, combatantId), "combatant must exist");
    }

    private static List<CombatantState> replace(List<CombatantState> combatants, CombatantState replacement) {
        List<CombatantState> updated = new ArrayList<>(combatants);
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).combatantId().equals(replacement.combatantId())) {
                updated.set(index, replacement);
                return List.copyOf(updated);
            }
        }
        return combatants;
    }

    private static Result unchanged(List<CombatantState> combatants) {
        return new Result(combatants, List.of());
    }
}