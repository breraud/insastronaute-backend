package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.DamageRuleModifier;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.HealApplied;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.List;
import java.util.UUID;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import java.util.ArrayList;

public final class DamageHandler implements EffectHandler {

    private final RelicEngine relicEngine;

    public DamageHandler(RelicEngine relicEngine) {
        this.relicEngine = relicEngine;
    }

    public DamageHandler() {
        this.relicEngine = null;
    }

    @Override
    public ResolutionStep handle(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        CombatantState source = combatants.stream()
                .filter(c -> c.combatantId().equals(sourceId))
                .findFirst()
                .orElse(null);

        EffectContext ctx = new EffectContext(null, source, target, List.of(target), card, false, cardModifiers, timeline, combatants);
        int calculatedDamage = Math.max(
                0,
                BattleCalculator.getModifiedValue(effect.value(), StatType.DAMAGE, ctx));
        // Lunaire passe outre l'armure, comme l'enchantement qui le fait déjà : la question posée
        // plus bas est la même, seule la provenance de la réponse change. Les réductions de la
        // cible sont écartées ailleurs, dans BattleCalculator, parce qu'elles agissent sur le
        // nombre avant qu'il n'arrive ici.
        boolean ignoresArmor = cardModifiers.stream()
            .filter(DamageRuleModifier.class::isInstance)
            .map(DamageRuleModifier.class::cast)
            .anyMatch(DamageRuleModifier::ignoresArmor)
            || fr.insastronaute.webapi.sts.combat.engine.BattleCalculator.piercesDefences(source);
        boolean floorsDamageAtBase = cardModifiers.stream()
            .filter(DamageRuleModifier.class::isInstance)
            .map(DamageRuleModifier.class::cast)
            .anyMatch(DamageRuleModifier::floorsDamageAtBase);
            boolean precisionActivates = source != null
                && card != null
                && card.type() == fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK
                && card.targetingMode()
                    == fr.insastronaute.webapi.sts.combat.catalog.TargetingMode.SINGLE_ENEMY
                && calculatedDamage < effect.value()
                && source.statuses().stream().anyMatch(status -> "Precision".equals(status.statusType()));
            boolean momentumActivates = source != null
                && card != null
                && card.type() == fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK
                && source.statuses().stream().anyMatch(status -> "Momentum".equals(status.statusType()));
            floorsDamageAtBase = floorsDamageAtBase || precisionActivates;
        int damage = floorsDamageAtBase ? Math.max(effect.value(), calculatedDamage) : calculatedDamage;
        // Aveuglé se dépense ici, coup par coup. Sa durée compte des coups annulés et non des
        // tours, donc Aveuglé (2) annule exactement deux instances de dégâts et laisse passer la
        // troisième — y compris quand l'armure de la cible les aurait bloquées de toute façon :
        // ce qu'il compte, c'est ce que le porteur a tenté, pas ce que la cible a encaissé.
        CombatantState blindedSource = spendBlind(source, effect.value(), damage);
        List<CombatantState> withBlindSpent = combatants;
        if (blindedSource != null) {
            withBlindSpent = new ArrayList<>(combatants);
            replace(withBlindSpent, blindedSource);
            source = blindedSource;
        }

        int armorAbsorbed = ignoresArmor ? 0 : Math.min(target.armor(), damage);
        int throughArmor = ignoresArmor ? damage : damage - armorAbsorbed;
        // What gets past the armour still has the intercepting statuses to get past.
        StatusReaction.HpLossVerdict verdict =
            StatusReaction.validateHpLoss(target.statuses(), throughArmor, target.hp(), withBlindSpent);
        int hpLost = Math.min(target.hp(), verdict.hpLost());
        int remainingArmor = target.armor() - armorAbsorbed;
        boolean fortifyActivates = target.armor() > 0 && remainingArmor == 0
            && target.statuses().stream().anyMatch(status -> "Fortify".equals(status.statusType()));
        if (fortifyActivates) {
            remainingArmor = 1;
        }
        int remainingHp = target.hp() - hpLost;
        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                remainingHp,
                remainingArmor,
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                fortifyActivates ? withoutFirst(verdict.statuses(), "Fortify") : verdict.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        DamageApplied event = new DamageApplied(
                combatId,
                revision,
                sourceId,
                target.combatantId(),
                damage,
                armorAbsorbed,
                hpLost,
                remainingArmor,
                remainingHp
        );

        List<CombatEvent> events = new ArrayList<>();
        events.add(event);
        if (precisionActivates) {
            List<StatusInstance> remaining = new ArrayList<>();
            boolean spentPrecision = false;
            for (StatusInstance status : source.statuses()) {
                if (!spentPrecision && "Precision".equals(status.statusType())) {
                    spentPrecision = true;
                    events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                            combatId, revision, source.combatantId(), status.statusType(), status.value(),
                            0, 0, "expiry"));
                    continue;
                }
                remaining.add(status);
            }
            source = withStatuses(source, remaining);
        }
        if (momentumActivates) {
            source = withStatuses(source, withoutFirst(source.statuses(), "Momentum"));
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                    combatId, revision, source.combatantId(), "Momentum", 0, 0, 0, "expiry"));
        }
        if (fortifyActivates) {
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                    combatId, revision, target.combatantId(), "Fortify", 0, 0, 0, "expiry"));
        }
        List<TimelineEntry> currentTimeline = timeline;
        // Two different questions asked of the same moment: what the combatant who lost the armour
        // does about it, and what the one who broke it gains from it. The first reads the struck
        // one's relics, the second the striker's, and neither carries the other's.
        // A blow that takes the last of somebody's armour is an armour break, and until now
        // nothing said so: only the explicit Break effect ever emitted the event, so the counters
        // never saw one and every card gated on ArmorBreak stayed silent no matter how much
        // armour the card had just destroyed.
        if (target.armor() > 0 && remainingArmor == 0) {
            events.add(new fr.insastronaute.webapi.sts.combat.event.ArmorBroken(
                    combatId, revision, sourceId, target.combatantId(), armorAbsorbed));
        }

        // L'Horloge du frappeur : chaque coup porté repousse les tours de qui l'a pris, d'un
        // pourcentage de son propre délai (ClockStatus.OnDamageDealt). Elle répond au coup et non
        // à la carte -- une carte qui frappe trois fois retarde trois fois -- et elle ne demande
        // pas que les points de vie soient tombés : ce qu'elle compte, c'est le coup porté.
        currentTimeline = withClockDelay(source, target, damage, combatants, currentTimeline);

        CombatantState strikerAfterBreak = null;
        if (relicEngine != null && target.armor() > 0 && remainingArmor == 0) {
            currentTimeline = relicEngine.processArmorBroken(source, updated, currentTimeline, events);
            strikerAfterBreak = relicEngine.processTargetArmorBroken(source, updated);
        }

        if (source != null) {
            withBlindSpent = new ArrayList<>(withBlindSpent);
            replace(withBlindSpent, source);
        }
        if (source != null && !source.combatantId().equals(target.combatantId()) && damage > 0) {
            replace(withBlindSpent, chargeAvenir(source, damage));
        }
        List<CombatantState> updatedCombatants = new ArrayList<>(withBlindSpent);
        for (int i = 0; i < updatedCombatants.size(); i++) {
            if (updatedCombatants.get(i).combatantId().equals(updated.combatantId())) {
                updatedCombatants.set(i, updated);
            }
        }
        if (strikerAfterBreak != null && source != null && !strikerAfterBreak.equals(source)) {
            for (int i = 0; i < updatedCombatants.size(); i++) {
                if (updatedCombatants.get(i).combatantId().equals(strikerAfterBreak.combatantId())) {
                    updatedCombatants.set(i, strikerAfterBreak);
                }
            }
        }

        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep step = new ResolutionStep(updatedCombatants, currentTimeline, events, random);

        if (relicEngine != null && damage > 0) {
            step = relicEngine.processDamageTaken(updated, damage, updatedCombatants, currentTimeline, random, combatId, revision, events);
            // The striker's own relics answer the blow they landed, which is a different question
            // from the one the relics of whoever took it answer.
            // The striker is read from the roster rather than from the step, which may carry
            // only whoever took the blow.
            CombatantState striker = step.combatants().stream()
                    .filter(combatant -> combatant.combatantId().equals(sourceId))
                    .findFirst()
                    .orElseGet(() -> updatedCombatants.stream()
                            .filter(combatant -> combatant.combatantId().equals(sourceId))
                            .findFirst()
                            .orElse(null));
            if (striker != null && !striker.combatantId().equals(updated.combatantId())) {
                CombatantState answered =
                        relicEngine.processDamageDealt(striker, updated, damage);
                if (answered != null && !answered.equals(striker)) {
                    List<CombatantState> merged = new ArrayList<>(step.combatants());
                    if (merged.stream().noneMatch(combatant ->
                            combatant.combatantId().equals(answered.combatantId()))) {
                        merged.add(answered);
                    }
                    for (int index = 0; index < merged.size(); index++) {
                        if (merged.get(index).combatantId().equals(answered.combatantId())) {
                            merged.set(index, answered);
                        }
                    }
                    step = new ResolutionStep(
                            merged, step.timeline(), step.events(), step.random(),
                            step.pendingCardPlays());
                }
            }
        }

        // Reactions answer the blow that landed, not what the target managed to keep: Unity fires
        // them from OnDamageTaken, which never sees the interception.
        ResolutionStep reacted = withStatusReactions(step, updatedCombatants, sourceId,
            updated.combatantId(), damage, combatId, revision, events,
            !isRecrystallization(card));
        return sourceId.equals(updated.combatantId())
            ? withEmpathy(reacted, sourceId, damage / 2, combatId, revision, effect, card)
            : reacted;
    }

        private ResolutionStep withEmpathy(
            ResolutionStep step,
            String sourceId,
            int damage,
            UUID combatId,
            long revision,
            EffectDefinition originalEffect,
            CardDefinition card
        ) {
        CombatantState source = find(step.combatants(), sourceId);
        if (damage <= 0 || source == null || source.statuses().stream()
            .noneMatch(status -> "Empathie".equals(status.statusType()))) {
            return step;
        }
        List<CombatantState> combatants = new ArrayList<>(step.combatants());
        List<CombatEvent> events = new ArrayList<>(step.events());
        List<TimelineEntry> timeline = step.timeline();
        RandomState random = step.random();
        for (CombatantState opponent : List.copyOf(combatants)) {
            if (opponent.hp() <= 0 || source.teamId().equals(opponent.teamId())) {
            continue;
            }
            ResolutionStep reflected = handle(combatId, revision, sourceId, opponent,
                new EffectDefinition(originalEffect.type(), damage, false), random, combatants,
                timeline, card, List.of());
            combatants = new ArrayList<>(reflected.combatants());
            timeline = reflected.timeline();
            random = reflected.random();
            events.addAll(reflected.events());
        }
        return new ResolutionStep(combatants, timeline, events, random, step.pendingCardPlays());
        }

    private static CombatantState withStatuses(CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(combatant.combatantId(), combatant.teamId(),
                combatant.controllerType(), combatant.maxHp(), combatant.hp(), combatant.armor(),
                combatant.energy(), combatant.baseEnergy(), combatant.turnDelayTicks(),
                combatant.piles(), statuses, combatant.relics(), combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }

    private static CombatantState chargeAvenir(CombatantState combatant, int damage) {
        List<StatusInstance> statuses = new ArrayList<>(combatant.statuses());
        for (int index = 0; index < statuses.size(); index++) {
            StatusInstance status = statuses.get(index);
            if ("Avenir".equals(status.statusType())) {
                statuses.set(index, new StatusInstance(status.statusType(), status.value() + damage,
                        status.duration(), status.cardId(), status.index(), status.removable(),
                        status.resistsEverything(), status.progress()));
            }
        }
        return withStatuses(combatant, statuses);
    }

    private static List<StatusInstance> withoutFirst(List<StatusInstance> statuses, String statusType) {
        List<StatusInstance> remaining = new ArrayList<>();
        boolean removed = false;
        for (StatusInstance status : statuses) {
            if (!removed && statusType.equals(status.statusType())) {
                removed = true;
                continue;
            }
            remaining.add(status);
        }
        return remaining;
    }

    /**
     * Lets the statuses on both sides answer a blow that drew blood.
     *
     * <p>Reactions are read off the state as it stands after the blow, so thorns bite back with
     * what they are worth now rather than with what they were worth before it landed.
     */
    /** The card an armed Crystallize plays, whose own blow its count leaves out. */
    private static boolean isRecrystallization(CardDefinition card) {
        return card != null && "recristallisation".equalsIgnoreCase(card.id());
    }

    private static ResolutionStep withStatusReactions(
            ResolutionStep step,
            List<CombatantState> everyone,
            String sourceId,
            String targetId,
            int blowDealt,
            UUID combatId,
            long revision,
            List<CombatEvent> events,
            boolean feedsCrystallize
    ) {
        // A relic step only carries the combatant it touched, so the full roster is overlaid with
        // whatever it changed before anyone can react: thorns need to see who struck them.
        List<CombatantState> combatants = new ArrayList<>(everyone);
        step.combatants().forEach(combatant -> replace(combatants, combatant));

        CombatantState caster = find(combatants, sourceId);
        CombatantState recipient = find(combatants, targetId);
        if (caster == null || recipient == null || caster.combatantId().equals(targetId)) {
            return step;
        }
        StatusReaction.Outcome reaction =
                StatusReaction.toDamage(caster.statuses(), recipient.statuses(), blowDealt,
                        feedsCrystallize);
        if (reaction.isNothing()) {
            return step;
        }

        // Ce que les réactions annoncent part avec le pas qu'on rend. Les reliques rendent le leur
        // sur une copie de la liste d'événements : écrire dans {@code events} ne l'atteignait
        // plus, et les Épines mordaient l'attaquant sans que le client en sache rien -- ses PV
        // tombaient en silence, et on croyait voir le coup suivant du porteur frapper plus fort.
        List<CombatEvent> reactionEvents = new ArrayList<>();
        replace(combatants, applyReaction(caster, reaction.casterHpDelta(), 0,
                reaction.casterStatuses(), combatId, revision, reactionEvents));
        replace(combatants, applyReaction(find(combatants, targetId), 0, reaction.recipientArmor(),
                reaction.recipientStatuses(), combatId, revision, reactionEvents));
        replace(combatants, updateReactionStatuses(find(combatants, targetId),
                reaction.recipientStatusUpdates(), combatId, revision, reactionEvents));
        events.addAll(reactionEvents);
        List<CombatEvent> stepEvents = new ArrayList<>(step.events());
        if (step.events() != events) {
            stepEvents.addAll(reactionEvents);
        }
        return new ResolutionStep(combatants, step.timeline(), stepEvents, step.random(),
            step.pendingCardPlays());
    }

    private static CombatantState updateReactionStatuses(
            CombatantState combatant,
            List<StatusInstance> replacements,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (replacements.isEmpty()) {
            return combatant;
        }
        List<StatusInstance> statuses = new ArrayList<>(combatant.statuses());
        // Une réponse par instance, dans l'ordre. Les remplacements sont produits en parcourant
        // les statuts du porteur, donc le n-ième remplacement d'un type appartient au n-ième
        // statut de ce type : chercher par le seul nom retombait a chaque fois sur le premier,
        // et un combattant portant deux Étourdissements retardés en voyait un décompter deux
        // fois pendant que l'autre ne bougeait jamais.
        java.util.Map<String, Integer> answered = new java.util.HashMap<>();
        for (StatusInstance replacement : replacements) {
            int skip = answered.merge(replacement.statusType(), 1, Integer::sum) - 1;
            for (int index = 0; index < statuses.size(); index++) {
                StatusInstance status = statuses.get(index);
                if (status.statusType().equals(replacement.statusType()) && skip-- > 0) {
                    continue;
                }
                if (status.statusType().equals(replacement.statusType())) {
                    if (replacement.duration() == 0) {
                        statuses.remove(index);
                        events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                                combatId, revision, combatant.combatantId(), status.statusType(),
                                status.value(), 0, 0, "expiry"));
                        if ("DelayedStun".equals(status.statusType())) {
                            StatusInstance stun = StatusOperations.insert(statuses,
                                    new StatusInstance("Stun", 0, 1, status.cardId(), status.index()));
                            events.add(new StatusApplied(combatId, revision, combatant.combatantId(),
                                    combatant.combatantId(), stun.statusType(), stun.value(),
                                    stun.duration(), false));
                        }
                    } else {
                        statuses.set(index, replacement);
                        // Annoncé une fois, au coup qui l'arme : un Multihit qui retombe sur une
                        // Cristallisation déjà armée ne l'arme pas une seconde fois.
                        if ("Crystallize".equals(status.statusType()) && replacement.progress() > 0
                                && status.progress() <= 0) {
                            events.add(new StatusApplied(combatId, revision, combatant.combatantId(),
                                    combatant.combatantId(), replacement.statusType(), replacement.value(),
                                    replacement.duration(), true));
                        }
                    }
                    break;
                }
            }
        }
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), combatant.hp(), combatant.armor(), combatant.energy(),
                combatant.baseEnergy(), combatant.turnDelayTicks(), combatant.piles(), statuses,
                combatant.relics(), combatant.intentCardId(), combatant.patternIndex(),
                combatant.relicMemory());
    }

    private static CombatantState applyReaction(
            CombatantState combatant,
            int hpDelta,
            int armorDelta,
            List<StatusInstance> statuses,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        int hp = combatant.hp();
        if (hpDelta < 0) {
            int loss = Math.min(hp, -hpDelta);
            hp -= loss;
            events.add(new HpLost(combatId, revision, combatant.combatantId(),
                    combatant.combatantId(), -hpDelta, loss, hp));
        } else if (hpDelta > 0) {
            // Sublimation est la première réaction à soigner plutôt qu'à blesser : rien avant
            // elle n'avait jamais emprunté cette branche, qui n'existait donc pas.
            int healed = Math.min(combatant.maxHp() - hp, hpDelta);
            hp += healed;
            events.add(new HealApplied(combatId, revision, combatant.combatantId(),
                    combatant.combatantId(), hpDelta, healed, hp));
        }
        int armor = combatant.armor() + armorDelta;
        if (armorDelta != 0) {
            events.add(new ArmorGained(combatId, revision, combatant.combatantId(),
                    combatant.combatantId(), armorDelta, armorDelta, armor));
        }
        List<StatusInstance> updated = new ArrayList<>(combatant.statuses());
        for (StatusInstance status : statuses) {
            StatusInstance applied = StatusOperations.insert(updated, status);
            events.add(new StatusApplied(combatId, revision, combatant.combatantId(),
                    combatant.combatantId(), applied.statusType(), applied.value(),
                    applied.duration(), true));
        }
        return new CombatantState(
                combatant.combatantId(), combatant.teamId(), combatant.controllerType(),
                combatant.maxHp(), hp, armor, combatant.energy(), combatant.baseEnergy(),
                combatant.turnDelayTicks(), combatant.piles(), updated, combatant.relics(),
                combatant.intentCardId(), combatant.patternIndex(), combatant.relicMemory());
    }

    /**
     * Retire une charge d'Aveuglé au lanceur quand c'est bien lui qui vient d'annuler le coup.
     *
     * <p>Le statut ne s'écoule plus au fil des tours : c'est ici, et nulle part ailleurs, que sa
     * durée bouge. La condition est resserrée à un coup qui valait quelque chose et qui n'en vaut
     * plus rien, faute de quoi un effet de dégâts nul en dépenserait une pour rien.
     *
     * @return le lanceur amputé d'une charge, ou null quand rien n'était à dépenser
     */
    private static CombatantState spendBlind(CombatantState source, int authored, int dealt) {
        if (source == null || authored <= 0 || dealt > 0) {
            return null;
        }
        List<StatusInstance> statuses = new ArrayList<>(source.statuses());
        for (int index = 0; index < statuses.size(); index++) {
            StatusInstance status = statuses.get(index);
            if (!"Blind".equals(status.statusType()) || status.duration() == 0) {
                continue;
            }
            if (status.duration() < 0) {
                // Un Aveuglé sans compte ne se dépense pas : il dure jusqu'à ce qu'on le retire.
                return null;
            }
            int remaining = status.duration() - 1;
            if (remaining <= 0) {
                statuses.remove(index);
            } else {
                statuses.set(index, new StatusInstance(status.statusType(), status.value(),
                        remaining, status.cardId(), status.index(), status.removable(),
                        status.resistsEverything(), status.progress()));
            }
            return new CombatantState(
                    source.combatantId(), source.teamId(), source.controllerType(),
                    source.maxHp(), source.hp(), source.armor(), source.energy(),
                    source.baseEnergy(), source.turnDelayTicks(), source.piles(), statuses,
                    source.relics(), source.intentCardId(), source.patternIndex(),
                    source.relicMemory());
        }
        return null;
    }

    /** Le statut qui repousse les tours de qui son porteur frappe. */
    private static final String CLOCK = "Clock";

    /**
     * Ce que l'Horloge du frappeur fait au tour de qui vient d'être touché.
     *
     * <p>« Retardé de {Value×2}% », comme la carte l'annonce : un pourcentage du délai de la
     * cible, passé au même calcul que l'effet DelayTurn. Unity donnait ce nombre à
     * {@code ApplyDelayAllTurns}, qui compte en crans et non en pourcents, si bien que l'Horloge y
     * retardait environ quatre fois ce qu'elle promettait ; ici elle ne retardait rien du tout,
     * n'étant déclarée qu'au catalogue.
     *
     * <p>Plusieurs Horloges s'ajoutent, chacune la sienne.
     */
    private List<TimelineEntry> withClockDelay(
            CombatantState source,
            CombatantState target,
            int damage,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline
    ) {
        if (source == null || target == null || damage <= 0) {
            return timeline;
        }
        int percent = source.statuses().stream()
                .filter(status -> CLOCK.equals(status.statusType()))
                .mapToInt(status -> status.value() * 2)
                .sum();
        if (percent <= 0) {
            return timeline;
        }
        return DelayTurnHandler.delayedBy(percent, target, combatants, timeline, relicEngine);
    }

    private static CombatantState find(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElse(null);
    }

    private static void replace(List<CombatantState> combatants, CombatantState replacement) {
        for (int index = 0; index < combatants.size(); index++) {
            if (combatants.get(index).combatantId().equals(replacement.combatantId())) {
                combatants.set(index, replacement);
                return;
            }
        }
    }
}
