package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionAction;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.effect.AddCardToDiscardPileHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.AddCardToDrawPileHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.AddCardToHandHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.AddCopyOfCardHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.AddRandomCardHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.SetStatusToMaxValueHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ForceNextCardHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.CardSelectionHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.FrameStatusesHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.MultihitHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.GravityHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.BreakHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.StealBuffHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.TransferDebuffHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.AdvanceTurnHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DelayTurnHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.CutInTurnHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DeleteNextTurnHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.EndTurnHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DispelDebuffsIntoDamageHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DispelBuffsIntoStatusHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ArmorHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DamageHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DiscardHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DrawHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.EffectHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ExhaustHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.GainEnergyHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.HealHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.LoseHpHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DoubleDebuffsHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DispelBuffHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DispelDebuffHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.DispelSpecificStatusHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ExtendStatusesHandler;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.effect.StatusHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;

import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.stream.Stream;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;

public final class EffectResolver {

    /** How deep a chain of cards playing other cards may run before it is cut short. */
    private static final int MAX_PENDING_PLAY_DEPTH = 4;

    private int pendingPlayDepth;

    private final Map<EffectType, EffectHandler> handlers;
    private final CardCatalog catalog;
    private final RelicEngine relicEngine;
    private EnchantmentEngine enchantmentEngine;
    private final PowerPassiveEngine powerPassiveEngine;

    public EffectResolver(CardCatalog catalog, RelicEngine relicEngine) {
        this(catalog, relicEngine, new EnchantmentEngine(java.util.List.of()));
    }

    public EffectResolver(CardCatalog catalog, RelicEngine relicEngine, EnchantmentEngine enchantmentEngine) {
        this.relicEngine = relicEngine;
        this.catalog = catalog;
        this.enchantmentEngine = enchantmentEngine;
        this.powerPassiveEngine = new PowerPassiveEngine(catalog);
        this.handlers = Map.ofEntries(
                Map.entry(EffectType.DAMAGE, new DamageHandler(relicEngine)),
                Map.entry(EffectType.ARMOR, new ArmorHandler(relicEngine)),
                Map.entry(EffectType.DRAW, new DrawHandler(catalog)),
                Map.entry(EffectType.GAIN_ENERGY, new GainEnergyHandler()),
                Map.entry(EffectType.HEAL, new HealHandler(relicEngine)),
                Map.entry(EffectType.LOSE_HP, new LoseHpHandler(relicEngine)),
                Map.entry(EffectType.STATUS, new StatusHandler(relicEngine, catalog)),
                Map.entry(EffectType.DISCARD, new DiscardHandler()),
                Map.entry(EffectType.EXHAUST, new ExhaustHandler()),
                Map.entry(EffectType.DISPEL_BUFF, new DispelBuffHandler(catalog)),
                Map.entry(EffectType.DISPEL_DEBUFF, new DispelDebuffHandler(catalog)),
                Map.entry(EffectType.DISPEL_SPECIFIC_STATUS, new DispelSpecificStatusHandler()),
                Map.entry(EffectType.DOUBLE_DEBUFFS, new DoubleDebuffsHandler()),
                Map.entry(EffectType.EXTEND_STATUSES, new ExtendStatusesHandler()),
                Map.entry(EffectType.ADD_CARD_TO_HAND, new AddCardToHandHandler()),
                Map.entry(EffectType.ADD_CARD_TO_DRAW_PILE, new AddCardToDrawPileHandler()),
                Map.entry(EffectType.ADD_CARD_TO_DISCARD_PILE, new AddCardToDiscardPileHandler()),
                Map.entry(EffectType.ADD_COPY_OF_CARD, new AddCopyOfCardHandler()),
                Map.entry(EffectType.MULTIHIT, new MultihitHandler(relicEngine)),
                Map.entry(EffectType.GRAVITY, new GravityHandler(relicEngine)),
                Map.entry(EffectType.BREAK, new BreakHandler(relicEngine)),
                Map.entry(EffectType.STEAL_BUFF, new StealBuffHandler(catalog)),
                Map.entry(EffectType.TRANSFER_DEBUFF, new TransferDebuffHandler(catalog)),
                Map.entry(EffectType.ADVANCE_TURN, new AdvanceTurnHandler(relicEngine)),
                Map.entry(EffectType.DELAY_TURN, new DelayTurnHandler(relicEngine)),
                Map.entry(EffectType.CUT_IN_TURN, new CutInTurnHandler(relicEngine)),
                Map.entry(EffectType.DELETE_NEXT_TURN, new DeleteNextTurnHandler()),
                Map.entry(EffectType.END_TURN, new EndTurnHandler()),
                Map.entry(EffectType.DISPEL_DEBUFFS_INTO_DAMAGE, new DispelDebuffsIntoDamageHandler()),
                Map.entry(EffectType.DISPEL_BUFFS_INTO_STATUS, new DispelBuffsIntoStatusHandler()),
                Map.entry(EffectType.SET_STATUS_TO_MAX_VALUE, new SetStatusToMaxValueHandler()),
                Map.entry(EffectType.FORCE_NEXT_CARD, new ForceNextCardHandler()),
                Map.entry(EffectType.ADD_RANDOM_CARD, new AddRandomCardHandler(catalog)),
                Map.entry(EffectType.CARD_SELECTION, new CardSelectionHandler(catalog)),
                Map.entry(EffectType.FRAME_BUFFS, new FrameStatusesHandler(true)),
                Map.entry(EffectType.FRAME_DEBUFFS, new FrameStatusesHandler(false))
        );
    }

