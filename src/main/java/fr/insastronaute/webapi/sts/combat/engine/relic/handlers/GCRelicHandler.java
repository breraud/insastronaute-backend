package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Mirrors {@code Base/GCRelic.cs}, "Fondation solide":
 *
 * <blockquote>À la fin de chaque tour (ennemi ou allié), gagnez 2 d'Armure. L'Armure n'est pas
 * perdue quand vous jouez plusieurs tours à la suite.</blockquote>
 *
 * <p>Both halves are literal now. The relic answers the end of every turn, its owner's and anybody
 * else's, as Unity's {@code OnFieldTurnEnd} does -- it had been narrowed to the owner's own turn
 * on the grounds that granting on every turn was too strong, which bought the balance by breaking
 * the sentence.
 *
 * <p>What the sentence cannot say on its own is that a turn is worth less in a duel. A run against
 * three enemies closes four turns a round and pays four times; a duel closes two and pays twice.
 * Worse, only the grant made on the owner's own turn end is really spent, since armour falls again
 * when its owner's next turn opens and a duel puts nothing between the two but one enemy turn. The
 * duel therefore grants {@link #ARMOR_PER_TURN_DUEL} where a run grants {@link #ARMOR_PER_TURN}.
 *
 * <p>A duel is read as a table with nobody the machine is playing: PvP seats two humans, a run
 * always seats at least one AI. {@code CombatState} carries no format of its own to ask.
 *
 * <p>The grant is announced, like any other armour: a client that replays events rather than
 * polling state cannot draw armour it was never told about.
 */
@Component
public class GCRelicHandler implements RelicHandler {

    /** What a turn is worth in a run, and what the relic is written with. */
    private static final int ARMOR_PER_TURN = 2;

    /** What a turn is worth in a duel, where a round closes half as many of them. */
    private static final int ARMOR_PER_TURN_DUEL = 4;

    @Override
    public String id() {
        return "GCRelic";
    }

    @Override
    public CombatantState onFieldTurnEnd(
            CombatState state, CombatantState owner, String endingActorId, List<CombatEvent> events) {
        int granted = isDuel(state) ? ARMOR_PER_TURN_DUEL : ARMOR_PER_TURN;
        int newArmor = owner.armor() + granted;

        if (events != null && state != null) {
            events.add(new ArmorGained(
                    state.combatId(),
                    state.revision(),
                    owner.combatantId(),
                    owner.combatantId(),
                    granted,
                    granted,
                    newArmor));
        }

        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                newArmor,
                owner.energy(),
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                owner.piles(),
                owner.statuses(),
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }

    /**
     * A table with nobody the machine is playing: two duelists and no AI seat.
     *
     * <p>Read off the controllers rather than the teams, which is the one place that distinction
     * is the right one -- the question is not which side somebody is on but whether anybody here
     * is a player at all.
     */
    private static boolean isDuel(CombatState state) {
        return state != null && state.combatants().stream()
                .noneMatch(combatant -> combatant.controllerType() == ControllerType.AI);
    }

    @Override
    public boolean shouldClearArmor(CombatState state, CombatantState owner, String previousActorId) {
        // "L'Armure n'est pas perdue quand vous jouez plusieurs tours à la suite" -- read
        // literally. Answering false unconditionally made the armour accumulate without bound
        // across a normal alternation, which the relic never promised.
        return !owner.combatantId().equals(previousActorId);
    }
}
