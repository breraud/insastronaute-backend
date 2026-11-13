package fr.insastronaute.webapi.challenge.auto;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class AutomaticChallengeMigrationTest {
    @Test void createsProtectedDailyCategory() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V74__daily_challenge_category.sql"));

        assertThat(sql).contains(
                "system_category BOOLEAN NOT NULL DEFAULT FALSE",
                "'DAILY'",
                "system_category = TRUE",
                "OLD.system_category = TRUE",
                "BEFORE UPDATE OR DELETE",
                "NEW.category = 'DAILY' AND NEW.end_date IS NULL"
        );
    }

    @Test void createsMembershipsBeforeGreedyCleanupAndAddsCandidateIndex() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V70__automatic_challenge_configuration.sql"));
        assertThat(sql.indexOf("SET validation_mode = 'ADMIN'")).isBetween(0, sql.indexOf("chk_challenge_auto_config") - 1);
        assertThat(sql.indexOf("CREATE TABLE challenge_active_memberships")).isLessThan(sql.indexOf("DO $$"));
        assertThat(sql.indexOf("DO $$")).isLessThan(sql.indexOf("CREATE TRIGGER trg_challenge_active_memberships_submissions"));
        assertThat(sql).contains("ORDER BY submission.created_at, submission.id", "CREATE INDEX", "ON users(id)", "WHERE scanned = true");
    }

    @Test void reservesOwnersAndParticipantsForEveryBlockingStatus() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V70__automatic_challenge_configuration.sql"));

        assertThat(sql).contains(
                "CREATE TABLE challenge_active_memberships",
                "PRIMARY KEY (challenge_id, user_id)",
                "CONSTRAINT fk_challenge_active_memberships_submission",
                "REFERENCES challenge_submissions(id)",
                "WAITING_CONFIRMATION", "PENDING", "VALIDATED",
                "UNION",
                "FROM challenge_submission_participants participant",
                "CREATE INDEX idx_challenge_submission_active_owner",
                "CREATE TRIGGER trg_challenge_active_memberships_submissions",
                "AFTER INSERT OR UPDATE OF status, challenge_id, user_id OR DELETE",
                "CREATE TRIGGER trg_challenge_active_memberships_participants",
                "AFTER INSERT OR DELETE"
        );
        assertThat(sql.indexOf("CREATE TABLE challenge_active_memberships")).isLessThan(sql.indexOf("INSERT INTO challenge_active_memberships"));
        assertThat(sql.indexOf("INSERT INTO challenge_active_memberships")).isLessThan(sql.indexOf("CREATE TRIGGER trg_challenge_active_memberships_submissions"));
    }

    @Test void greedilyRejectsOnlyTheCurrentSubmissionAgainstPreviouslyKeptWinners() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V70__automatic_challenge_configuration.sql"));

        assertThat(sql).contains(
                "FOR blocking_submission IN",
                "ORDER BY submission.created_at, submission.id",
                "SELECT ARRAY(",
                "UNION",
                "unnest(member_ids)",
                "FROM challenge_active_memberships membership",
                "SET status = 'REJECTED'",
                "WHERE id = blocking_submission.id",
                "ELSE",
                "INSERT INTO challenge_active_memberships"
        );
        assertThat(sql).doesNotContain("ROW_NUMBER() OVER", "ranked_blocking_memberships");

        // S0 owns B and wins. S1 owns A with participant B, so it loses to S0.
        // S2 owns A and must then win because rejected S1 reserved nothing.
        java.util.Set<String> reserved = new java.util.HashSet<>();
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (var submission : java.util.List.of(
                new HistoricalSubmission("S0", java.util.Set.of("B")),
                new HistoricalSubmission("S1", java.util.Set.of("A", "B")),
                new HistoricalSubmission("S2", java.util.Set.of("A")))) {
            if (submission.members().stream().noneMatch(reserved::contains)) {
                reserved.addAll(submission.members());
                kept.add(submission.id());
            }
        }
        assertThat(kept).containsExactly("S0", "S2");
    }

    @Test void keepsValidatedCompletionClaimsIndependentFromActiveMemberships() throws Exception {
        String v24 = Files.readString(Path.of("src/main/resources/db/migration/V24__challenge_validation_and_completion_claims.sql"));
        String v70 = Files.readString(Path.of("src/main/resources/db/migration/V70__automatic_challenge_configuration.sql"));

        assertThat(v24).contains("CREATE TABLE challenge_completion_claims", "WHERE status = 'VALIDATED'");
        assertThat(v70).doesNotContain("DROP TABLE challenge_completion_claims", "DROP TRIGGER trg_challenge_completion_claims");
    }

    private record HistoricalSubmission(String id, java.util.Set<String> members) {}
}
