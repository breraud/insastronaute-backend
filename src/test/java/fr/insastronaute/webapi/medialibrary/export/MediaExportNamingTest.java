package fr.insastronaute.webapi.medialibrary.export;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaExportNamingTest {

    @Test
    void slugKeepsPathsSafe() {
        assertThat(MediaExportNaming.slug("Chanter au RU !")).isEqualTo("chanter-au-ru");
        assertThat(MediaExportNaming.slug("Éléphant/rose: \"x\"")).isEqualTo("elephant-rose-x");
        assertThat(MediaExportNaming.slug("../../etc/passwd")).isEqualTo("etc-passwd");
        assertThat(MediaExportNaming.slug("🚀")).isEqualTo("sans-nom");
        assertThat(MediaExportNaming.slug("")).isEqualTo("sans-nom");
        assertThat(MediaExportNaming.slug(null)).isEqualTo("sans-nom");
        assertThat(MediaExportNaming.slug("a".repeat(30) + " " + "b".repeat(40)))
                .hasSizeLessThanOrEqualTo(60)
                .doesNotEndWith("-");
    }

    @Test
    void theDayIsReadInParis() {
        assertThat(MediaExportNaming.day(Instant.parse("2026-09-14T23:30:00Z"))).isEqualTo("2026-09-15");
        assertThat(MediaExportNaming.day(null)).isEqualTo("sans-date");
    }

    @Test
    void entryPathNamesTheChallengeTheDayTheGroupAndThePosition() {
        UUID submission = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000000");

        String path = MediaExportNaming.entryPath("mediatheque-2026-10-02", "Chanter au RU",
                Instant.parse("2026-09-15T10:00:00Z"), "Léo Durand", 3, submission, 0, "jpg");

        assertThat(path).isEqualTo("mediatheque-2026-10-02/chanter-au-ru/2026-09-15_leo-durand+3_a1b2c3d4_1.jpg");
    }

    @Test
    void otherNamesFollowTheSameRules() {
        Instant day = Instant.parse("2026-09-14T10:00:00Z");

        assertThat(MediaExportNaming.downloadFileName("Défi", "Camille Martin", day, 1, "mp4"))
                .isEqualTo("defi_camille-martin_2026-09-14_2.mp4");
        assertThat(MediaExportNaming.partFileName(Instant.parse("2026-10-02T08:00:00Z"), 1, 3))
                .isEqualTo("mediatheque-2026-10-02-partie-1-sur-3.zip");
        assertThat(MediaExportNaming.extension("proofs/u/c/x.JPEG")).isEqualTo("jpeg");
        assertThat(MediaExportNaming.extension("proofs/u/c/sans-extension")).isEqualTo("bin");
    }
}
