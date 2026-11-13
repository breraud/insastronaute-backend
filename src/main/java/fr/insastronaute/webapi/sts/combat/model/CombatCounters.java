package fr.insastronaute.webapi.sts.combat.model;

import fr.insastronaute.webapi.sts.combat.event.ArmorBroken;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.HpLost;
import fr.insastronaute.webapi.sts.combat.event.StatusRemoved;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What has happened so far in this combat, for the cards that scale on it.
 *
 * <p>A card reading "one more damage per energy spent this turn" needs the combat to remember
 * that, and so do the conditions that gate an effect on a killing blow or on a card being played
 * for the first time this turn. The counters are part of the state rather than derived after the
 * fact, so a reloaded combat scales exactly like the one that was saved.
 *
 * <p>They advance by reading the events a resolution produced, which keeps a single account of
 * what happened instead of asking every handler to also report a number.
 *
 * <p>Three scopes live here and are cleared at different moments: what lasts the whole combat,
 * what lasts a turn, and what describes only the action being resolved.
 */
public record CombatCounters(
        int turnCount,
        List<String> cardsPlayedThisTurn,
        List<String> definitionsPlayedThisTurn,
        List<String> cardsPlayedThisCombat,
        int cardsDiscardedThisCombat,
        int cardsExhaustedThisAction,
        int effectsDispelledThisAction,
        int energySpentThisTurn,
        int energyGainedThisTurn,
        int damageDealtThisAction,
        boolean killingBlow,
        boolean armorBroken,
        Map<String, Integer> hpLostSinceLastTurn,
        int attacksPlayedThisTurn,
        int attacksPlayedThisCombat,
        fr.insastronaute.webapi.sts.combat.catalog.CardType lastCardTypePlayed
) {

    private static final String EXHAUST_PILE = "EXHAUST";
    private static final String DISCARD_PILE = "DISCARD";

    public CombatCounters {
        cardsPlayedThisTurn = cardsPlayedThisTurn == null ? List.of() : List.copyOf(cardsPlayedThisTurn);
        definitionsPlayedThisTurn = definitionsPlayedThisTurn == null
                ? List.of() : List.copyOf(definitionsPlayedThisTurn);
        cardsPlayedThisCombat = cardsPlayedThisCombat == null ? List.of() : List.copyOf(cardsPlayedThisCombat);
        hpLostSinceLastTurn = hpLostSinceLastTurn == null ? Map.of() : Map.copyOf(hpLostSinceLastTurn);
        // A combat saved before these counters existed reloads without them, so nothing may
        // require them to be present.
        lastCardTypePlayed = lastCardTypePlayed == null
                ? fr.insastronaute.webapi.sts.combat.catalog.CardType.NONE
                : lastCardTypePlayed;
    }

    /**
     * The three-argument form, kept for a caller with no card family to declare.
     */
    public CombatCounters(
            int turnCount,
            List<String> cardsPlayedThisTurn,
            List<String> definitionsPlayedThisTurn,
            List<String> cardsPlayedThisCombat,
            int cardsDiscardedThisCombat,
            int cardsExhaustedThisAction,
            int effectsDispelledThisAction,
            int energySpentThisTurn,
            int energyGainedThisTurn,
            int damageDealtThisAction,
            boolean killingBlow,
            boolean armorBroken,
            Map<String, Integer> hpLostSinceLastTurn
    ) {
        this(turnCount, cardsPlayedThisTurn, definitionsPlayedThisTurn, cardsPlayedThisCombat,
                cardsDiscardedThisCombat, cardsExhaustedThisAction, effectsDispelledThisAction,
                energySpentThisTurn, energyGainedThisTurn, damageDealtThisAction, killingBlow,
                armorBroken, hpLostSinceLastTurn, 0, 0,
                fr.insastronaute.webapi.sts.combat.catalog.CardType.NONE);
    }

    public static CombatCounters initial() {
        return new CombatCounters(
                0, List.of(), List.of(), List.of(), 0, 0, 0, 0, 0, 0, false, false, Map.of());
    }

    public int hpLostSinceLastTurn(String combatantId) {
        return hpLostSinceLastTurn.getOrDefault(combatantId, 0);
    }

    public boolean playedThisTurn(String cardInstanceId) {
        return cardsPlayedThisTurn.contains(cardInstanceId);
    }

    public boolean playedThisCombat(String cardInstanceId) {
        return cardsPlayedThisCombat.contains(cardInstanceId);
    }

    /** Advances the counters by everything one resolution reported. */
    public CombatCounters observing(List<CombatEvent> events) {
        int exhausted = cardsExhaustedThisAction;
        int discarded = cardsDiscardedThisCombat;
        int dispelled = effectsDispelledThisAction;
        int spent = energySpentThisTurn;
        int gained = energyGainedThisTurn;
        int damage = damageDealtThisAction;
        boolean killed = killingBlow;
        boolean broken = armorBroken;
        Map<String, Integer> hpLost = new LinkedHashMap<>(hpLostSinceLastTurn);

        for (CombatEvent event : events) {
            switch (event) {
                case CardMoved moved when EXHAUST_PILE.equals(moved.toPile()) -> exhausted++;
                case CardMoved moved when DISCARD_PILE.equals(moved.toPile()) -> discarded++;
                // Unity only counts DispelBuff, DispelDebuff and DispelSpecificStatus toward this
                // tally (EffectResolver.cs effectsDispelled++), never a steal, a transfer, a
                // natural expiry, or the two effects that convert a status into damage or into
                // another status instead of plainly dispelling it.
                case StatusRemoved removed when "dispel".equals(removed.reason()) -> dispelled++;
                case EnergySpent energy -> spent += energy.amount();
                case EnergyGained energy -> gained += energy.amount();
                case ArmorBroken armor -> broken = true;
                case DamageApplied applied -> {
                    damage += applied.requestedDamage();
                    hpLost.merge(applied.targetId(), applied.hpLost(), Integer::sum);
                    killed = killed || applied.remainingHp() == 0;
                }
                case HpLost lost -> hpLost.merge(lost.targetId(), lost.actualLoss(), Integer::sum);
                default -> {
                }
            }
        }
        return new CombatCounters(turnCount, cardsPlayedThisTurn, definitionsPlayedThisTurn,
                cardsPlayedThisCombat, discarded, exhausted, dispelled, spent, gained, damage,
                killed, broken, hpLost,
                attacksPlayedThisTurn, attacksPlayedThisCombat, lastCardTypePlayed);
    }

    /** Remembers that a card was played, once its effects have resolved. */
    public CombatCounters afterPlaying(String cardInstanceId, String definitionId) {
        return afterPlaying(cardInstanceId, definitionId,
                fr.insastronaute.webapi.sts.combat.catalog.CardType.NONE);
    }

    /**
     * Records a card being played, including what family it belonged to.
     *
     * <p>The family is here rather than in a relic because several relics read it and because a
     * handler is a singleton: what a combat remembers has to survive being saved and reloaded.
     */
    public CombatCounters afterPlaying(
            String cardInstanceId,
            String definitionId,
            fr.insastronaute.webapi.sts.combat.catalog.CardType type
    ) {
        List<String> thisTurn = new ArrayList<>(cardsPlayedThisTurn);
        List<String> definitions = new ArrayList<>(definitionsPlayedThisTurn);
        List<String> thisCombat = new ArrayList<>(cardsPlayedThisCombat);
        thisTurn.add(cardInstanceId);
        definitions.add(definitionId);
        thisCombat.add(cardInstanceId);
        boolean attack = type == fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK;
        return new CombatCounters(turnCount, thisTurn, definitions, thisCombat,
                cardsDiscardedThisCombat, cardsExhaustedThisAction, effectsDispelledThisAction,
                energySpentThisTurn, energyGainedThisTurn, damageDealtThisAction, killingBlow,
                armorBroken, hpLostSinceLastTurn,
                attack ? attacksPlayedThisTurn + 1 : attacksPlayedThisTurn,
                attack ? attacksPlayedThisCombat + 1 : attacksPlayedThisCombat,
                type);
    }

    /** Clears what described only the action that just finished. */
    public CombatCounters afterAction() {
        return new CombatCounters(turnCount, cardsPlayedThisTurn, definitionsPlayedThisTurn,
                cardsPlayedThisCombat, cardsDiscardedThisCombat, 0, 0, energySpentThisTurn,
                energyGainedThisTurn, 0, false, false, hpLostSinceLastTurn,
                attacksPlayedThisTurn, attacksPlayedThisCombat, lastCardTypePlayed);
    }

    /** Clears what described only the turn that is starting. */
    public CombatCounters atTurnStart() {
        return new CombatCounters(turnCount + 1, List.of(), List.of(), cardsPlayedThisCombat,
                cardsDiscardedThisCombat, cardsExhaustedThisAction, effectsDispelledThisAction,
                0, 0, damageDealtThisAction, killingBlow, armorBroken, hpLostSinceLastTurn,
                0, attacksPlayedThisCombat, lastCardTypePlayed);
    }

    /** Starts a fresh tally of what this combatant loses before its next turn. */
    public CombatCounters atTurnEnd(String combatantId) {
        Map<String, Integer> hpLost = new LinkedHashMap<>(hpLostSinceLastTurn);
        hpLost.put(combatantId, 0);
        return new CombatCounters(turnCount, cardsPlayedThisTurn, definitionsPlayedThisTurn,
                cardsPlayedThisCombat, cardsDiscardedThisCombat, cardsExhaustedThisAction,
                effectsDispelledThisAction, energySpentThisTurn, energyGainedThisTurn,
                damageDealtThisAction, killingBlow, armorBroken, hpLost,
                attacksPlayedThisTurn, attacksPlayedThisCombat, lastCardTypePlayed);
    }
}
