package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.dto.ChallengeSubmissionDto;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeFavoriteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionMessageRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le défi de promo : le département le relève une fois, pour lui.
 *
 * <p>Trois règles qui ne se déduisent pas l'une de l'autre : ses points vont au
 * département et non au porteur de la preuve, il se ferme aux autres dès la
 * première preuve envoyée, et une fois gagné il compte comme fait pour toute la
 * promo.</p>
 */
@ExtendWith(MockitoExtension.class)
class PromoChallengeTest {

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

    private ChallengeService challengeService;
    private final UUID departmentId = UUID.randomUUID();
    private final UUID challengeId = UUID.randomUUID();
    private AppUser player;
    private Challenge challenge;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(
                challengeRepository,
                categoryRepository,
                favoriteRepository,
                userRepository,
                submissionRepository,
                submissionMessageRepository,
                departmentRepository,
                verificationLinkRepository,
                proofStorageService,
                notificationService,
                autoChallengeTypeRegistry,
                moderationAuditService,
                mediaReadAuditPublisher,
                autoChallengeTriggerPublisher,
                journal,
                challengeVisibility,
                org.mockito.Mockito.mock(fr.insastronaute.webapi.friend.repository.FriendshipRepository.class),
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),
                org.mockito.Mockito.mock(fr.insastronaute.webapi.leaderboard.service.LeaderboardService.class),
                graceRepository
        );

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub("player-sub");
        player.setDepartment(departmentId);

        challenge = new Challenge();
        challenge.setId(challengeId);
        challenge.setTitle("Photo de toute la promo");
        challenge.setPointsReward(100);
        challenge.setValidationMode("ADMIN");
        challenge.setProofType("TEXT");
        challenge.setPromo(true);
    }

    private ChallengeSubmission submission(String status) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(status);
        submission.setChallenge(challenge);
        AppUser teammate = new AppUser();
        teammate.setId(UUID.randomUUID());
        teammate.setDepartment(departmentId);
        submission.setUser(teammate);
        return submission;
    }

    @Test
    void aPromoTakesItsChallengeOnlyOnce() {
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findPromoBlockingSubmissions(challengeId, departmentId))
                .thenReturn(List.of(submission("VALIDATED")));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("déjà validé");
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void aProofAlreadySentClosesTheChallengeForTheOthers() {
        // Il n'est à relever qu'une fois : laisser trois personnes préparer la même
        // preuve, c'est en refuser deux après coup pour un travail déjà fait.
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findPromoBlockingSubmissions(challengeId, departmentId))
                .thenReturn(List.of(submission("PENDING")));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("attend d'être jugée");
    }

    @Test
    void aRefusedProofGivesThePromoItsChanceBack() {
        // La requête ne rend ni les refusées ni les annulées : rien ne bloque plus.
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findPromoBlockingSubmissions(challengeId, departmentId))
                .thenReturn(List.of());
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(player.getId()), challengeId))
                .thenReturn(List.of());

        challengeService.submitChallenge("player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve"));

        verify(submissionRepository).save(any());
    }

    @Test
    void aPlayerWithoutAPromoCannotTakeItOn() {
        player.setDepartment((UUID) null);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void thePointsGoToTheDepartmentAndNotToTheProofBearer() {
        ChallengeSubmission validated = submission("VALIDATED");
        AppUser bearer = validated.getUser();
        bearer.setChallengePoints(0L);
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(validated));
        when(userRepository.findAll()).thenReturn(List.of(bearer));

        challengeService.recalculateChallengePoints();

        // Le défi rapporte 100 au département ; son porteur, lui, ne gagne rien.
        assertThat(bearer.getChallengePoints()).isZero();
    }

    @Test
    void onceWonTheChallengeCountsAsDoneForTheWholePromo() {
        // Le joueur n'a rien envoyé : c'est un coéquipier de promo qui l'a validé.
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(challenge));
        when(submissionRepository.findPromoSubmissionsForDepartment(departmentId))
                .thenReturn(List.of(submission("VALIDATED")));

        List<PlayerChallengeDto> challenges = challengeService.getPlayerChallenges("player-sub");

        assertThat(challenges).singleElement().satisfies(dto -> {
            assertThat(dto.promo()).isTrue();
            assertThat(dto.completed()).isTrue();
            assertThat(dto.canStillComplete()).isFalse();
        });
    }

    @Test
    void aProofBeingJudgedShowsAsPendingForTheWholePromo() {
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(challenge));
        when(submissionRepository.findPromoSubmissionsForDepartment(departmentId))
                .thenReturn(List.of(submission("PENDING")));

        List<PlayerChallengeDto> challenges = challengeService.getPlayerChallenges("player-sub");

        assertThat(challenges).singleElement().satisfies(dto -> {
            assertThat(dto.hasPendingSubmission()).isTrue();
            assertThat(dto.completed()).isFalse();
            assertThat(dto.canStillComplete()).isFalse();
        });
    }
}
