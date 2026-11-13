package fr.insastronaute.webapi.challenge.auto.award;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AutoChallengeAwardAttemptService {
    private final ChallengeRepository challenges;
    private final UserRepository users;
    private final ChallengeSubmissionRepository submissions;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final NotificationService notificationService;

    public AutoChallengeAwardAttemptService(ChallengeRepository challenges, UserRepository users,
                                            ChallengeSubmissionRepository submissions,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            NotificationService notificationService) {
        this.challenges = challenges;
        this.users = users;
        this.submissions = submissions;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.notificationService = notificationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void award(UUID challengeId, UUID userId) {
        award(challengeId, userId, java.util.List.of());
    }

    /**
     * Attribuer un defi automatique, seul ou pour toute une equipe.
     *
     * <p>{@code teammateIds} enregistre les autres membres sur la soumission. C'est ce qui
     * rend le partage possible et surtout stable : la recompense se divisera entre ceux-la
     * et non entre l'equipe du moment, qui aura peut-etre change quand les points seront
     * recalcules.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void award(UUID challengeId, UUID userId, java.util.List<UUID> teammateIds) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setChallenge(challenges.getReferenceById(challengeId));
        submission.setUser(users.getReferenceById(userId));
        if (!teammateIds.isEmpty()) {
            submission.setParticipants(teammateIds.stream()
                    .map(users::getReferenceById)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        }
        submission.setStatus("VALIDATED");
        submission.setProofType("AUTO");
        submission.setProofData("");
        submission.setValidatedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        submission.setBonusPoints(0);
        submission.setMessagesEnabled(false);
        submissions.saveAndFlush(submission);

        // Un défi attribué sans un mot ne se voit nulle part : le joueur n'a rien
        // envoyé, donc rien n'attire son regard vers le résultat. La notification
        // est ce qui allume la popup, tout de suite ou à sa prochaine venue.
        Challenge challenge = submission.getChallenge();
        notificationService.notifyUser(
                userId,
                NotificationType.CHALLENGE_SUBMISSION_VALIDATED,
                "Défi validé",
                "Tu as validé « " + challenge.getTitle() + " ».",
                java.util.Map.of(
                        "challengeId", challenge.getId().toString(),
                        "submissionId", submission.getId().toString(),
                        "status", "VALIDATED",
                        "pointsReward", challenge.getPointsReward() == null ? 0 : challenge.getPointsReward(),
                        "bonusPoints", 0,
                        "automatic", true
                )
        );
        autoChallengeTriggerPublisher.publish(userId, AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }
}
