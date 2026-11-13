package fr.insastronaute.webapi.sts.combat.api;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombatCommandMetricsTest {

    @Test
    void recordsEachFixedCommandPhase() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CombatCommandMetrics metrics = new CombatCommandMetrics(registry);

        String result = metrics.record("catalog", () -> "ready");
        metrics.record("publish", () -> { });

        assertThat(result).isEqualTo("ready");
        assertThat(registry.find("sts.combat.command.duration").tag("phase", "catalog").timer().count())
                .isEqualTo(1);
        assertThat(registry.find("sts.combat.command.duration").tag("phase", "publish").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsFailedPhasesWithoutSwallowingTheFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CombatCommandMetrics metrics = new CombatCommandMetrics(registry);

        assertThatThrownBy(() -> metrics.record("player_engine", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.find("sts.combat.command.duration")
                .tag("phase", "player_engine").timer().count()).isEqualTo(1);
    }
}
