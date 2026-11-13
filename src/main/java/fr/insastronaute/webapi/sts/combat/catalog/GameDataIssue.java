package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Objects;

/**
 * One reason a piece of game data cannot be served faithfully by the authoritative engine.
 *
 * @param scope   which catalog the offending entry belongs to
 * @param entryId card id or enemy id, so a report groups by the thing a designer edits
 * @param path    JSON path of the offending value, relative to the scanned document
 * @param problem what is wrong, phrased so it can be fixed without reading engine code
 */
public record GameDataIssue(Scope scope, String entryId, String path, String problem) {

    public enum Scope {
        CARD,
        ENEMY
    }

    public GameDataIssue {
        Objects.requireNonNull(scope, "scope");
        requireNonBlank(entryId, "entryId");
        requireNonBlank(path, "path");
        requireNonBlank(problem, "problem");
    }

    @Override
    public String toString() {
        return scope + " '" + entryId + "' at " + path + ": " + problem;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
