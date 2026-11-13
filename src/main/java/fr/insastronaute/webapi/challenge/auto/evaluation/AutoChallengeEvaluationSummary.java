package fr.insastronaute.webapi.challenge.auto.evaluation;

import java.time.Duration;

public record AutoChallengeEvaluationSummary(
        int examinedChallenges,
        int awardedUsers,
        int skippedChallenges,
        int errors,
        Duration duration
) {
}