    public EffectResolver(Map<EffectType, EffectHandler> handlers) {
        this.catalog = null;
        this.relicEngine = null;
        this.powerPassiveEngine = new PowerPassiveEngine(null);
        this.handlers = Map.copyOf(Objects.requireNonNull(handlers, "handlers"));
    }

    public Resolution resolve(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String sourceId,
            List<String> targetIds,
            CardDefinition definition,
            List<EnchantmentInstance> enchantments
    ) {
        return resolve(combatId, revision, combatants, timeline, random, sourceId, targetIds,
                definition, enchantments, List.of());
    }

    public Resolution resolve(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String sourceId,
            List<String> targetIds,
            CardDefinition definition,
            List<EnchantmentInstance> enchantments,
            List<String> selectedCardInstanceIds
    ) {
        return resolve(combatId, revision, combatants, timeline, random, sourceId, targetIds,
                definition, enchantments, selectedCardInstanceIds, CombatCounters.initial(), null, 1);
    }

    /**
     * @param counters             what has happened so far, which the conditional effects read
     * @param sourceCardInstanceId the instance being played, or null when an enemy plays a move
     * @param availableEnergy      what the caster had before paying, which is what X stands for
     */
    public Resolution resolve(
            UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String sourceId,
            List<String> targetIds,
            CardDefinition definition,
            List<EnchantmentInstance> enchantments,
            List<String> selectedCardInstanceIds,
            CombatCounters counters,
            String sourceCardInstanceId,
            int availableEnergy
    ) {
        Objects.requireNonNull(combatId, "combatId");
        Objects.requireNonNull(combatants, "combatants");
        Objects.requireNonNull(random, "random");
        requireNonBlank(sourceId, "sourceId");
        Objects.requireNonNull(targetIds, "targetIds");
        Objects.requireNonNull(definition, "definition");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }

        List<CombatantState> resolvedCombatants = List.copyOf(combatants);
        // Le tour pendant lequel cette carte se résout est celui de la première entrée vivante
        // de la file, lue avant que la carte ne la touche : c'est « votre tour » pour Plein
        // régime, que la carte soit jouée de la main ou déclenchée par un statut.
        String turnOwnerId = firstLivingTimelineCombatant(timeline, resolvedCombatants);
        List<CombatEvent> events = new ArrayList<>();
        List<TimelineEntry> resolvedTimeline = timeline;
        RandomState resolvedRandom = random;
        // The tally advances as the card resolves, so a later effect can be gated on what an
        // earlier one just did: "deal damage if you gained energy this turn" includes this card.
        CombatCounters resolvedCounters = counters;
        List<EffectDefinition> allEffects = withReplays(withConsumptionFollowUps(
                Stream.concat(
                        definition.effects().stream(),
                        enchantmentEngine != null
                                ? enchantmentEngine.getBonusEffects(enchantments).stream()
                                : Stream.empty()
                ).toList(),
                selectedCardInstanceIds.size()));
        // A card's own modifiers, the ones its enchantments add and the ones the caster's relics
        // bring all change the same numbers, so they reach the calculator as one list.
        List<StatModifier> cardModifiers = modifiersFor(
                definition, enchantments, counters, sourceId, resolvedCombatants);
        // Solaire rend la moitié de ce qui est porté, et se prend sur le même chemin que le vol
        // de vie d'un enchantement : une seule façon de rendre des PV pour ce qu'on a infligé.
        // Les deux s'additionnent quand le porteur a les deux.
        int lifestealPercent = (enchantmentEngine != null
                ? enchantmentEngine.lifestealPercent(enchantments)
                : 0)
                + solarLifestealPercent(findCombatant(resolvedCombatants, sourceId));
        // Un effet EndTurn quelque part sur la carte ferme le tour une fois qu'elle a fini : le
        // reste de la carte se résout d'abord, comme dans Unity, où l'effet se contente
        // d'appeler PlayerEndTurn au moment où il est atteint.
        boolean endsTurn = false;
        boolean universeAnswered = false;
        // Suite arithmétique lit le sort de chaque condition rencontrée pendant la carte : une
        // seule échouée la remet à zéro, quel que soit ce que les autres ont tenu.
        boolean anyConditionHeld = false;
        boolean anyConditionFailed = false;

