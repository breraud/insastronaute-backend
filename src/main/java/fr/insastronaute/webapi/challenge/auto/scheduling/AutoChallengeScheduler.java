package fr.insastronaute.webapi.challenge.auto.scheduling;

import fr.insastronaute.webapi.challenge.auto.configuration.AutoChallengeProperties;
import fr.insastronaute.webapi.challenge.auto.evaluation.AutoChallengeEvaluationService;
import fr.insastronaute.webapi.challenge.auto.evaluation.AutoChallengeEvaluationSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "app.auto-challenges.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AutoChallengeScheduler {

    private final AutoChallengeEvaluationService evaluationService;
    private final AutoChallengeScanLock scanLock;
    private final AutoChallengeProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public AutoChallengeScheduler(
            AutoChallengeEvaluationService evaluationService,
            AutoChallengeScanLock scanLock,
            AutoChallengeProperties properties
    ) {
        this.evaluationService = evaluationService;
        this.scanLock = scanLock;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.auto-challenges.scheduler.cron:0 0 * * * *}")
    public void evaluateAutomaticChallenges() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            Optional<String> owner = scanLock.tryAcquire(properties.getLockDuration());
            if (owner.isEmpty()) {
                return;
            }
            try {
                AutoChallengeEvaluationSummary summary = evaluationService.evaluateAll();
                org.slf4j.LoggerFactory.getLogger(AutoChallengeScheduler.class).info(
                        "Automatic challenge scan completed: examinedChallenges={}, awardedUsers={}, skippedChallenges={}, errors={}, duration={}",
                        summary.examinedChallenges(), summary.awardedUsers(), summary.skippedChallenges(),
                        summary.errors(), summary.duration()
                );
            } finally {
                scanLock.release(owner.orElseThrow());
            }
        } finally {
            running.set(false);
        }
    }
}
