package fr.insastronaute.webapi.common.timetravel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeTravelContextTest {

    @AfterEach
    void clear() {
        TimeTravelContext.clear();
    }

    @Test
    void withRealTimeSuspendsTheForcedClockAndGivesItBack() {
        Instant forced = Instant.now().plus(Duration.ofDays(30));
        UUID department = UUID.randomUUID();
        TimeTravelContext.setForcedTime(forced);
        TimeTravelContext.setForcedDepartmentId(department);

        Instant seen = TimeTravelContext.withRealTime(TimeTravelContext::instantNow);

        assertThat(seen).isBefore(forced);
        assertThat(TimeTravelContext.getForcedTime()).isEqualTo(forced);
        assertThat(TimeTravelContext.getForcedDepartmentId()).isEqualTo(department);
    }

    @Test
    void withRealTimeGivesTheForcedClockBackEvenWhenTheActionFails() {
        Instant forced = Instant.now().plus(Duration.ofDays(30));
        TimeTravelContext.setForcedTime(forced);

        assertThatThrownBy(() -> TimeTravelContext.withRealTime(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TimeTravelContext.getForcedTime()).isEqualTo(forced);
    }

    @Test
    void withRealTimeLeavesARequestWithoutTimeTravelWithoutIt() {
        TimeTravelContext.withRealTime(() -> null);

        assertThat(TimeTravelContext.getForcedTime()).isNull();
    }
}
