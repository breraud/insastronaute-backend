package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.model.RandomState;

/**
 * Names the card instances the engine creates during a combat.
 *
 * <p>A random UUID would make the same commands replayed on the same state produce a different
 * result, which breaks replay, snapshot comparison and any differential test against Unity. Every
 * generated name therefore comes from something already part of the combat state.
 */
public final class GeneratedCardId {

    private GeneratedCardId() {
    }

    /**
     * Name drawn from the random stream, for effects that already carry it. The caller must keep
     * the advanced state, otherwise the next card gets the same name.
     */
    public static String fromRandom(RandomState advanced) {
        return "gen-" + Long.toUnsignedString(advanced.value(), 36);
    }

    /**
     * Name for a creation that happens outside the effect pipeline and so has no random stream to
     * draw from. The scope, revision, owner and ordinal are unique together: a given source
     * creates a given card at most once per combatant per revision.
     */
    public static String fromContext(String scope, long revision, String combatantId, int ordinal) {
        return "gen-" + scope + "-" + revision + "-" + combatantId + "-" + ordinal;
    }
}
