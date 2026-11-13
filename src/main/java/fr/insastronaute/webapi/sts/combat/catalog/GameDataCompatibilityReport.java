package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aggregated outcome of scanning one {@code dataVersion}.
 *
 * <p>The point of this record is that a single scan reports every incompatibility, so a data
 * version can be judged before a player enters a combat instead of failing on the first bad
 * entry. "Loadable" is deliberately kept apart from "faithful": {@link #loadableCardIds()} only
 * means the definition could be built, never that the mechanic matches Unity.
 */
public record GameDataCompatibilityReport(
        String dataVersion,
        List<String> loadableCardIds,
        List<String> blockedCardIds,
        List<String> loadableEnemyIds,
        List<String> blockedEnemyIds,
        List<GameDataIssue> issues
) {

    public GameDataCompatibilityReport {
        requireNonBlank(dataVersion, "dataVersion");
        loadableCardIds = List.copyOf(Objects.requireNonNull(loadableCardIds, "loadableCardIds"));
        blockedCardIds = List.copyOf(Objects.requireNonNull(blockedCardIds, "blockedCardIds"));
        loadableEnemyIds = List.copyOf(Objects.requireNonNull(loadableEnemyIds, "loadableEnemyIds"));
        blockedEnemyIds = List.copyOf(Objects.requireNonNull(blockedEnemyIds, "blockedEnemyIds"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean compatible() {
        return issues.isEmpty();
    }

    public List<GameDataIssue> issuesFor(String entryId) {
        return issues.stream()
                .filter(issue -> issue.entryId().equals(entryId))
                .toList();
    }

    public List<GameDataIssue> issues(GameDataIssue.Scope scope) {
        return issues.stream()
                .filter(issue -> issue.scope() == scope)
                .toList();
    }

    /** Human-readable matrix, suitable for a failing test message or a startup refusal. */
    public String describe() {
        StringBuilder report = new StringBuilder();
        report.append("STS game data compatibility for dataVersion '").append(dataVersion).append("'\n");
        report.append("  cards:   ").append(loadableCardIds.size()).append(" loadable, ")
                .append(blockedCardIds.size()).append(" blocked\n");
        report.append("  enemies: ").append(loadableEnemyIds.size()).append(" loadable, ")
                .append(blockedEnemyIds.size()).append(" blocked\n");
        if (issues.isEmpty()) {
            report.append("  no incompatibility detected\n");
            return report.toString();
        }
        report.append("  ").append(issues.size()).append(" incompatibilities:\n");
        issues.stream()
                .collect(Collectors.groupingBy(
                        GameDataIssue::entryId,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .forEach((entryId, entryIssues) -> {
                    report.append("  - ").append(entryIssues.getFirst().scope())
                            .append(' ').append(entryId).append('\n');
                    entryIssues.forEach(issue -> report.append("      ")
                            .append(issue.path()).append(": ").append(issue.problem()).append('\n'));
                });
        return report.toString();
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
