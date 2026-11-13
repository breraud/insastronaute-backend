package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Builds combats that finish on their own, from a seed.
 *
 * <p>Combats that end are the point, but combats that end <em>at once</em> are worthless. Hit
 * points used to be drawn from the burn's own range, so a burn was always lethal and every
 * combatant died closing its first turn: a whole combat was one command, or two; the enemies won
 * every single seed; and the two card handlers the transition reform rewrote were never entered
 * once in two thousand of them. The property test above this had a shape but nothing under it.
 *
 * <p>Hit points are drawn on their own now, and the damage that ends a combat is a permanent Trap:
 * one point as its holder's turn closes, every turn, and it never runs out. That is what makes a
 * combat end without anyone having to play a card, and it does it slowly, so the combats run for as
 * many rounds as the health they were dealt. A Poison joins it for the deaths that fall as a turn
 * opens and a Burn for the ones that fall as it closes, both of them spending themselves over a few
 * turns — enough to end some combats quickly and leave others to the Trap. (It used to be a Sap,
 * until Sap began biting on cards played rather than on the clock.)
 *
 * <p>Every combatant carries a deck, the enemies are driven as enemies are, and the seeds that
 * play cards go through both card handlers.
 */
final class CombatScenarios {

    /** The one card in the catalog: three damage to a random enemy, for one energy. */
    static final String STRIKE = "strike";

    private static final String PLAYER_TEAM = "player";
    private static final String ENEMY_TEAM = "enemy";

    /** A hand of five, several times over: long combats reshuffle, which is worth walking too. */
    private static final int DECK_SIZE = 12;

    private static final int MIN_HP = 8;
    private static final int HP_SPREAD = 13;

    /**
     * The Trap bites for one every turn and never expires, so a combatant of {@code h} health is dead
     * within {@code h} of its own turns whatever else happens. That is the whole of why these
     * combats end, and it is what lets the health be drawn freely: no status range has to be
     * sized against it. A permanent status is written with a negative duration.
     */
    private static final int PERMANENT = -1;

    private CombatScenarios() {
    }

    static CombatEngine engine() {
        return new CombatEngine(catalog(), new RelicEngine(List.of()));
    }

    static CardCatalog catalog() {
        return new CardCatalog("v1", Map.of(
                STRIKE,
                new CardDefinition(
                        STRIKE,
                        1,
                        CardType.ATTACK,
                        // The engine picks the target itself, so a caller has none to get wrong
                        // and the random stream is exercised by the choice.
                        TargetingMode.RANDOM_ENEMY,
                        Set.of(),
                        List.of(new EffectDefinition(EffectType.DAMAGE, 3)))));
    }

    static CombatState randomCombat(int seed) {
        Random random = new Random(seed);
        int perTeam = 1 + random.nextInt(2);

        List<CombatantState> combatants = new ArrayList<>();
        List<TimelineEntry> timeline = new ArrayList<>();
        int sequence = 0;
        for (String team : List.of(PLAYER_TEAM, ENEMY_TEAM)) {
            for (int i = 0; i < perTeam; i++) {
                String id = team + "-" + i;
                int hp = MIN_HP + random.nextInt(HP_SPREAD);

                List<StatusInstance> statuses = new ArrayList<>();
                // The Trap is the floor: one point as the turn closes, for as long as the combat
                // runs, so every combat ends and none of them ends at once.
                statuses.add(new StatusInstance("Trap", 1, PERMANENT, null, 0));
                // Poison strikes as the turn opens, for what is left of it, and counts down.
                statuses.add(new StatusInstance("Poison", 0, 1 + random.nextInt(4), null, 1));
                // Burn strikes as the turn closes, so deaths land on both halves of a turn.
                statuses.add(new StatusInstance("Burn", 0, 1 + random.nextInt(4), null, 2));
                if (random.nextInt(4) == 0) {
                    // Exercises the loop that skips turns, where a stunned combatant can
                    // die of its own statuses on the turn it was too stunned to take.
                    statuses.add(new StatusInstance("Stun", 0, 1 + random.nextInt(2), null, 3));
                }
                // Delays differ on purpose: a short one puts a combatant that has just
                // died back at the head of the timeline.
                int delay = 5 + random.nextInt(40);
                combatants.add(new CombatantState(
                        id,
                        team,
                        // The enemies are driven the way enemies are driven, so the seeds that
                        // act go through the authored-move handler as well as the card one.
                        ENEMY_TEAM.equals(team) ? ControllerType.AI : ControllerType.HUMAN,
                        hp, hp, 0, 0, 3, delay,
                        new CardPiles(deck(id), List.of(), List.of(), List.of()),
                        statuses, List.of(), null, 0, List.of()));
                timeline.add(new TimelineEntry(id, sequence * 10L, sequence));
                sequence++;
            }
        }

        return new CombatState(
                new UUID(0L, seed),
                1,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(seed),
                combatants,
                timeline,
                timeline.get(0).combatantId());
    }

    private static List<CardInstance> deck(String combatantId) {
        List<CardInstance> cards = new ArrayList<>();
        for (int card = 0; card < DECK_SIZE; card++) {
            cards.add(new CardInstance(combatantId + "-card-" + card, STRIKE));
        }
        return cards;
    }
}
