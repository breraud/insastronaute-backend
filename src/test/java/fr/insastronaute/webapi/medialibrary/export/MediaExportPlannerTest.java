package fr.insastronaute.webapi.medialibrary.export;

import fr.insastronaute.webapi.medialibrary.export.MediaExportPlanner.Candidate;
import fr.insastronaute.webapi.medialibrary.export.MediaExportPlanner.PlannedItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaExportPlannerTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void ordersByChallengeThenDateThenSubmissionThenPosition() {
        Instant early = Instant.parse("2026-09-01T00:00:00Z");
        Instant late = Instant.parse("2026-09-02T00:00:00Z");
        List<PlannedItem> plan = MediaExportPlanner.plan(List.of(
                new Candidate(B, 1, "Zèbre", early, 10),
                new Candidate(A, 1, "Élan", late, 10),
                new Candidate(A, 0, "Élan", late, 10),
                new Candidate(B, 0, "Élan", early, 10)
        ), 1_000);

        assertThat(plan).extracting(item -> item.candidate().challengeTitle() + ":" + item.candidate().mediaIndex())
                .containsExactly("Élan:0", "Élan:0", "Élan:1", "Zèbre:1");
        assertThat(plan).extracting(PlannedItem::position).containsExactly(0, 1, 2, 3);
    }

    @Test
    void aPartNeverExceedsItsLimit() {
        List<PlannedItem> plan = MediaExportPlanner.plan(List.of(
                new Candidate(A, 0, "Defi", Instant.EPOCH, 600),
                new Candidate(A, 1, "Defi", Instant.EPOCH, 400),
                new Candidate(A, 2, "Defi", Instant.EPOCH, 1),
                new Candidate(A, 3, "Defi", Instant.EPOCH, 999)
        ), 1_000);

        assertThat(plan).extracting(PlannedItem::partNumber).containsExactly(1, 1, 2, 2);
        assertThat(MediaExportPlanner.partCount(plan)).isEqualTo(2);
        assertThat(MediaExportPlanner.partCount(List.of())).isZero();
    }

    @Test
    void aFileLargerThanALimitStillGetsItsOwnPart() {
        List<PlannedItem> plan = MediaExportPlanner.plan(List.of(
                new Candidate(A, 0, "Defi", Instant.EPOCH, 10),
                new Candidate(A, 1, "Defi", Instant.EPOCH, 5_000)
        ), 1_000);

        assertThat(plan).extracting(PlannedItem::partNumber).containsExactly(1, 2);
    }

    @Test
    void theLimitIsTwoGibibytes() {
        assertThat(MediaExportPlanner.PART_LIMIT_BYTES).isEqualTo(2_147_483_648L);
    }
}
