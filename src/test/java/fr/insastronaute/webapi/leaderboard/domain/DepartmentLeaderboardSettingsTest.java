package fr.insastronaute.webapi.leaderboard.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DepartmentLeaderboardSettingsTest {

    @Test
    void requiresAPositiveTheoreticalHeadcountAndMaintainsTimestamps() {
        DepartmentLeaderboardSettings settings = new DepartmentLeaderboardSettings();

        assertThatIllegalArgumentException().isThrownBy(() -> settings.setTheoreticalHeadcount(0));

        settings.setTheoreticalHeadcount(70);
        settings.onCreate();
        assertThat(settings.getTheoreticalHeadcount()).isEqualTo(70);
        assertThat(settings.getCreatedAt()).isNotNull();
        assertThat(settings.getUpdatedAt()).isEqualTo(settings.getCreatedAt());

        settings.onUpdate();
        assertThat(settings.getUpdatedAt()).isNotNull();
    }
}
