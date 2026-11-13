package fr.insastronaute.webapi.challenge.auto.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.time.DurationMin;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "app.auto-challenges.scheduler")
public class AutoChallengeProperties {

    private boolean enabled = true;
    private String cron = "0 0 * * * *";
    @Min(1)
    private int batchSize = 250;
    @DurationMin(seconds = 1)
    private Duration lockDuration = Duration.ofMinutes(55);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }
}
