package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeFavoriteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionMessageRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les preuves d'un defi sensible quittent le stockage des que la moderation a tranche.
 *
 * <p>Un defi sensible recoit des photos que personne n'a de raison de garder une fois la
 * decision prise. La seule duree de conservation qui se defend est celle de l'examen :
 * elle s'arrete a l'arbitrage, quel qu'il soit.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensitiveProofPurgeTest {

    @Mock ChallengeRepository challengeRepository;
    @Mock ChallengeCategoryRepository categoryRepository;
    @Mock ChallengeFavoriteRepository favoriteRepository;
    @Mock UserRepository userRepository;
    @Mock ChallengeSubmissionRepository submissionRepository;
    @Mock ChallengeSubmissionMessageRepository submissionMessageRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock DepartmentVerificationLinkRepository verificationLinkRepository;
    @Mock ProofStorageService proofStorageService;
    @Mock NotificationService notificationService;
    @Mock AutoChallengeTypeRegistry autoChallengeTypeRegistry;
    @Mock ModerationAuditService moderationAuditService;
    @Mock MediaReadAuditPublisher mediaReadAuditPublisher;
    @Mock AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    @Mock AdminActionJournal journal;

    private ChallengeService challengeService;
    private ChallengeSubmission submission;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(
                challengeRepository, categoryRepository, favoriteRepository, userRepository,
                submissionRepository, submissionMessageRepository, departmentRepository,
                verificationLinkRepository, proofStorageService, notificationService,
                autoChallengeTypeRegistry, moderationAuditService, mediaReadAuditPublisher,
                autoChallengeTriggerPublisher, journal,
                mock(ChallengeVisibilityService.class), mock(FriendshipRepository.class),
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class)
        );

        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub("player-sub");

        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setTitle("Défi sensible");
        challenge.setPointsReward(10);

        submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setStatus("PENDING");
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setProofType("MEDIA");
        submission.setProofData("[\"proofs/a.jpg\"]");
        // La decision exige le verrou de review, sans quoi decideSubmission refuse.
        submission.setReviewLockedBy("sub-moderator");
        submission.setReviewStartedAt(Instant.now());
        submission.setReviewExpiresAt(Instant.now().plusSeconds(900));

        when(submissionRepository.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        when(proofStorageService.parseProofFiles(any())).thenAnswer(invocation -> {
            String data = invocation.getArgument(0);
            return data != null && data.startsWith("[\"") ? List.of("proofs/a.jpg") : List.<String>of();
        });
    }

    private void decide(String status) {
        challengeService.decideSubmission(submission.getId(), "sub-moderator",
                List.of("ROLE_ADMIN"), status, status.equals("REJECTED") ? "flou" : null, null);
    }

    @Test
    void aSensitiveChallengeLosesItsMediaOnValidation() {
        submission.getChallenge().setSensitive(true);

        decide("VALIDATED");

        verify(proofStorageService).purgeSubmittedProof("[\"proofs/a.jpg\"]");
        // Les cles sont videes en meme temps : sinon les ecrans continueraient de demander
        // des fichiers qui n'existent plus.
        assertThat(submission.getProofData()).isEqualTo("[]");
        assertThat(submission.getProofMediaPurgedAt()).isNotNull();
    }

    /** Un refus est un arbitrage aussi : la photo n'a pas plus de raison de rester. */
    @Test
    void aSensitiveChallengeLosesItsMediaOnRejection() {
        submission.getChallenge().setSensitive(true);

        decide("REJECTED");

        verify(proofStorageService).purgeSubmittedProof(any());
        assertThat(submission.getProofMediaPurgedAt()).isNotNull();
    }

    /** Un defi ordinaire garde ses preuves : elles servent encore. */
    @Test
    void anOrdinaryChallengeKeepsItsMedia() {
        submission.getChallenge().setSensitive(false);

        decide("VALIDATED");

        verify(proofStorageService, never()).purgeSubmittedProof(any());
        assertThat(submission.getProofData()).isEqualTo("[\"proofs/a.jpg\"]");
        assertThat(submission.getProofMediaPurgedAt()).isNull();
    }

    /** Une preuve texte n'a pas de fichier : rien a effacer, et rien a signaler. */
    @Test
    void aTextProofIsLeftAlone() {
        submission.getChallenge().setSensitive(true);
        submission.setProofType("TEXT");
        submission.setProofData("j'y étais");

        decide("VALIDATED");

        verify(proofStorageService, never()).purgeSubmittedProof(any());
        assertThat(submission.getProofData()).isEqualTo("j'y étais");
        assertThat(submission.getProofMediaPurgedAt()).isNull();
    }
}
