package fr.insastronaute.webapi.sts.combat.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class CombatCommandMetrics {

    private static final String METRIC_NAME = "sts.combat.command.duration";

    private final MeterRegistry meterRegistry;

    public CombatCommandMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T record(String phase, Supplier<T> operation) {
        return timer(phase).record(operation);
    }

    public void record(String phase, Runnable operation) {
        timer(phase).record(operation);
    }

    private Timer timer(String phase) {
        return Timer.builder(METRIC_NAME)
                .description("Time spent processing an authoritative STS combat command")
                .tag("phase", phase)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }
}
