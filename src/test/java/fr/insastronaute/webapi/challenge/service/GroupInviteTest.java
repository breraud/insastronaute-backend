package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeGroupInvite;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeFavoriteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeGroupInviteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionMessageRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'invitation par QR : montrer un code plutôt que chercher quelqu'un par son nom.
 *
 * <p>Scanner vaut acceptation. Renvoyer ensuite une invitation à confirmer ce qu'on
 * vient de faire serait exactement la redondance que ce code existe pour supprimer.</p>
 */
@ExtendWith(MockitoExtension.class)
class GroupInviteTest {

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
    @Mock ChallengeVisibilityService challengeVisibility;
    @Mock fr.insastronaute.webapi.challenge.repository.ChallengeGraceRepository graceRepository;
    @Mock ChallengeGroupInviteRepository groupInviteRepository;
    @Mock org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private ChallengeService challengeService;
    private final UUID challengeId = UUID.randomUUID();
    private AppUser leader;
    private AppUser scanner;
    private Challenge challenge;
    private ChallengeGroupInvite invite;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(
                challengeRepository, categoryRepository, favoriteRepository, userRepository,
                submissionRepository, submissionMessageRepository, departmentRepository,
                verificationLinkRepository, proofStorageService, notificationService,
                autoChallengeTypeRegistry, moderationAuditService, mediaReadAuditPublisher,
                autoChallengeTriggerPublisher, journal, challengeVisibility,
                org.mockito.Mockito.mock(fr.insastronaute.webapi.friend.repository.FriendshipRepository.class),
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),
                org.mockito.Mockito.mock(fr.insastronaute.webapi.leaderboard.service.LeaderboardService.class),
                graceRepository
        );
        // Les deux dépendances optionnelles sont injectées par champ en production.
        ReflectionTestUtils.setField(challengeService, "groupInviteRepository", groupInviteRepository);
        ReflectionTestUtils.setField(challengeService, "messagingTemplate", messagingTemplate);
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setFrontendBaseUrl("https://insastronaute.test");
        ReflectionTestUtils.setField(challengeService, "securityProperties", properties);

        leader = new AppUser();
        leader.setId(UUID.randomUUID());
        leader.setKeycloakSub("leader-sub");
        leader.setDisplayName("Camille");

        scanner = new AppUser();
        scanner.setId(UUID.randomUUID());
        scanner.setKeycloakSub("scanner-sub");
        scanner.setDisplayName("Alex");

        challenge = new Challenge();
        challenge.setId(challengeId);
        challenge.setTitle("Photo de promo");
        challenge.setValidationMode("ADMIN");
        challenge.setPointsReward(10);
        challenge.setMinGroupSize(2);
        challenge.setMaxGroupSize(4);

        invite = new ChallengeGroupInvite();
        invite.setChallenge(challenge);
        invite.setSubmitter(leader);
        invite.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
    }

    private ChallengeSubmission waitingSubmission() {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setStatus("WAITING_CONFIRMATION");
        submission.setUser(leader);
        submission.setChallenge(challenge);
        return submission;
    }

    @Test
    void theCodeCarriesTheUrlThePhoneWillOpen() {
        when(userRepository.findByKeycloakSub("leader-sub")).thenReturn(Optional.of(leader));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(groupInviteRepository.findByChallengeIdAndSubmitterId(challengeId, leader.getId()))
                .thenReturn(Optional.of(invite));

        var code = challengeService.createGroupInviteCode("leader-sub", challengeId);

        assertThat(code.joinUrl()).isEqualTo("https://insastronaute.test/rejoindre/" + invite.getToken());
        assertThat(code.qrSvg()).startsWith("<svg").contains("path");
        // Régénérer prolonge le même code : un code montré hier ne doit pas rester
        // valable à côté de celui d'aujourd'hui.
        assertThat(code.token()).isEqualTo(invite.getToken());
    }

    @Test
    void scanningAWaitingTeamJoinsItAlreadyAccepted() {
        ChallengeSubmission submission = waitingSubmission();
        when(userRepository.findByKeycloakSub("scanner-sub")).thenReturn(Optional.of(scanner));
        when(groupInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(leader.getId()), challengeId))
                .thenReturn(List.of(submission));
        when(submissionRepository.countParticipantsNotAccepted(submission.getId())).thenReturn(0L);

        challengeService.joinGroupInvite("scanner-sub", invite.getToken());

        assertThat(submission.getParticipants()).containsExactly(scanner);
        // Scanner vaut acceptation : la confirmation est posée sans rien demander.
        verify(submissionRepository).updateParticipantConfirmation(submission.getId(), scanner.getId(), "ACCEPTED");
        // Et la preuve part, puisque plus personne n'est attendu.
        assertThat(submission.getStatus()).isEqualTo("PENDING");
        // Une invitation à accepter n'aurait plus aucun sens.
        verify(notificationService, never()).notifyUser(
                eq(scanner.getId()), any(), eq(NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED),
                any(), any(), anyMap(), any(), any());
    }

    @Test
    void scanningBeforeTheProofLeavesJoinsTheTeamBeingComposed() {
        when(userRepository.findByKeycloakSub("scanner-sub")).thenReturn(Optional.of(scanner));
        when(groupInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(leader.getId()), challengeId))
                .thenReturn(List.of());

        challengeService.joinGroupInvite("scanner-sub", invite.getToken());

        // L'équipe vit dans le brouillon du chef tant que la preuve n'est pas partie.
        verify(proofStorageService).addDraftParticipant(leader, challenge, scanner);
        assertThat(invite.getJoinedUserIds()).containsExactly(scanner.getId());
    }

    /**
     * Le chef a le panneau sous les yeux pendant que les gens scannent.
     *
     * <p>Sans ce mot, il doit fermer la carte et la rouvrir pour voir qui est
     * arrivé — ce qui, un code QR à la main, est exactement le mauvais moment.</p>
     */
    @Test
    void theLeaderIsToldHisTeamJustChanged() {
        when(userRepository.findByKeycloakSub("scanner-sub")).thenReturn(Optional.of(scanner));
        when(groupInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(leader.getId()), challengeId))
                .thenReturn(List.of());

        challengeService.joinGroupInvite("scanner-sub", invite.getToken());

        // Adressé au chef, sur la clé qui sert déjà à la fresque et aux combats.
        verify(messagingTemplate).convertAndSendToUser(
                eq("leader-sub"), eq("/queue/challenge-teams"), any(Object.class));
    }

    @Test
    void anExpiredCodeSaysWhatToDo() {
        invite.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(userRepository.findByKeycloakSub("scanner-sub")).thenReturn(Optional.of(scanner));
        when(groupInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));

        var preview = challengeService.previewGroupInvite("scanner-sub", invite.getToken());

        assertThat(preview.canJoin()).isFalse();
        assertThat(preview.reason()).contains("expiré");
        assertThatThrownBy(() -> challengeService.joinGroupInvite("scanner-sub", invite.getToken()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void yourOwnCodeIsNotForYou() {
        when(userRepository.findByKeycloakSub("leader-sub")).thenReturn(Optional.of(leader));
        when(groupInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));

        var preview = challengeService.previewGroupInvite("leader-sub", invite.getToken());

        assertThat(preview.canJoin()).isFalse();
        assertThat(preview.reason()).contains("ton propre code");
    }

    @Test
    void aFullTeamTurnsTheScannerAway() {
        ChallengeSubmission submission = waitingSubmission();
        for (int index = 0; index < 3; index++) {
            AppUser member = new AppUser();
            member.setId(UUID.randomUUID());
            member.setDisplayName("Membre " + index);
            submission.getParticipants().add(member);
        }
        when(userRepository.findByKeycloakSub("scanner-sub")).thenReturn(Optional.of(scanner));
        when(groupInviteRepository.findByToken(invite.getToken())).thenReturn(Optional.of(invite));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(leader.getId()), challengeId))
                .thenReturn(List.of(submission));

        var preview = challengeService.previewGroupInvite("scanner-sub", invite.getToken());

        assertThat(preview.canJoin()).isFalse();
        assertThat(preview.reason()).contains("au complet");
    }
}
