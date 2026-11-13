package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.sts.combat.model.CombatState;

import java.util.Objects;

/**
 * Turn and revision guards for PvP commands.
 *
 * <p>The engine's timeline decides who acts; PvP only refuses input that does
 * not come from that combatant, so speed modifiers and extra turns keep working.
 */
public final class StsPvpTurnGuard {

    private StsPvpTurnGuard() {
    }

    /**
     * The combatant the engine lets act, which is {@link CombatState#activeCombatantId()} itself.
     *
     * <p>Re-deriving it from the timeline would be both redundant and wrong. {@code CombatState}
     * already refuses to exist unless an active combat's {@code activeCombatantId} is a living
     * combatant and the first living entry of the timeline, so on a live combat the two agree by
     * construction. On a finished combat they do not: the field is {@code null}, while walking the
     * timeline would name the surviving winner and let a command through after the combat is over.
     *
     * @throws ConflictException when the combat has no combatant left to act
     */
    public static String activeCombatantId(CombatState state) {
        Objects.requireNonNull(state, "state");
        String activeCombatantId = state.activeCombatantId();
        if (activeCombatantId == null) {
            throw new ConflictException("Ce combat n'a plus de combattant actif.");
        }
        return activeCombatantId;
    }

    public static void requireActiveCombatant(CombatState state, String combatantId) {
        if (!activeCombatantId(state).equals(combatantId)) {
            throw new ForbiddenException("Ce n'est pas votre tour.");
        }
    }

    public static void requireCurrentRevision(CombatState state, long expectedRevision) {
        Objects.requireNonNull(state, "state");
        if (state.revision() != expectedRevision) {
            throw new ConflictException("Révision de combat obsolète.");
        }
    }
}
