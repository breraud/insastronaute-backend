package fr.insastronaute.webapi.sts.combat.model;

import java.util.Objects;

/**
 * One thing a relic remembers for the length of a combat.
 *
 * <p>A relic that fires once per combat, or that counts up to something, needs somewhere to keep
 * that. It cannot be the handler: handlers are singletons shared by every combat in flight. It
 * used to be a status on the owner, which sent the note to the client as if it were part of the
 * fight and let a card dispel it. It is a field of its own now, saved and reloaded with the rest
 * of the state and out of reach of anything in the game.
 *
 * @param relicId which relic wrote it, so two relics cannot tread on each other
 * @param key     what it is, for a relic that remembers more than one thing
 * @param value   the number; a flag is 1
 */
public record RelicMemory(String relicId, String key, int value) {

    public RelicMemory {
        Objects.requireNonNull(relicId, "relicId");
        Objects.requireNonNull(key, "key");
    }
}
