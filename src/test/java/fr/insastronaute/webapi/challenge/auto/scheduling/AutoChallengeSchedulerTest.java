package fr.insastronaute.webapi.challenge.auto.scheduling;

import fr.insastronaute.webapi.challenge.auto.configuration.AutoChallengeProperties;
import fr.insastronaute.webapi.challenge.auto.evaluation.AutoChallengeEvaluationService;
import fr.insastronaute.webapi.challenge.auto.evaluation.AutoChallengeEvaluationSummary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AutoChallengeSchedulerTest {

    @Test
    void logsTheCompleteSuccessfulSummary(CapturedOutput output) {
        AutoChallengeEvaluationService evaluator = mock(AutoChallengeEvaluationService.class);
        AutoChallengeScanLock lock = mock(AutoChallengeScanLock.class);
        AutoChallengeProperties properties = properties();
        when(lock.tryAcquire(properties.getLockDuration())).thenReturn(Optional.of("owner"));
        when(evaluator.evaluateAll()).thenReturn(new AutoChallengeEvaluationSummary(
                3, 7, 1, 2, Duration.ofSeconds(4)
        ));

        new AutoChallengeScheduler(evaluator, lock, properties).evaluateAutomaticChallenges();

        assertThat(output).contains("examinedChallenges=3", "awardedUsers=7", "skippedChallenges=1",
                "errors=2", "duration=PT4S");
    }

    @Test
    void skipsEvaluationWhenDistributedLockIsUnavailable() {
        AutoChallengeEvaluationService evaluator = mock(AutoChallengeEvaluationService.class);
        AutoChallengeScanLock lock = mock(AutoChallengeScanLock.class);
        AutoChallengeProperties properties = properties();
        when(lock.tryAcquire(properties.getLockDuration())).thenReturn(Optional.empty());

        new AutoChallengeScheduler(evaluator, lock, properties).evaluateAutomaticChallenges();

        verify(evaluator, never()).evaluateAll();
        verify(lock, never()).release(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void evaluatesWithLockAndAlwaysReleasesOwnerToken() {
        AutoChallengeEvaluationService evaluator = mock(AutoChallengeEvaluationService.class);
        AutoChallengeScanLock lock = mock(AutoChallengeScanLock.class);
        AutoChallengeProperties properties = properties();
        when(lock.tryAcquire(properties.getLockDuration())).thenReturn(Optional.of("owner"));
        doThrow(new IllegalStateException("evaluation failed")).when(evaluator).evaluateAll();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AutoChallengeScheduler(evaluator, lock, properties).evaluateAutomaticChallenges()
        ).isInstanceOf(IllegalStateException.class);

        verify(evaluator).evaluateAll();
        verify(lock).release("owner");
    }

    @Test
    void preventsOverlappingRunsInsideOneApplicationInstance() throws Exception {
        AutoChallengeEvaluationService evaluator = mock(AutoChallengeEvaluationService.class);
        AutoChallengeScanLock lock = mock(AutoChallengeScanLock.class);
        AutoChallengeProperties properties = properties();
        CountDownLatch evaluationStarted = new CountDownLatch(1);
        CountDownLatch finishEvaluation = new CountDownLatch(1);
        when(lock.tryAcquire(properties.getLockDuration())).thenReturn(Optional.of("owner"));
        doAnswer(invocation -> {
            evaluationStarted.countDown();
            assertThat(finishEvaluation.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(evaluator).evaluateAll();
        AutoChallengeScheduler scheduler = new AutoChallengeScheduler(evaluator, lock, properties);

        Thread firstRun = Thread.ofPlatform().start(scheduler::evaluateAutomaticChallenges);
        assertThat(evaluationStarted.await(2, TimeUnit.SECONDS)).isTrue();
        scheduler.evaluateAutomaticChallenges();
        finishEvaluation.countDown();
        firstRun.join(2_000);

        assertThat(firstRun.isAlive()).isFalse();
        verify(lock).tryAcquire(properties.getLockDuration());
        verify(evaluator).evaluateAll();
        verify(lock).release("owner");
    }

    @Test
    void isEnabledByDefaultAndCanBeDisabledByProperty() {
        ConditionalOnProperty condition = AutoChallengeScheduler.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition.prefix()).isEqualTo("app.auto-challenges.scheduler");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    private AutoChallengeProperties properties() {
        AutoChallengeProperties properties = new AutoChallengeProperties();
        properties.setLockDuration(Duration.ofMinutes(7));
        return properties;
    }
}