        // How many times the card resolves is asked with the card's targets in hand, because a
        // modifier may scale on them: "Frappe directrice" replays once per debuff on whoever it
        // is aimed at. Asked with no target, as it used to be, that modifier counted nothing and
        // the card never replayed at all.
        List<CombatantState> atPlay = resolvedCombatants;
        List<CombatantState> replayTargets = targetIds.stream()
                .map(targetId -> findCombatant(atPlay, targetId))
                .filter(Objects::nonNull)
                .toList();
        int replays = Math.max(0, definition.replaysFor(availableEnergy)) * Math.max(1,
                BattleCalculator.getModifiedValue(1, StatType.REPLAY_COUNT, new EffectContext(
                        null,
                        findCombatant(resolvedCombatants, sourceId),
                        replayTargets.isEmpty() ? null : replayTargets.getFirst(),
                        replayTargets,
                        definition, false, cardModifiers, timeline, resolvedCombatants)));
                resolvedCombatants = consumeEcho(
                    resolvedCombatants, sourceId, definition.type(),
                    definition.hasTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP));
        for (int replay = 0; replay < replays; replay++) {
        for (EffectDefinition effect : allEffects) {
            List<String> effectTargets =
                    targetsOf(effect, definition, sourceId, targetIds, resolvedCombatants);
            for (String targetId : effectTargets) {
                CombatantState target = requireCombatant(resolvedCombatants, targetId);
                boolean conditionHolds = !effect.isConditional() || ConditionEvaluator.holds(
                        effect.condition(),
                        resolvedCounters,
                        findCombatant(resolvedCombatants, sourceId),
                        target,
                        sourceCardInstanceId,
                        target.intentCardId(),
                        catalog,
                        timeline);
                if (effect.isConditional()) {
                    if (conditionHolds) {
                        anyConditionHeld = true;
                    } else {
                        anyConditionFailed = true;
                    }
                }
                if (effect.isConditional() && !conditionHolds) {
                    if (!universeAnswered) {
                        CombatantState source = findCombatant(resolvedCombatants, sourceId);
                        if (source != null && source.statuses().stream()
                                .anyMatch(status -> "Univers".equals(status.statusType()))) {
                            CombatantState armored = new CombatantState(source.combatantId(), source.teamId(),
                                    source.controllerType(), source.maxHp(), source.hp(), source.armor() + 3,
                                    source.energy(), source.baseEnergy(), source.turnDelayTicks(), source.piles(),
                                    source.statuses(), source.relics(), source.intentCardId(), source.patternIndex(),
                                    source.relicMemory());
                            resolvedCombatants = replaceCombatant(resolvedCombatants, sourceId, armored);
                            events.add(new fr.insastronaute.webapi.sts.combat.event.ArmorGained(
                                    combatId, revision, sourceId, sourceId, 3, 3, armored.armor()));
                            universeAnswered = true;
                        }
                    }
                    InfaillibleUse forced = consumeInfaillible(
                            resolvedCombatants, sourceId, combatId, revision);
                    if (!forced.used()) {
                        continue;
                    }
                    resolvedCombatants = forced.combatants();
                    events.add(forced.event());
                }
                EffectHandler handler = handlers.get(effect.type());
                if (handler == null) {
                    throw new UnsupportedOperationException("EffectType not implemented yet: " + effect.type());
                }
                                cardModifiers = modifiersFor(
                                                definition, enchantments, resolvedCounters, sourceId, resolvedCombatants);
                // Chaos double ce que son porteur envoie en face. Décidé ici plutôt que dans
                // chaque handler : c'est le seul endroit qui tient à la fois l'effet, sa cible et
                // son lanceur, et ce qui part doublé doit l'être pour tout le monde à la fois --
                // le statut posé, la file décalée, le soin rendu.
                EffectDefinition landing = starlit(
                        chaoticallyDoubled(effect, sourceId, target, resolvedCombatants),
                        sourceId,
                        resolvedCombatants);
                ResolutionStep step = handler instanceof CardSelectionHandler cardSelectionHandler
                        ? cardSelectionHandler.handleSelection(
                                combatId,
                                revision,
                                sourceId,
                                target,
                                landing,
                                resolvedRandom,
                                resolvedCombatants,
                                resolvedTimeline,
                                definition,
                                cardModifiers,
                                selectedCardInstanceIds)
                        : handler.handle(
                        combatId,
                        revision,
                        sourceId,
                        target,
                        landing,
                        resolvedRandom,
                        resolvedCombatants,
                        resolvedTimeline,
                        definition,
                        cardModifiers
                );
                for (CombatantState updated : step.combatants()) {
                    resolvedCombatants =
                            replaceCombatant(resolvedCombatants, updated.combatantId(), updated);
                }
                resolvedTimeline = step.timeline();
                resolvedRandom = step.random();
                // Ce que l'effet vient de faire, et ce que les statuts en disent aussitôt : la
                // Force de Plein régime et un Sadisme qui mûrit comptent dès l'effet suivant.
                List<CombatEvent> stepEvents = new ArrayList<>(step.events());
                resolvedCombatants = withFullThrottle(
                        combatId, revision, turnOwnerId, step.events(), resolvedCombatants,
                        stepEvents);
                resolvedCombatants = fr.insastronaute.webapi.sts.combat.engine.status.StatusEvolution
                        .evolve(combatId, revision, resolvedCombatants, stepEvents);
                events.addAll(stepEvents);
                PowerPassiveEngine.Result powerResult = powerPassiveEngine.onEvents(
                    combatId, revision, stepEvents, resolvedCombatants);
                resolvedCombatants = powerResult.combatants();
                events.addAll(powerResult.events());
                List<CombatEvent> generated = powerResult.events();
                // Passive reactions can legitimately feed another passive (for example an
                // EnergyGained response feeding Condensateur). Drain the resulting events too,
                // but cap the chain so a future authored cycle cannot hold a combat command.
                for (int depth = 0; depth < 8 && !generated.isEmpty(); depth++) {
                    PowerPassiveEngine.Result cascade = powerPassiveEngine.onEvents(
                            combatId, revision, generated, resolvedCombatants);
                    resolvedCombatants = cascade.combatants();
                    events.addAll(cascade.events());
                    generated = cascade.events();
                }
                // Le vol de vie se prend sur ce que l'effet vient de faire, donc après lui : ce
                // qu'il rend n'est connu qu'une fois les dégâts calculés.
                resolvedCombatants = withLifesteal(
                        combatId, revision, sourceId, lifestealPercent,
                        step.events(), resolvedCombatants, resolvedTimeline, resolvedRandom, events);
                resolvedCounters = resolvedCounters.observing(step.events());
                endsTurn = endsTurn || step.endsTurn();
                if (!step.pendingCardPlays().isEmpty()) {
                    Played played = playPending(
                            combatId, revision, step.pendingCardPlays(), resolvedCombatants,
                            resolvedTimeline, resolvedRandom, events, resolvedCounters);
                    resolvedCombatants = played.combatants();
                    resolvedTimeline = played.timeline();
                    resolvedRandom = played.random();
                }
            }
        }
        }
        resolvedCombatants = applySuiteArithmetique(
                resolvedCombatants, sourceId, anyConditionHeld, anyConditionFailed,
                combatId, revision, events);
        return new Resolution(resolvedCombatants, resolvedTimeline, events, resolvedRandom,
                resolvedCounters, endsTurn);
    }

    private static String firstLivingTimelineCombatant(
            List<TimelineEntry> timeline, List<CombatantState> combatants) {
        if (timeline == null) {
            return null;
        }
        java.util.Set<String> living = new java.util.HashSet<>();
        for (CombatantState combatant : combatants) {
            if (combatant.hp() > 0) {
                living.add(combatant.combatantId());
            }
        }
        return timeline.stream()
                .sorted(TimelineEntry.CANONICAL_ORDER)
                .map(TimelineEntry::combatantId)
                .filter(living::contains)
                .findFirst()
                .orElse(null);
    }

    /**
     * Plein régime : chaque perte de PV que subit le combattant dont c'est le tour lui rend la
     * valeur du statut en Force (FullThrottleStatus.OnDamageTaken, « quand vous perdez des PV
     * pendant votre tour »).
     *
     * <p>Il répondait autrefois depuis les réactions aux coups, qui ne parlent que d'un coup porté
     * par quelqu'un d'autre : il ne se déclenchait donc jamais pendant le tour de son porteur,
     * où les PV perdus viennent de ses propres cartes, et se déclenchait à tort pendant celui de
     * l'adversaire. Il lit maintenant ce que l'effet vient de coûter, quelle qu'en soit la source,
     * et une armure qui a tout absorbé ne coûte pas de PV.
     *
     * @param events appended to in place, one application per loss
     */
    private List<CombatantState> withFullThrottle(
            UUID combatId,
            long revision,
            String turnOwnerId,
            List<CombatEvent> stepEvents,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        if (turnOwnerId == null) {
            return combatants;
        }
        int losses = 0;
        for (CombatEvent event : stepEvents) {
            if (event instanceof fr.insastronaute.webapi.sts.combat.event.DamageApplied damage
                    && turnOwnerId.equals(damage.targetId()) && damage.hpLost() > 0) {
                losses++;
            } else if (event instanceof fr.insastronaute.webapi.sts.combat.event.HpLost loss
                    && turnOwnerId.equals(loss.targetId()) && loss.actualLoss() > 0) {
                losses++;
            }
        }
        if (losses == 0) {
            return combatants;
        }
        CombatantState owner = findCombatant(combatants, turnOwnerId);
        if (owner == null || owner.hp() <= 0) {
            return combatants;
        }
        int strength = owner.statuses().stream()
                .filter(status -> "FullThrottle".equals(status.statusType()))
                .mapToInt(StatusInstance::value)
                .sum();
        if (strength <= 0) {
            return combatants;
        }
        List<StatusInstance> statuses = owner.statuses();
        for (int loss = 0; loss < losses; loss++) {
            fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations.Application application =
                    fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations.apply(
                            statuses, new StatusInstance("Strength", strength, -1, "", 0),
                            statuses, catalog);
            statuses = application.statuses();
            if (application.applied() && application.result() != null) {
                events.add(new fr.insastronaute.webapi.sts.combat.event.StatusApplied(
                        combatId, revision, turnOwnerId, turnOwnerId, "Strength",
                        application.result().value(), application.result().duration(),
                        application.stacked()));
            }
        }
        return replaceCombatant(combatants, turnOwnerId, new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(), owner.maxHp(),
                owner.hp(), owner.armor(), owner.energy(), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), statuses, owner.relics(),
                owner.intentCardId(), owner.patternIndex(), owner.relicMemory()));
    }

    /**
     * Suite arithmétique monte de 10 à chaque condition tenue et retombe à zéro dès qu'une carte
     * en a manqué une, quel que soit ce que la carte a par ailleurs réussi.
     */
    private static List<CombatantState> applySuiteArithmetique(
            List<CombatantState> combatants,
            String sourceId,
            boolean anyConditionHeld,
            boolean anyConditionFailed,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (!anyConditionHeld && !anyConditionFailed) {
            return combatants;
        }
        CombatantState source = findCombatant(combatants, sourceId);
        if (source == null) {
            return combatants;
        }
        List<StatusInstance> statuses = new ArrayList<>(source.statuses());
        boolean changed = false;
        int cap = fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog
                .of("SuiteArithmetique").maxValue();
        for (int index = 0; index < statuses.size(); index++) {
            StatusInstance status = statuses.get(index);
            if (!"SuiteArithmetique".equals(status.statusType())) {
                continue;
            }
            int next = anyConditionFailed ? 0 : Math.min(cap, status.value() + 10);
            if (next == status.value()) {
                continue;
            }
            statuses.set(index, new StatusInstance(status.statusType(), next, status.duration(),
                    status.cardId(), status.index(), status.removable(), status.resistsEverything(),
                    status.progress()));
            changed = true;
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusApplied(
                    combatId, revision, sourceId, sourceId, status.statusType(), next,
                    status.duration(), true));
        }
        if (!changed) {
            return combatants;
        }
        return replaceCombatant(combatants, sourceId, new CombatantState(
                source.combatantId(), source.teamId(), source.controllerType(), source.maxHp(),
                source.hp(), source.armor(), source.energy(), source.baseEnergy(),
                source.turnDelayTicks(), source.piles(), statuses, source.relics(),
                source.intentCardId(), source.patternIndex(), source.relicMemory()));
    }

    private record Played(
            List<CombatantState> combatants, List<TimelineEntry> timeline, RandomState random) {
    }

    /**
     * Joue les cartes qu'un effet ou une relique a demandé à voir jouer.
     *
     * <p>La carte se résout comme n'importe quelle autre et annonce un {@code CardPlayed}, faute
     * de quoi le client n'aurait rien à animer — c'est précisément ce qui manquait à PERF. Elle
     * est jouée sans rien payer : ce n'est pas le joueur qui la joue.
     *
     * <p>Le compteur de profondeur est celui des rejeux : une carte qui en fait jouer une autre
     * ne peut pas descendre indéfiniment.
     */
    private Played playPending(
            UUID combatId,
            long revision,
            List<ResolutionStep.PendingCardPlay> plays,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<CombatEvent> events,
            CombatCounters counters
    ) {
        List<CombatantState> current = combatants;
        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        int ordinal = 0;
        // Une carte jouée par ce chemin peut en demander une autre. La garde est portée par le
        // résolveur plutôt que passée de main en main, parce que les handlers qui posent ces
        // demandes ne se connaissent pas entre eux et ne sauraient pas quoi transmettre.
        if (pendingPlayDepth >= MAX_PENDING_PLAY_DEPTH || catalog == null) {
            return new Played(current, currentTimeline, currentRandom);
        }
        for (ResolutionStep.PendingCardPlay play : plays) {
            CardDefinition definition = catalog.find(play.definitionId());
            CombatantState caster = findCombatant(current, play.casterId());
            if (definition == null || caster == null || caster.hp() <= 0) {
                continue;
            }
            List<String> targetIds = pendingPlayTargets(current, caster, definition, play);
            if (targetIds.isEmpty() && definition.targetingMode() != TargetingMode.NONE) {
                continue;
            }
            // L'exemplaire réel quand il y en a un : un identifiant fabriqué ne désigne aucune
            // carte côté client, qui devait alors en reconstruire une depuis la définition seule.
            String playedInstanceId = play.cardInstanceId() != null
                    ? play.cardInstanceId()
                    : GeneratedCardId.fromContext(
                            "pending-play", revision, caster.combatantId(), ordinal++);
            events.add(new fr.insastronaute.webapi.sts.combat.event.CardPlayed(
                    combatId, revision, caster.combatantId(),
                    playedInstanceId,
                    definition.id(), targetIds));
            Resolution resolution;
            pendingPlayDepth++;
            try {
                resolution = resolve(
                        combatId, revision, current, currentTimeline, currentRandom,
                        caster.combatantId(), targetIds, definition, List.of(), List.of(),
                        counters, null, 0);
            } finally {
                pendingPlayDepth--;
            }
            current = resolution.combatants();
            currentTimeline = resolution.timeline();
            currentRandom = resolution.random();
            events.addAll(resolution.events());
        }
        return new Played(current, currentTimeline, currentRandom);
    }

    /** Qui la carte demandée vise : la cible nommée si elle tient encore debout, sinon le premier adversaire vivant. */
    private static List<String> pendingPlayTargets(
            List<CombatantState> combatants,
            CombatantState caster,
            CardDefinition definition,
            ResolutionStep.PendingCardPlay play
    ) {
        if (definition.targetingMode() == TargetingMode.NONE) {
            return List.of();
        }
        if (definition.targetingMode() == TargetingMode.SELF) {
            return List.of(caster.combatantId());
        }
        if (play.targetId() != null) {
            CombatantState named = findCombatant(combatants, play.targetId());
            if (named != null && named.hp() > 0) {
                return List.of(named.combatantId());
            }
        }
        List<String> opponents = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .filter(combatant -> caster.teamId() == null
                        || !caster.teamId().equals(combatant.teamId()))
                .map(CombatantState::combatantId)
                .toList();
        if (opponents.isEmpty()) {
            return List.of();
        }
        return definition.targetingMode() == TargetingMode.ALL_ENEMIES
                ? opponents
                : List.of(opponents.getFirst());
    }

    /**
     * Rend au lanceur une part des dégâts que l'effet vient d'infliger.
     *
     * <p>Unity le fait depuis le cas Damage lui-même, sur la valeur modifiée et avant l'armure
     * (EffectResolver.cs:159-163) : c'est ce que porte {@code requestedDamage}. Un Multihit
     * frappe plusieurs fois en une étape, et les coups s'additionnent avant d'être rendus.
     *
     * <p>Le soin passe par le handler de soin plutôt que par un calcul à part, pour que les
     * reliques qui interceptent un soin et l'inversion de Mort-vivant s'appliquent ici aussi.
     */
    private List<CombatantState> withLifesteal(
            UUID combatId,
            long revision,
            String sourceId,
            int percent,
            List<CombatEvent> stepEvents,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<CombatEvent> events
    ) {
        if (percent <= 0) {
            return combatants;
        }
        int dealt = stepEvents.stream()
                .filter(fr.insastronaute.webapi.sts.combat.event.DamageApplied.class::isInstance)
                .map(fr.insastronaute.webapi.sts.combat.event.DamageApplied.class::cast)
                .filter(damage -> damage.sourceId().equals(sourceId))
                .mapToInt(fr.insastronaute.webapi.sts.combat.event.DamageApplied::requestedDamage)
                .sum();
        int heal = dealt * percent / 100;
        if (heal <= 0) {
            return combatants;
        }
        CombatantState caster = findCombatant(combatants, sourceId);
        EffectHandler healHandler = handlers.get(EffectType.HEAL);
        if (caster == null || healHandler == null) {
            return combatants;
        }
        ResolutionStep step = healHandler.handle(
                combatId,
                revision,
                sourceId,
                caster,
                new EffectDefinition(EffectType.HEAL, heal, true, null, 0, null, null),
                random,
                combatants,
                timeline,
                null,
                List.of());
        events.addAll(step.events());
        List<CombatantState> healed = combatants;
        for (CombatantState updated : step.combatants()) {
            healed = replaceCombatant(healed, updated.combatantId(), updated);
        }
        return healed;
    }

    /**
     * How far a chain of replays may be expanded before the card is treated as authored wrong.
     *
     * <p>Each ReplayCard multiplies everything in front of it, so a handful of them in one card
     * multiply together: three of them replaying twice each is already eight times the card. The
     * ceiling is not a game rule, it is what keeps a mis-authored card from resolving for
     * thousands of effects and taking the combat down with it.
     */
    private static final int MAX_RESOLVED_EFFECTS = 256;

    /**
     * Expands every {@code ReplayCard} into the effects standing in front of it.
     *
     * <p>The card does its earlier effects again -- {@code value} more times -- so a strike
     * followed by a ReplayCard of 1 strikes twice. Doing it here, once, rather than recursively
     * while resolving, means everything downstream sees a plain list of effects and nothing has
     * to know that replaying exists.
     *
     * <p>Reading left to right is also what makes a second ReplayCard behave: by the time it is
     * reached, the first one has already been expanded into the prefix it will copy, so the
     * chain compounds the way the card reads and cannot recurse into itself.
     */
    /** Le statut qui double ce que son porteur envoie de l'autre côté de la ligne. */
    private static final String CHAOTIC = "Chaotic";

    /** Le statut qui fait repartir la carte quatre fois de plus, pour un point à chaque fois. */
    private static final String ASTRA = "Astra";

    /** Le statut qui rend en soin la moitié de ce que son porteur inflige. */
    private static final String SOL = "Sol";

    /** La part des dégâts que Solaire rend, en pourcent. */
    private static final int SOL_LIFESTEAL_PERCENT = 50;

    /** @return ce que Solaire ajoute au vol de vie, ou zéro quand le lanceur ne la porte pas */
    private static int solarLifestealPercent(CombatantState source) {
        return source != null
                && source.statuses().stream().anyMatch(s -> SOL.equals(s.statusType()))
                ? SOL_LIFESTEAL_PERCENT
                : 0;
    }

    /**
     * The effect as Starlight sends it: whatever it was worth, it is worth one.
     *
     * <p>Le prix des quatre redites que Stellaire ajoute (StatusModifier). Une carte de 12 dégâts
     * jouée cinq fois pour 1 en fait 5 : ce que le statut donne en nombre de fois, il le reprend
     * sur la taille, et il porte aussi bien sur ce qu'on s'applique à soi que sur le reste.
     *
     * <p>Les deux nombres y passent, et non la seule valeur : côté Unity, l'enchantement du même
     * nom pose un {@code OneModifier(StatType.Any)}, et un modificateur générique atteint là-bas
     * la potence <em>et</em> la durée d'un statut ({@code StatTypeChecker}). Ne ramener que la
     * valeur laissait une Vulnérabilité durer ses trois tours pleins.
     *
     * <p>Ramené comme {@code OneModifier} le fait, en pinçant entre -1 et 1 plutôt qu'en écrivant
     * 1 : le signe est ce qui dit de quel côté va l'effet, et une durée de -1 est l'écriture du
     * permanent et non un compte. Un malus de -3 devient -1, un permanent reste permanent.
     *
     * <p>Appliqué en dernier, après le doublement du Chaos : ramené à 1 veut dire 1, et non 2.
     */
    private static EffectDefinition starlit(
            EffectDefinition effect, String sourceId, List<CombatantState> combatants) {
        CombatantState source = findCombatant(combatants, sourceId);
        if (source == null
                || source.statuses().stream().noneMatch(s -> ASTRA.equals(s.statusType()))) {
            return effect;
        }
        return effect.withValueAndDuration(
                toOne(effect.value()), toOne(effect.duration()));
    }

    /** {@code OneModifier.Modify} : pincé entre -1 et 1, le signe conservé. */
    private static int toOne(int value) {
        return Math.max(-1, Math.min(1, value));
    }

    /**
     * Les dégâts et l'armure, que Chaos ne grossit pas.
     *
     * <p>Les dégâts parce qu'il les supprime déjà, et l'armure parce que Chaos ne parle que de ce
     * qui part vers l'autre bord : une armure y arrive quand une carte en donne à l'adversaire,
     * ce qui n'est pas une chose à récompenser.
     */
    private static final java.util.Set<EffectType> CHAOS_LEAVES_ALONE =
            java.util.Set.of(EffectType.DAMAGE, EffectType.MULTIHIT, EffectType.ARMOR);

    /**
     * The effect as Chaos sends it: twice the number, when it is aimed at the other side.
     *
     * <p>Chaos trades away the blow for everything else. What its bearer aims at an adversary
     * lands twice as hard and twice as long -- an affliction, a turn pushed back, a life drained --
     * while damage and armour are left as authored.
     *
     * <p>Both numbers are read the way the effect reads them, so nothing invents a figure: a
     * duration that is not a count (zero, or the -1 that spells "permanent") is not doubled, since
     * twice a permanent is still a permanent, and a status that does not read its duration will
     * drop the doubled one anyway when it is instantiated.
     */
    private static EffectDefinition chaoticallyDoubled(
            EffectDefinition effect,
            String sourceId,
            CombatantState target,
            List<CombatantState> combatants
    ) {
        if (CHAOS_LEAVES_ALONE.contains(effect.type())) {
            return effect;
        }
        CombatantState source = findCombatant(combatants, sourceId);
        if (source == null || target == null || source.teamId() == null) {
            return effect;
        }
        // De l'autre côté de la ligne, et pas sur soi : un effet qu'une carte se réserve garde sa
        // taille, même joué par un porteur de Chaos.
        if (source.teamId().equals(target.teamId())) {
            return effect;
        }
        if (source.statuses().stream().noneMatch(status -> CHAOTIC.equals(status.statusType()))) {
            return effect;
        }
        int value = Math.multiplyExact(effect.value(), 2);
        int duration = effect.duration() > 0
                ? Math.multiplyExact(effect.duration(), 2)
                : effect.duration();
        return effect.withValueAndDuration(value, duration);
    }

    private static List<EffectDefinition> withReplays(List<EffectDefinition> effects) {
        if (effects.stream().noneMatch(effect -> effect.type() == EffectType.REPLAY_CARD)) {
            return effects;
        }
        List<EffectDefinition> resolved = new ArrayList<>();
        for (EffectDefinition effect : effects) {
            if (effect.type() != EffectType.REPLAY_CARD) {
                resolved.add(effect);
                continue;
            }
            // Nothing in front of it: a card that opens with a replay has nothing to replay.
            if (resolved.isEmpty()) {
                continue;
            }
            List<EffectDefinition> preceding = List.copyOf(resolved);
            int times = Math.max(1, effect.value());
            for (int repeat = 0; repeat < times; repeat++) {
                if (resolved.size() + preceding.size() > MAX_RESOLVED_EFFECTS) {
                    break;
                }
                resolved.addAll(preceding);
            }
        }
        return List.copyOf(resolved);
    }

    private List<StatModifier> modifiersFor(
            CardDefinition definition,
            List<EnchantmentInstance> enchantments,
            CombatCounters counters,
            String sourceId,
            List<CombatantState> combatants
    ) {
        return Stream.concat(
                Stream.concat(
                        CardModifierFactory.create(
                                definition.modifiers(), counters, sourceId, catalog).stream(),
                        enchantmentEngine != null
                                ? enchantmentEngine.getModifiers(enchantments).stream()
                                : Stream.<StatModifier>empty()),
                relicEngine != null
                        ? relicEngine.statModifiers(findCombatant(combatants, sourceId), counters).stream()
                        : Stream.<StatModifier>empty()
        ).toList();
    }

    /**
     * A selection that consumes its cards pays out through an ordinary armor or damage effect.
     *
     * <p>Expressing the payout as a normal effect rather than doing it inside the selection keeps
     * relics, statuses and events on one path: consuming three cards for armor is armor, and is
     * modified like any other armor.
     */
    private static List<EffectDefinition> withConsumptionFollowUps(
            List<EffectDefinition> effects,
            int selectedCardCount
    ) {
        if (selectedCardCount == 0
                || effects.stream().noneMatch(EffectResolver::consumesItsSelection)) {
            return effects;
        }
        List<EffectDefinition> expanded = new ArrayList<>(effects.size() + 1);
        for (EffectDefinition effect : effects) {
            expanded.add(effect);
            if (!consumesItsSelection(effect)) {
                continue;
            }
            int total = Math.multiplyExact(effect.duration(), selectedCardCount);
            if (total <= 0) {
                continue;
            }
            expanded.add(effect.cardSelectionAction() == CardSelectionAction.CONSUME_AND_GAIN_ARMOR
                    ? new EffectDefinition(EffectType.ARMOR, total, true)
                    : new EffectDefinition(EffectType.DAMAGE, total, false));
        }
        return List.copyOf(expanded);
    }

    private static boolean consumesItsSelection(EffectDefinition effect) {
        return effect.type() == EffectType.CARD_SELECTION
                && (effect.cardSelectionAction() == CardSelectionAction.CONSUME_AND_GAIN_ARMOR
                        || effect.cardSelectionAction() == CardSelectionAction.CONSUME_AND_DEAL_DAMAGE_TO_ALL);
    }

    /**
     * Effects whose {@code targetSelf} turns them around rather than aiming them at the caster.
     *
     * <p>Each of them already involves the caster and a target by nature, so aiming one at its
     * own caster would leave it nothing to do. Checked, the flag reverses what they do between
     * the two: StealBuff steals a debuff from the target, TransferDebuff gives the target a buff,
     * and CutInTurn places the caster's turn after the target's instead of the other way round.
     */
    private static final java.util.Set<EffectType> SELF_FLAG_FLIPS_MEANING = java.util.EnumSet.of(
            EffectType.STEAL_BUFF, EffectType.TRANSFER_DEBUFF, EffectType.CUT_IN_TURN);

    /**
     * Who one effect of a card actually hits.
     *
     * <p>A self-targeted effect always resolves on its caster, whatever the card is aimed at, and
     * an effect marked as targeting the others reaches every living opponent the card did not
     * pick, which is how a card splashes past its own target.
     */
    private static List<String> targetsOf(
            EffectDefinition effect,
            CardDefinition definition,
            String sourceId,
            List<String> targetIds,
            List<CombatantState> combatants
    ) {
        if (effect.type() == EffectType.CARD_SELECTION
                || (effect.targetSelf() && !SELF_FLAG_FLIPS_MEANING.contains(effect.type()))
                || definition.targetingMode() == TargetingMode.SELF
                || definition.targetingMode() == TargetingMode.NONE) {
            return List.of(sourceId);
        }
        if (!effect.targetOthers()) {
            return targetIds;
        }
        CombatantState source = findCombatant(combatants, sourceId);
        return combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .filter(combatant -> source == null || !combatant.teamId().equals(source.teamId()))
                .map(CombatantState::combatantId)
                .filter(combatantId -> !targetIds.contains(combatantId))
                .toList();
    }

    private static CombatantState findCombatant(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElse(null);
    }

    private static List<CombatantState> consumeEcho(
            List<CombatantState> combatants,
            String sourceId,
            fr.insastronaute.webapi.sts.combat.catalog.CardType playedType,
            boolean playedCardIsFollowUp
    ) {
        if (playedCardIsFollowUp) {
            return combatants;
        }
        CombatantState source = findCombatant(combatants, sourceId);
        if (source == null) {
            return combatants;
        }
        List<StatusInstance> statuses = source.statuses().stream()
                .flatMap(status -> {
                    if (!"Echo".equals(status.statusType()) || !echoWatches(status.index(), playedType)) {
                        return Stream.of(status);
                    }
                    int duration = status.duration() > 0 ? status.duration() - 1 : status.duration();
                    return duration == 0 ? Stream.empty() : Stream.of(new StatusInstance(
                            status.statusType(), status.value(), duration, status.cardId(), status.index(),
                            status.removable(), status.resistsEverything(), status.progress()));
                })
                .toList();
        if (statuses.equals(source.statuses())) {
            return combatants;
        }
        CombatantState updated = new CombatantState(
                source.combatantId(), source.teamId(), source.controllerType(), source.maxHp(), source.hp(),
                source.armor(), source.energy(), source.baseEnergy(), source.turnDelayTicks(), source.piles(),
                statuses, source.relics(), source.intentCardId(), source.patternIndex(), source.relicMemory());
        return replaceCombatant(combatants, sourceId, updated);
    }

    private static boolean echoWatches(
            int index, fr.insastronaute.webapi.sts.combat.catalog.CardType playedType) {
        return switch (index) {
            case 1 -> playedType == fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK;
            case 2 -> playedType == fr.insastronaute.webapi.sts.combat.catalog.CardType.SKILL;
            case 3 -> playedType == fr.insastronaute.webapi.sts.combat.catalog.CardType.POWER;
            default -> true;
        };
    }

    private static List<CombatantState> replaceCombatant(
            List<CombatantState> combatants,
            String combatantId,
            CombatantState replacement
    ) {
        return combatants.stream()
                .map(combatant -> combatant.combatantId().equals(combatantId)
                        ? replacement
                        : combatant)
                .toList();
    }

    /** A failed condition may be guaranteed by one Infaillible charge, never by a true condition. */
    private record InfaillibleUse(
            List<CombatantState> combatants,
            fr.insastronaute.webapi.sts.combat.event.StatusRemoved event) {
        boolean used() {
            return event != null;
        }
    }

    private static InfaillibleUse consumeInfaillible(
            List<CombatantState> combatants,
            String sourceId,
            UUID combatId,
            long revision
    ) {
        CombatantState source = findCombatant(combatants, sourceId);
        if (source == null) {
            return new InfaillibleUse(combatants, null);
        }
        List<StatusInstance> updated = new ArrayList<>();
        boolean consumed = false;
        int remaining = 0;
        for (StatusInstance status : source.statuses()) {
            if (!consumed && "Infaillible".equals(status.statusType()) && status.value() > 0) {
                consumed = true;
                remaining = status.value() - 1;
                if (remaining > 0) {
                    updated.add(new StatusInstance(status.statusType(), remaining, status.duration(),
                            status.cardId(), status.index(), status.removable(),
                            status.resistsEverything(), status.progress()));
                }
                continue;
            }
            updated.add(status);
        }
        if (!consumed) {
            return new InfaillibleUse(combatants, null);
        }
        CombatantState replaced = new CombatantState(
                source.combatantId(), source.teamId(), source.controllerType(), source.maxHp(),
                source.hp(), source.armor(), source.energy(), source.baseEnergy(),
                source.turnDelayTicks(), source.piles(), updated, source.relics(), source.intentCardId(),
                source.patternIndex(), source.relicMemory());
        return new InfaillibleUse(replaceCombatant(combatants, sourceId, replaced),
                new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                        combatId, revision, sourceId, "Infaillible", 1, remaining, -1, "expiry"));
    }

    private static CombatantState requireCombatant(
            List<CombatantState> combatants,
            String combatantId
    ) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "validated target is absent from combatants"));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record Resolution(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            List<CombatEvent> events,
            RandomState random,
            CombatCounters counters,
            boolean endsTurn
    ) {

        /** The shape every caller that cannot end a turn still uses. */
        public Resolution(
                List<CombatantState> combatants,
                List<TimelineEntry> timeline,
                List<CombatEvent> events,
                RandomState random,
                CombatCounters counters
        ) {
            this(combatants, timeline, events, random, counters, false);
        }

        public Resolution {
            counters = counters == null ? CombatCounters.initial() : counters;
            combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
            timeline = List.copyOf(Objects.requireNonNull(timeline, "timeline"));
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            Objects.requireNonNull(random, "random");
        }
    }
}
