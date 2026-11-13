package fr.insastronaute.webapi.challenge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeCategory;
import fr.insastronaute.webapi.challenge.domain.ChallengeFavorite;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmissionMessage;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateResultDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeRecommendationReason;
import fr.insastronaute.webapi.challenge.dto.ChallengeSubmissionDto;
import fr.insastronaute.webapi.challenge.dto.AdminChallengeDto;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageCreateDto;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeFavoriteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionMessageRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.proof.domain.ProofType;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeCategoryRepository categoryRepository;

    @Mock
    private ChallengeFavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChallengeSubmissionRepository submissionRepository;

    @Mock
    private ChallengeSubmissionMessageRepository submissionMessageRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DepartmentVerificationLinkRepository verificationLinkRepository;

    @Mock
    private ProofStorageService proofStorageService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ModerationAuditService moderationAuditService;

    @Mock
    private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    @Mock private MediaReadAuditPublisher mediaReadAuditPublisher;
    @Mock private AdminActionJournal journal;
    @Mock private fr.insastronaute.webapi.friend.repository.FriendshipRepository friendshipRepository;
    @Mock private fr.insastronaute.webapi.challenge.repository.ChallengeGraceRepository graceRepository;
    @Mock private fr.insastronaute.webapi.challenge.service.ChallengeVisibilityService challengeVisibility;
    @Mock private LeaderboardService leaderboardService;

    private ChallengeService challengeService;

    private static final String ADMIN_SUB = "sub-admin";

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
                new AutoChallengeTypeRegistry(List.of(new PermanentQrScannedType(), new CountingType()), OBJECT_MAPPER),
                moderationAuditService,
                mediaReadAuditPublisher,
                autoChallengeTriggerPublisher,
                journal,
                challengeVisibility,
                friendshipRepository,
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),
                leaderboardService,
                graceRepository
        );
    }

    @Test
    void departmentSharesApplyCurrentLeaderboardCoefficients() {
        UUID challengeId = UUID.randomUUID();
        UUID departmentAId = UUID.randomUUID();
        UUID departmentBId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        challenge.setPointsReward(10);

        Department departmentA = new Department();
        departmentA.setId(departmentAId);
        departmentA.setShortName("A");
        departmentA.setColor("#112233");
        Department departmentB = new Department();
        departmentB.setId(departmentBId);
        departmentB.setShortName("B");
        departmentB.setColor("#AABBCC");

        AppUser userA = player(UUID.randomUUID());
        userA.setDepartment(departmentAId);
        AppUser userB = player(UUID.randomUUID());
        userB.setDepartment(departmentBId);

        ChallengeSubmission submissionA = new ChallengeSubmission();
        submissionA.setChallenge(challenge);
        submissionA.setUser(userA);
        submissionA.setStatus("VALIDATED");
        ChallengeSubmission submissionB = new ChallengeSubmission();
        submissionB.setChallenge(challenge);
        submissionB.setUser(userB);
        submissionB.setStatus("VALIDATED");

        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(submissionA, submissionB));
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(departmentA, departmentB));
        when(leaderboardService.currentDepartmentCoefficients()).thenReturn(Map.of(
                departmentAId, 1d,
                departmentBId, 2d
        ));
        when(leaderboardService.contributesToLeaderboards(any(AppUser.class))).thenReturn(true);

        var result = challengeService.getAllDepartmentShares().get(challengeId);

        assertThat(result).extracting(share -> share.department()).containsExactly("B", "A");
        assertThat(result).extracting(share -> share.percentage())
                .containsExactly(66.6667d, 33.3333d);
        assertThat(result).extracting(share -> share.color())
                .containsExactly("#AABBCC", "#112233");
    }

    @Test
    void recommendationsNameTheirReasonAndNeverRepeatAChallenge() {
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeVisibility.hidden()).thenReturn(false);

        UUID closingId = UUID.randomUUID();
        Challenge closing = challenge(closingId);
        closing.setTitle("Photo au coucher du soleil");
        closing.setCategory("Photos");
        closing.setEndDate(Instant.now().plus(java.time.Duration.ofHours(20)));
        when(challengeRepository.findChallengesForDepartment(any())).thenReturn(List.of(closing));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(submissionRepository.validatedChallengeIdsForUser(playerId)).thenReturn(List.of());
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());
        when(submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendIds(playerId)).thenReturn(List.of());

        var recommendations = challengeService.getRecommendations("player-sub", 6);

        assertThat(recommendations).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.challenge().id()).isEqualTo(closingId);
            // Une liste qui ne dit pas pourquoi elle propose ressemble à du hasard.
            assertThat(recommendation.reason()).isEqualTo(ChallengeRecommendationReason.ENDING_SOON);
            assertThat(recommendation.detail()).contains("Se termine");
        });
    }

    /**
     * Le défi du jour a déjà sa place, en haut de la page et en grand. Remis dans
     * les recommandations il se compte deux fois, et comme il ferme dans les
     * vingt-quatre heures, « se termine bientôt » le remonterait toujours au
     * détriment de tout le reste.
     */
    @Test
    void recommendationsLeaveTheDailyChallengeToItsOwnBlock() {
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeVisibility.hidden()).thenReturn(false);

        Challenge daily = challenge(UUID.randomUUID());
        daily.setCategory(ChallengeCategory.DAILY_NAME);
        daily.setEndDate(Instant.now().plus(java.time.Duration.ofHours(6)));

        // Un quotidien l'est autant s'il porte DAILY en catégorie secondaire.
        Challenge alsoDaily = challenge(UUID.randomUUID());
        alsoDaily.setCategory("Photos");
        alsoDaily.setExtraCategories(Set.of(ChallengeCategory.DAILY_NAME));
        alsoDaily.setEndDate(Instant.now().plus(java.time.Duration.ofHours(8)));

        UUID ordinaryId = UUID.randomUUID();
        Challenge ordinary = challenge(ordinaryId);
        ordinary.setCategory("Photos");
        ordinary.setEndDate(Instant.now().plus(java.time.Duration.ofHours(20)));

        when(challengeRepository.findChallengesForDepartment(any())).thenReturn(List.of(daily, alsoDaily, ordinary));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(submissionRepository.validatedChallengeIdsForUser(playerId)).thenReturn(List.of());
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());
        when(submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendIds(playerId)).thenReturn(List.of());

        var recommendations = challengeService.getRecommendations("player-sub", 6);

        assertThat(recommendations).extracting(recommendation -> recommendation.challenge().id())
                .containsExactly(ordinaryId);
    }

    /**
     * Un compteur à zéro ne distingue pas un défi entamé d'un défi ignoré : c'est
     * l'état de tous ceux qu'on n'a pas commencés, et le mettre en avant remplit
     * la liste de propositions au hasard. Le compteur entamé, lui, désigne un
     * effort déjà fourni qu'il suffit de finir.
     */
    @Test
    void recommendationsPushStartedAutomaticChallengesAndIgnoreUntouchedOnes() {
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeVisibility.hidden()).thenReturn(false);

        Challenge untouched = countingChallenge(UUID.randomUUID(), 0, 3);
        Challenge started = countingChallenge(UUID.randomUUID(), 2, 3);
        Challenge barelyStarted = countingChallenge(UUID.randomUUID(), 1, 10);
        List<Challenge> all = List.of(untouched, started, barelyStarted);

        when(challengeRepository.findChallengesForDepartment(any())).thenReturn(all);
        when(challengeRepository.findAllById(any())).thenReturn(all);
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(submissionRepository.validatedChallengeIdsForUser(playerId)).thenReturn(List.of());
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());
        when(submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendIds(playerId)).thenReturn(List.of());

        var recommendations = challengeService.getRecommendations("player-sub", 6);

        assertThat(recommendations).extracting(recommendation -> recommendation.challenge().id())
                // Le plus avancé d'abord, et jamais celui qui n'a pas bougé.
                .containsExactly(started.getId(), barelyStarted.getId());
        assertThat(recommendations.getFirst().reason()).isEqualTo(ChallengeRecommendationReason.ALMOST_DONE);
        assertThat(recommendations.getFirst().detail()).contains("2 / 3");
        assertThat(recommendations.getFirst().progressPercentage()).isEqualTo(66);
    }

    private static Challenge countingChallenge(UUID challengeId, int current, int target) {
        Challenge challenge = challenge(challengeId);
        challenge.setValidationMode("AUTO");
        challenge.setAutoChallengeType("COUNTING");
        challenge.setAutoChallengeParameters(
                OBJECT_MAPPER.createObjectNode().put("current", current).put("target", target));
        return challenge;
    }

    /**
     * Une preuve envoyée la veille de la fermeture et lue le lendemain est refusée
     * alors qu'il ne reste plus une minute pour la refaire : le joueur a fait le
     * défi, il a mal cadré sa photo, et il n'a aucun recours. La grâce lui rend ce
     * délai — sans quoi le refus est définitif pour une raison qui ne le regarde pas.
     */
    @Test
    void aGraceReopensAClosedChallengeForItsHolderOnly() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = textChallenge(challengeId);
        challenge.setEndDate(Instant.now().minus(java.time.Duration.ofHours(3)));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        // Sans grâce, la fenêtre fermée refuse.
        when(graceRepository.findActiveForUsersAndChallenge(any(), eq(challengeId), any())).thenReturn(List.of());
        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi n'est plus disponible.");

        // Avec, elle laisse passer.
        when(graceRepository.findActiveForUsersAndChallenge(any(), eq(challengeId), any()))
                .thenReturn(List.of(grace(challenge, player)));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId))
                .thenReturn(List.of());

        challengeService.submitChallenge("player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve"));

        verify(submissionRepository).save(any());
    }

    /** La grâce rend du temps ; elle n'ouvre pas en avance ce qui n'a pas commencé. */
    @Test
    void aGraceDoesNotOpenAChallengeThatHasNotStarted() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = textChallenge(challengeId);
        challenge.setStartDate(Instant.now().plus(java.time.Duration.ofDays(2)));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi n'est pas encore disponible.");

        verify(graceRepository, never()).findActiveForUsersAndChallenge(any(), any(), any());
    }

    /**
     * Un seul gracié ne fait pas entrer toute une équipe : la fenêtre se rouvre
     * pour ceux qui ont reçu la grâce, pas pour ceux qui les accompagnent.
     */
    @Test
    void aGraceDoesNotCoverTeammatesWhoDidNotGetOne() {
        UUID playerId = UUID.randomUUID();
        UUID mateId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        AppUser mate = player(mateId);
        Challenge challenge = textChallenge(challengeId);
        challenge.setMaxGroupSize(2);
        challenge.setEndDate(Instant.now().minus(java.time.Duration.ofHours(3)));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(any())).thenReturn(List.of(mate));
        when(graceRepository.findActiveForUsersAndChallenge(any(), eq(challengeId), any()))
                .thenReturn(List.of(grace(challenge, player)));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId,
                new ChallengeSubmissionDto("TEXT", null, List.of(mateId), "preuve", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi n'est plus disponible.");

        verify(submissionRepository, never()).save(any());
    }

    /** Rien à refaire tant qu'un modérateur n'a pas tranché : la grâce n'a pas d'objet. */
    @Test
    void aGraceIsRefusedOnASubmissionThatWasNotRejected() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setStatus("PENDING");
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setUser(player(UUID.randomUUID()));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.grantSubmissionGrace(submissionId, ADMIN_SUB, 24, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Une grâce ne s'accorde que sur une soumission refusée.");

        verify(graceRepository, never()).save(any());
    }

    /**
     * Le refus concernait toute l'équipe : gracier le seul auteur ne servirait à
     * rien, il ne peut pas reposter une preuve de groupe sans ses coéquipiers.
     */
    @Test
    void aGraceGoesToEveryMemberOfTheRejectedSubmissionAndTellsThem() {
        UUID submissionId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID mateId = UUID.randomUUID();
        AppUser author = player(authorId);
        AppUser mate = player(mateId);
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Pyramide humaine");

        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(submissionId);
        submission.setStatus("REJECTED");
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(Set.of(mate));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(userRepository.findByKeycloakSub(ADMIN_SUB)).thenReturn(Optional.empty());

        challengeService.grantSubmissionGrace(submissionId, ADMIN_SUB, null, "Recadre la photo.");

        verify(graceRepository, times(2)).save(any());
        // Une grâce que personne n'annonce expire avant que le joueur l'apprenne.
        for (UUID member : List.of(authorId, mateId)) {
            verify(notificationService).notifyUser(
                    eq(member), any(), eq(NotificationType.CHALLENGE_GRACE_GRANTED),
                    eq("Tu peux retenter ce défi"),
                    argThat(body -> body.contains("Pyramide humaine") && body.contains("24 h")
                            && body.contains("Recadre la photo.")),
                    anyMap(), eq("CHALLENGE_GRACE"), any());
            verify(journal).record(eq(ADMIN_SUB), eq(member),
                    eq(AdminActionType.CHALLENGE_GRACE_GRANTED), anyMap());
        }
    }

    @Test
    void aGraceCannotBeStretchedBeyondAWeek() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(submissionId);
        submission.setStatus("REJECTED");
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setUser(player(UUID.randomUUID()));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.grantSubmissionGrace(submissionId, ADMIN_SUB, 200, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le délai accordé doit tenir entre une heure et une semaine.");
    }

    private static fr.insastronaute.webapi.challenge.domain.ChallengeGrace grace(Challenge challenge, AppUser user) {
        var grace = new fr.insastronaute.webapi.challenge.domain.ChallengeGrace();
        grace.setChallenge(challenge);
        grace.setUser(user);
        grace.setExpiresAt(Instant.now().plus(java.time.Duration.ofHours(24)));
        return grace;
    }

    /**
     * Une preuve de défi est une photo de soi, souvent avec d'autres, envoyée pour
     * qu'un modérateur la regarde. Cet examen fini et le défi validé, elle n'a plus
     * d'usage — et rien ne permettait de demander qu'elle parte.
     */
    @Test
    void proofFilesAreErasedOnValidationWhenThePlayerAskedForIt() {
        UUID submissionId = UUID.randomUUID();
        AppUser author = player(UUID.randomUUID());
        author.setPurgeProofsAfterValidation(true);

        ChallengeSubmission submission = decidableSubmission(submissionId, author, challenge(UUID.randomUUID()));
        when(proofStorageService.parseProofFiles("[\"cle\"]")).thenReturn(List.of("cle"));

        challengeService.decideSubmission(submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), "VALIDATED", null, 0);

        verify(proofStorageService).purgeSubmittedProof("[\"cle\"]");
        assertThat(submission.getProofData()).isEqualTo("[]");
        // Sans cette date, une preuve effacée ressemblerait à une preuve qui n'a
        // jamais eu de fichier, et l'écran de modération ne saurait pas quoi dire.
        assertThat(submission.getProofMediaPurgedAt()).isNotNull();
    }

    /** Une preuve refusée peut encore être discutée : rien ne part avant la validation. */
    @Test
    void proofFilesSurviveARejectionEvenWhenThePlayerAskedForErasure() {
        UUID submissionId = UUID.randomUUID();
        AppUser author = player(UUID.randomUUID());
        author.setPurgeProofsAfterValidation(true);

        ChallengeSubmission submission = decidableSubmission(submissionId, author, challenge(UUID.randomUUID()));

        challengeService.decideSubmission(submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), "REJECTED", "Flou", 0);

        verify(proofStorageService, never()).purgeSubmittedProof(any());
        assertThat(submission.getProofMediaPurgedAt()).isNull();
    }

    /** Sans demande, on garde : c'est ce qui permet de revenir sur une validation contestée. */
    @Test
    void proofFilesStayWhenNobodyAskedForTheirErasure() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = decidableSubmission(
                submissionId, player(UUID.randomUUID()), challenge(UUID.randomUUID()));

        challengeService.decideSubmission(submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), "VALIDATED", null, 0);

        verify(proofStorageService, never()).purgeSubmittedProof(any());
        assertThat(submission.getProofMediaPurgedAt()).isNull();
    }

    /**
     * La photo montre toute l'équipe : le refus de la laisser traîner ne se partage
     * pas en parts, la demande d'un seul membre efface la preuve entière.
     */
    @Test
    void oneTeammatesRequestIsEnoughToEraseAGroupProof() {
        UUID submissionId = UUID.randomUUID();
        AppUser mate = player(UUID.randomUUID());
        mate.setPurgeProofsAfterValidation(true);

        ChallengeSubmission submission = decidableSubmission(
                submissionId, player(UUID.randomUUID()), challenge(UUID.randomUUID()));
        submission.setParticipants(Set.of(mate));
        when(proofStorageService.parseProofFiles("[\"cle\"]")).thenReturn(List.of("cle"));

        challengeService.decideSubmission(submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), "VALIDATED", null, 0);

        verify(proofStorageService).purgeSubmittedProof("[\"cle\"]");
    }

    // ── Quatre reglages ─────────────────────────────────────────────────────────

    /**
     * Un malus ne mord sous zero que si le defi l'autorise.
     *
     * <p>Sur un defi ordinaire, un chiffre mal saisi retirerait des points a qui n'a
     * rien demande : la garde reste, et c'est le defi qui la leve.</p>
     */
    @Test
    void aMalusCannotGoBelowZeroUnlessTheChallengeAllowsIt() {
        UUID submissionId = UUID.randomUUID();
        AppUser author = player(UUID.randomUUID());
        Challenge ordinary = challenge(UUID.randomUUID());
        ChallengeSubmission submission = decidableSubmission(submissionId, author, ordinary);
        submission.setStatus("VALIDATED");

        assertThatThrownBy(() -> challengeService.adjustValidatedSubmissionBonusPoints(
                submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), -50, "sanction"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aChallengeThatAllowsLossAcceptsANegativeTotal() {
        UUID submissionId = UUID.randomUUID();
        AppUser author = player(UUID.randomUUID());
        Challenge sanctionable = challenge(UUID.randomUUID());
        sanctionable.setAllowNegativeReward(true);
        ChallengeSubmission submission = decidableSubmission(submissionId, author, sanctionable);
        submission.setStatus("VALIDATED");

        challengeService.adjustValidatedSubmissionBonusPoints(submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), -50, "sanction");

        // Dix de recompense, cinquante de malus : le solde du defi est negatif.
        assertThat(submission.getBonusPoints()).isEqualTo(-50);
    }

    /**
     * Une preuve declaree sensible par son auteur vaut un defi sensible.
     *
     * <p>Celui qui envoie la photo est parfois seul a savoir ce qu'elle montre : ses
     * fichiers partent apres validation, comme sur un defi que l'organisation a
     * declare sensible pour tout le monde.</p>
     */
    @Test
    void aSubmissionItsAuthorCalledSensitiveLosesItsFilesOnValidation() {
        UUID submissionId = UUID.randomUUID();
        AppUser author = player(UUID.randomUUID());
        ChallengeSubmission submission = decidableSubmission(submissionId, author, challenge(UUID.randomUUID()));
        submission.setSensitive(true);
        when(proofStorageService.parseProofFiles("[\"cle\"]")).thenReturn(List.of("cle"));

        challengeService.decideSubmission(submissionId, ADMIN_SUB, Set.of("ROLE_ADMIN"), "VALIDATED", null, 0);

        verify(proofStorageService).purgeSubmittedProof("[\"cle\"]");
        assertThat(submission.getProofData()).isEqualTo("[]");
        assertThat(submission.getProofMediaPurgedAt()).isNotNull();
    }

    /**
     * Effacer les preuves d'un defi epargne celles qui attendent leur tour.
     *
     * <p>Sans sa photo, une preuve en attente devient injugeable : elle resterait dans
     * la file sans que personne ne comprenne pourquoi elle n'avance plus.</p>
     */
    @Test
    void purgingAChallengeSpareTheSubmissionsStillWaitingForADecision() {
        UUID challengeId = UUID.randomUUID();
        Challenge target = challenge(challengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(target));

        ChallengeSubmission validated = new ChallengeSubmission();
        validated.setId(UUID.randomUUID());
        validated.setStatus("VALIDATED");
        validated.setProofData("[\"validee\"]");
        ChallengeSubmission rejected = new ChallengeSubmission();
        rejected.setId(UUID.randomUUID());
        rejected.setStatus("REJECTED");
        rejected.setProofData("[\"refusee\"]");
        when(submissionRepository.findByChallengeIdAndStatusIn(challengeId, List.of("VALIDATED", "REJECTED")))
                .thenReturn(List.of(validated, rejected));
        when(proofStorageService.parseProofFiles("[\"validee\"]")).thenReturn(List.of("validee"));
        when(proofStorageService.parseProofFiles("[\"refusee\"]")).thenReturn(List.of("refusee"));

        int purged = challengeService.purgeChallengeProofs(challengeId, ADMIN_SUB);

        assertThat(purged).isEqualTo(2);
        assertThat(validated.getProofData()).isEqualTo("[]");
        assertThat(rejected.getProofData()).isEqualTo("[]");
        // La requete elle-meme exclut les preuves en attente : le service ne les voit pas.
        verify(submissionRepository).findByChallengeIdAndStatusIn(challengeId, List.of("VALIDATED", "REJECTED"));
    }

    /**
     * Le registre refuse toute cle contenant « proof », « token », « cookie », « range »
     * ou « url » : la garde existe pour qu'aucune URL signee n'y atterrisse.
     *
     * <p>Une cle nommee « proofsPurged » la declenchait, et l'effacement finissait en
     * erreur 500 -- apres avoir supprime les fichiers du stockage, que l'annulation de
     * la transaction ne ramenait pas. Les tests simulaient le registre, donc aucun ne
     * le voyait : celui-ci passe les metadonnees par la vraie garde.</p>
     */
    @Test
    void theJournalOfAPurgeUsesKeysTheRegisterAccepts() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge(challengeId)));
        ChallengeSubmission validated = new ChallengeSubmission();
        validated.setId(UUID.randomUUID());
        validated.setStatus("VALIDATED");
        validated.setProofData("[\"cle\"]");
        when(submissionRepository.findByChallengeIdAndStatusIn(challengeId, List.of("VALIDATED", "REJECTED")))
                .thenReturn(List.of(validated));
        when(proofStorageService.parseProofFiles("[\"cle\"]")).thenReturn(List.of("cle"));

        challengeService.purgeChallengeProofs(challengeId, ADMIN_SUB);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(journal).recordGlobal(eq(ADMIN_SUB), eq(AdminActionType.CHALLENGE_PROOFS_PURGED), details.capture());
        assertThatCode(() -> AdminActionJournal.rejectSensitiveDetailKeys(details.getValue()))
                .doesNotThrowAnyException();
        assertThat(details.getValue()).containsEntry("purgedMedia", 1);
    }

    /** Une preuve deja effacee ne se recompte pas : l'effacement est idempotent. */
    @Test
    void purgingTwiceErasesNothingTheSecondTime() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge(challengeId)));
        ChallengeSubmission already = new ChallengeSubmission();
        already.setId(UUID.randomUUID());
        already.setStatus("VALIDATED");
        already.setProofData("[]");
        already.setProofMediaPurgedAt(Instant.now());
        when(submissionRepository.findByChallengeIdAndStatusIn(challengeId, List.of("VALIDATED", "REJECTED")))
                .thenReturn(List.of(already));

        assertThat(challengeService.purgeChallengeProofs(challengeId, ADMIN_SUB)).isZero();
        verify(proofStorageService, never()).purgeSubmittedProof(any());
    }

    private ChallengeSubmission decidableSubmission(UUID submissionId, AppUser author, Challenge challenge) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(submissionId);
        submission.setStatus("PENDING");
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setProofType("MEDIA");
        submission.setProofData("[\"cle\"]");
        // Juger demande d'avoir pris la preuve en review : c'est la regle du
        // verrou, pas un detail de montage.
        submission.setReviewLockedBy(ADMIN_SUB);
        submission.setReviewStartedAt(Instant.now());
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        return submission;
    }

    @Test
    void hiddenChallengesLeaveThePageEmptyAndRefuseSubmissions() {
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeVisibility.hidden()).thenReturn(true);

        assertThat(challengeService.getPlayerChallenges("player-sub")).isEmpty();

        // Masqué ne veut pas dire seulement invisible : un défi dont on garde
        // l'adresse ne doit pas rester soumettable.
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge(challengeId)));
        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", null, null, "preuve", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Les défis sont momentanément indisponibles.");
    }

    @Test
    void getAllChallengesMapsTargetDepartmentsToIds() {
        UUID challengeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID prerequisiteId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        Department department = new Department();
        department.setId(departmentId);
        challenge.setTargetDepartments(java.util.Set.of(department));
        Challenge prerequisite = challenge(prerequisiteId);
        challenge.setPrerequisiteChallenges(java.util.Set.of(prerequisite));

        when(challengeRepository.findAll()).thenReturn(List.of(challenge));

        List<AdminChallengeDto> result = challengeService.getAllChallenges();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(challengeId);
        assertThat(result.getFirst().targetDepartmentIds()).containsExactly(departmentId);
        assertThat(result.getFirst().prerequisiteChallengeIds()).containsExactly(prerequisiteId);
    }

    @Test
    void aChallengeCanCarrySeveralCategories() {
        // Un défi photo qui est aussi un défi de groupe doit se trouver sous les deux
        // onglets. La tête de liste reste la principale : c'est elle qu'on affiche.
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(categoryRepository.existsById("groupe")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo de groupe", "Description", 10, null, List.of("photo", "groupe"),
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);

        AdminChallengeDto result = challengeService.createChallenge(dto);

        assertThat(result.category()).isEqualTo("photo");
        assertThat(result.categories()).containsExactly("photo", "groupe");
    }

    @Test
    void aChallengeWithoutTheListKeepsItsSingleCategory() {
        // Un client qui ne connaît pas encore le champ continue d'envoyer `category`
        // seul, et rien ne doit changer pour lui.
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo", "Description", 10, "photo", null,
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);

        AdminChallengeDto result = challengeService.createChallenge(dto);

        assertThat(result.categories()).containsExactly("photo");
    }

    @Test
    void anInventedCategoryIsRefusedWhereverItSitsInTheList() {
        // Une catégorie inventée doit se voir à l'enregistrement, pas à l'affichage.
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(categoryRepository.existsById("inconnue")).thenReturn(false);

        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo", "Description", 10, null, List.of("photo", "inconnue"),
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void automaticProgressIsEmptyForAModeratedChallenge() {
        // Un défi validé par un modérateur n'a pas de compteur : il n'y a rien à
        // afficher, et inventer un zéro laisserait croire à un défi automatique.
        UUID challengeId = UUID.randomUUID();
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        Challenge challenge = challenge(challengeId);
        challenge.setValidationMode("ADMIN");
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThat(challengeService.autoChallengeProgress("player-sub", challengeId)).isNull();
    }

    @Test
    void automaticProgressStaysSilentBeforeTheChallengeOpens() {
        // Un defi auto peut etre visible avant son ouverture : par une date de
        // parution anterieure, ou parce qu'il est quotidien. Sa carte annoncait
        // alors une progression et promettait de se valider tout seul, alors
        // qu'aucune attribution ne peut tomber avant la date de debut. Barre
        // pleine et rien qui vienne : le defi passe pour casse.
        UUID challengeId = UUID.randomUUID();
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        Challenge challenge = countingChallenge(challengeId, 10, 10);
        challenge.setStartDate(Instant.now().plus(Duration.ofDays(7)));
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThat(challengeService.autoChallengeProgress("player-sub", challengeId)).isNull();
    }

    @Test
    void automaticProgressIsAnnouncedOnceTheChallengeHasOpened() {
        // Le garde-fou porte sur la date de debut, pas sur le type : un defi ouvert
        // continue de dire ou en est le joueur.
        UUID challengeId = UUID.randomUUID();
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        Challenge challenge = countingChallenge(challengeId, 6, 10);
        challenge.setStartDate(Instant.now().minus(Duration.ofDays(1)));
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThat(challengeService.autoChallengeProgress("player-sub", challengeId))
                .isNotNull()
                .satisfies(progress -> assertThat(progress.current()).isEqualTo(6));
    }

    @Test
    void automaticProgressSurvivesAMisconfiguredPhrase() {
        // Une phrase illisible ne doit pas casser l'affichage du défi : on n'annonce
        // pas d'avancement, c'est tout.
        UUID challengeId = UUID.randomUUID();
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        Challenge challenge = challenge(challengeId);
        challenge.setValidationMode("AUTO");
        // Un type que le registre ne connaît pas : c'est ce que devient une phrase
        // dont le type a été retiré du serveur.
        challenge.setAutoChallengeType("TYPE_DISPARU");
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThat(challengeService.autoChallengeProgress("player-sub", challengeId)).isNull();
    }

    @Test
    void createCardChallengeWithoutCategoryFailsAsABadRequestNotAServerError() {
        // Le panneau en ligne ne sait pas encore afficher le champ « catégorie de
        // carte » : il envoie donc des paramètres sans elle. C'est une requête
        // invalide, et elle doit se voir comme telle — un 500 laisse croire à une
        // panne du serveur et fait chercher au mauvais endroit.
        com.fasterxml.jackson.databind.node.ObjectNode parameters = OBJECT_MAPPER.createObjectNode();
        parameters.put("count", 3);
        parameters.put("collectionType", "PHYSICAL");
        ChallengeCreateDto dto = challengeDto("AUTO", "CARD_CATEGORY_OBTAINED", parameters);
        // Aucune catégorie de défi à résoudre : le refus tombe avant.

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminChallengePageFiltersBeforePaginationAndIgnoresAccents() {
        Challenge summer = challenge(UUID.randomUUID());
        summer.setTitle("Défi Été");
        summer.setCategory("photo");
        summer.setValidationMode("ADMIN");
        Challenge winter = challenge(UUID.randomUUID());
        winter.setTitle("Mission hiver");
        winter.setCategory("photo");
        winter.setValidationMode("ADMIN");
        when(challengeRepository.findAll()).thenReturn(List.of(summer, winter));

        var result = challengeService.getChallengesPage("ete", "draft", "photo", "ADMIN", "START_DATE_DESC", 0, 25);

        assertThat(result.content()).extracting(AdminChallengeDto::title).containsExactly("Défi Été");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void createAutomaticChallengePreservesAutomaticConfiguration() {
        ChallengeCreateDto dto = challengeDto(
                "AUTO",
                "PERMANENT_QR_SCANNED",
                OBJECT_MAPPER.createObjectNode()
        );
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminChallengeDto result = challengeService.createChallenge(dto);

        assertThat(result.autoChallengeType()).isEqualTo("PERMANENT_QR_SCANNED");
        assertThat(result.autoChallengeParameters()).isEmpty();
    }

    @Test
    void createChallengeAcceptsAnEmptyDescription() {
        // La description est facultative : ni le DTO ni la colonne — un TEXT sans
        // NOT NULL depuis V4__challenges_create.sql — ne l'exigent. Ce test le fige,
        // pour qu'un futur @NotBlank ne réintroduise pas la contrainte que
        // l'interface d'administration vient d'abandonner.
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        for (String description : new String[] { null, "", "   " }) {
            ChallengeCreateDto dto = new ChallengeCreateDto(
                    "Photo de groupe", description, 10, "photo", null,
                    null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);

            AdminChallengeDto result = challengeService.createChallenge(dto);

            assertThat(result.title()).isEqualTo("Photo de groupe");
        }
    }

    @Test
    void createDailyChallengeRequiresEndDate() {
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Bilan quotidien", "Description", 10, ChallengeCategory.DAILY_NAME, null,
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);
        when(categoryRepository.existsById(ChallengeCategory.DAILY_NAME)).thenReturn(true);

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date de fin requise pour un défi du jour.");

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void updateAutomaticChallengePreservesAutomaticConfiguration() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        ChallengeCreateDto dto = challengeDto(
                "AUTO",
                "PERMANENT_QR_SCANNED",
                OBJECT_MAPPER.createObjectNode()
        );
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);

        AdminChallengeDto result = challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        assertThat(result.autoChallengeType()).isEqualTo("PERMANENT_QR_SCANNED");
        assertThat(result.autoChallengeParameters()).isEmpty();
    }

    /**
     * Passer un defi en automatique vide sa file : une preuve en attente bloquerait
     * l'attribution de son auteur, et une preuve de groupe confirmee apres coup serait
     * validee sans que personne ne l'ait regardee.
     */
    @Test
    void switchingToAutomaticRetiresTheQueueWithAnExplanation() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        AppUser alice = named("Alice");
        AppUser bruno = named("Bruno");
        AppUser chloe = named("Chloe");

        ChallengeSubmission pending = new ChallengeSubmission();
        pending.setId(UUID.randomUUID());
        pending.setChallenge(challenge);
        pending.setUser(alice);
        pending.setStatus("PENDING");
        pending.setReviewLockedBy("sub-moderateur");
        pending.setReviewStartedAt(Instant.parse("2026-09-27T10:00:00Z"));
        pending.setReviewExpiresAt(Instant.parse("2026-09-27T10:10:00Z"));

        ChallengeSubmission waitingGroup = new ChallengeSubmission();
        waitingGroup.setId(UUID.randomUUID());
        waitingGroup.setChallenge(challenge);
        waitingGroup.setUser(bruno);
        waitingGroup.setParticipants(Set.of(chloe));
        waitingGroup.setStatus("WAITING_CONFIRMATION");

        ChallengeCreateDto dto = challengeDto("AUTO", "PERMANENT_QR_SCANNED", OBJECT_MAPPER.createObjectNode());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        // Les preuves validees ne sont pas demandees : elles gardent leurs points.
        when(submissionRepository.findByChallengeIdAndStatusIn(eq(challengeId), argThat(statuses ->
                statuses.size() == 2 && statuses.containsAll(List.of("PENDING", "WAITING_CONFIRMATION")))))
                .thenReturn(List.of(pending, waitingGroup));

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        assertThat(List.of(pending, waitingGroup)).allSatisfy(submission -> {
            assertThat(submission.getStatus()).isEqualTo("REJECTED");
            assertThat(submission.getModerationComment())
                    .isEqualTo("Ce défi se valide désormais automatiquement : ta preuve n'est plus nécessaire.");
            assertThat(submission.getValidatedAt()).isNull();
        });
        assertThat(pending.getReviewLockedBy()).isNull();
        assertThat(pending.getReviewExpiresAt()).isNull();

        for (AppUser member : List.of(alice, bruno, chloe)) {
            verify(notificationService).notifyUser(
                    eq(member.getId()),
                    org.mockito.ArgumentMatchers.isNull(),
                    eq(NotificationType.CHALLENGE_SUBMISSION_REJECTED),
                    eq("Défi devenu automatique"),
                    // Le titre tel que l'enregistrement vient de l'ecrire.
                    eq("\"Photo equipe\" se valide désormais automatiquement : ta preuve n'est plus nécessaire."),
                    anyMap(),
                    eq("CHALLENGE_SUBMISSION"),
                    any(UUID.class));
        }
        // Ce n'est pas un refus de moderateur : ni registre, ni message en son nom.
        verify(moderationAuditService, never()).record(any(), any(), any(), any(), anyMap());
        verify(submissionMessageRepository, never()).save(any());
    }

    @Test
    void editingAnAlreadyAutomaticChallengeKeepsItsSubmissions() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        challenge.setValidationMode("AUTO");
        challenge.setAutoChallengeType("PERMANENT_QR_SCANNED");
        challenge.setAutoChallengeParameters(OBJECT_MAPPER.createObjectNode());
        ChallengeCreateDto dto = challengeDto("AUTO", "PERMANENT_QR_SCANNED", OBJECT_MAPPER.createObjectNode());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        verify(submissionRepository, never()).findByChallengeIdAndStatusIn(any(), any());
    }

    @Test
    void editingAManualChallengeKeepsItsQueue() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        ChallengeCreateDto dto = challengeDto("ADMIN", null, OBJECT_MAPPER.createObjectNode());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        verify(submissionRepository, never()).findByChallengeIdAndStatusIn(any(), any());
    }

    @Test
    void createAutomaticChallengeRejectsMissingType() {
        ChallengeCreateDto dto = challengeDto("AUTO", null, OBJECT_MAPPER.createObjectNode());

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class)
                .satisfies(exception -> assertThat(((BadRequestException) exception).getFieldErrors())
                        .extracting(fr.insastronaute.webapi.common.web.ApiErrorResponse.FieldViolation::field)
                        .containsExactly("autoChallengeType"));

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void createAutomaticChallengeRejectsUnknownType() {
        ChallengeCreateDto dto = challengeDto("AUTO", "UNKNOWN", OBJECT_MAPPER.createObjectNode());

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class)
                .satisfies(exception -> assertThat(((BadRequestException) exception).getFieldErrors())
                        .extracting(fr.insastronaute.webapi.common.web.ApiErrorResponse.FieldViolation::field)
                        .containsExactly("autoChallengeType"));

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void createAutomaticChallengeRejectsInvalidParameters() {
        ChallengeCreateDto dto = challengeDto(
                "AUTO",
                "PERMANENT_QR_SCANNED",
                OBJECT_MAPPER.createObjectNode().put("unexpected", true)
        );

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class)
                .satisfies(exception -> assertThat(((BadRequestException) exception).getFieldErrors())
                        .extracting(fr.insastronaute.webapi.common.web.ApiErrorResponse.FieldViolation::field)
                        .containsExactly("autoChallengeParameters"));

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void switchingChallengeToAdminClearsAutomaticConfiguration() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        challenge.setValidationMode("AUTO");
        challenge.setAutoChallengeType("PERMANENT_QR_SCANNED");
        challenge.setAutoChallengeParameters(OBJECT_MAPPER.createObjectNode());
        ChallengeCreateDto dto = challengeDto("ADMIN", null, (java.util.Map<String, Object>) null);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);

        AdminChallengeDto result = challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        assertThat(result.autoChallengeType()).isNull();
        assertThat(result.autoChallengeParameters()).isNull();
        assertThat(challenge.getAutoChallengeType()).isNull();
        assertThat(challenge.getAutoChallengeParameters()).isNull();
    }

    @Test
    void getPlayerChallengesHidesChallengesWithIncompletePrerequisites() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID lockedChallengeId = UUID.randomUUID();
        UUID unlockedChallengeId = UUID.randomUUID();
        UUID prerequisiteId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge prerequisite = challenge(prerequisiteId);
        Challenge lockedChallenge = challenge(lockedChallengeId);
        lockedChallenge.setPrerequisiteChallenges(java.util.Set.of(prerequisite));
        Challenge unlockedChallenge = challenge(unlockedChallengeId);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findChallengesForDepartment(departmentId))
                .thenReturn(List.of(lockedChallenge, unlockedChallenge));
        // Groupee : les defis deja valides sont lus une fois pour la page.
        when(submissionRepository.validatedChallengeIdsForUser(playerId)).thenReturn(List.of());
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(unlockedChallengeId);
    }

    /**
     * Le departement force gouverne aussi la liste des defis - meme defaut que
     * la page des events, et meme correctif. Sans lui, la page repond avec les
     * defis du vrai departement sans rien dire, et le voyage dans le temps
     * passe pour casse.
     */
    @Test
    void aForcedDepartmentDrivesTheChallengesAPlayerSees() {
        UUID playerId = UUID.randomUUID();
        UUID realDepartment = UUID.randomUUID();
        UUID forcedDepartment = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(realDepartment);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findChallengesForDepartment(forcedDepartment)).thenReturn(List.of());
        TimeTravelContext.setForcedDepartmentId(forcedDepartment);

        challengeService.getPlayerChallenges("player-sub");

        verify(challengeRepository).findChallengesForDepartment(forcedDepartment);
    }

    @AfterEach
    void clearTimeTravel() {
        TimeTravelContext.clear();
    }

    @Test
    void getsAvailableChallengesForAnAdminSelectedUser() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge challenge = challenge(challengeId);

        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(challenge));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        List<fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto> result = challengeService.getPlayerChallenges(playerId);

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(challengeId);
    }

    @Test
    void toPlayerChallengeDtoIncludesStartDateAndEndDate() {
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));

        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        Instant start = Instant.parse("2026-09-08T08:00:00Z");
        Instant end = Instant.parse("2026-09-08T22:00:00Z");
        challenge.setStartDate(start);
        challenge.setEndDate(end);
        challenge.setPublishedAt(Instant.parse("2026-09-01T00:00:00Z"));

        when(challengeRepository.findChallengesForDepartment(player.getDepartment())).thenReturn(List.of(challenge));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());

        List<PlayerChallengeDto> dtos = challengeService.getPlayerChallenges(playerId);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).startDate()).isEqualTo(start);
        assertThat(dtos.get(0).endDate()).isEqualTo(end);
    }

    @Test
    void dailyChallengeWithoutPublishedAtIsVisibleOnTheEveAtMidnightParis() {
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));

        // Défi prévu pour le mardi 8 septembre 2026 à 08:00 UTC
        UUID challengeTuesdayId = UUID.randomUUID();
        Challenge challengeTuesday = challenge(challengeTuesdayId);
        challengeTuesday.setCategory(ChallengeCategory.DAILY_NAME);
        challengeTuesday.setStartDate(Instant.parse("2026-09-08T08:00:00Z"));
        challengeTuesday.setEndDate(Instant.parse("2026-09-08T22:00:00Z"));

        // Défi prévu pour le mercredi 9 septembre 2026 à 08:00 UTC
        UUID challengeWednesdayId = UUID.randomUUID();
        Challenge challengeWednesday = challenge(challengeWednesdayId);
        challengeWednesday.setCategory(ChallengeCategory.DAILY_NAME);
        challengeWednesday.setStartDate(Instant.parse("2026-09-09T08:00:00Z"));
        challengeWednesday.setEndDate(Instant.parse("2026-09-09T22:00:00Z"));

        when(challengeRepository.findChallengesForDepartment(player.getDepartment()))
                .thenReturn(List.of(challengeTuesday, challengeWednesday));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId)).thenReturn(List.of());

        // Simule le lundi 7 septembre 2026 à 10:00 UTC (J1)
        TimeTravelContext.setForcedTime(Instant.parse("2026-09-07T10:00:00Z"));
        try {
            List<PlayerChallengeDto> dtos = challengeService.getPlayerChallenges(playerId);
            // Le défi du mardi (J2) doit être visible (veille), mais pas celui du mercredi (J3)
            assertThat(dtos).extracting(PlayerChallengeDto::id).containsExactly(challengeTuesdayId);
            // Le défi du mardi n'est pas encore actif pour soumission
            assertThat(dtos.get(0).canStillComplete()).isFalse();
        } finally {
            TimeTravelContext.clear();
        }
    }

    @Test
    void getPlayerChallengesMarksCompletedAndFavoriteChallenges() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge challenge = challenge(challengeId);
        ChallengeSubmission validatedSubmission = submission("VALIDATED", challenge);
        ChallengeFavorite favorite = favorite(player, challenge);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of(favorite));
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(challenge));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of(validatedSubmission));

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().completed()).isTrue();
        assertThat(result.getFirst().favorite()).isTrue();
        assertThat(result.getFirst().canStillComplete()).isFalse();
    }

    @Test
    void getPlayerChallengesKeepsAChallengeWhoseWindowIsClosed() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge closed = challenge(challengeId);
        closed.setEndDate(Instant.now().minusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(closed));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(challengeId);
        assertThat(result.getFirst().canStillComplete()).isFalse();
    }

    /**
     * Un défi annoncé mais pas encore ouvert reste visible, sans être complétable.
     *
     * <p>C'est la liste « bientôt », et elle existe toujours -- mais elle se demande
     * désormais. Ce cas affirmait la même chose sans date de parution : un défi à venir
     * s'affichait par ce seul fait, et rien ne permettait de programmer une ouverture sans
     * l'annoncer du même coup. Le défaut a changé, l'annonce reste possible.</p>
     */
    @Test
    void getPlayerChallengesKeepsAnAnnouncedChallengeThatHasNotOpenedYet() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge notOpenedYet = challenge(challengeId);
        notOpenedYet.setStartDate(Instant.now().plusSeconds(3600));
        notOpenedYet.setPublishedAt(Instant.now().minusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(notOpenedYet));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(challengeId);
        assertThat(result.getFirst().canStillComplete()).isFalse();
    }

    @Test
    void getPlayerChallengesLetsAnOpenChallengeBeCompleted() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge open = challenge(challengeId);
        open.setStartDate(Instant.now().minusSeconds(3600));
        open.setEndDate(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(open));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result.getFirst().canStillComplete()).isTrue();
    }

    @Test
    void aChallengeOutsideItsWindowCanBeAddedToFavorites() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge closed = challenge(challengeId);
        closed.setEndDate(Instant.now().minusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(closed));
        when(favoriteRepository.existsByUserIdAndChallengeId(playerId, challengeId)).thenReturn(false);

        challengeService.addFavoriteChallenge("player-sub", challengeId);

        verify(favoriteRepository).save(any());
    }

    @Test
    void aChallengeOutsideItsWindowStaysIneligibleForAnAutomaticAward() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge closed = challenge(UUID.randomUUID());
        closed.setEndDate(Instant.now().minusSeconds(3600));
        Challenge notOpenedYet = challenge(UUID.randomUUID());
        notOpenedYet.setStartDate(Instant.now().plusSeconds(3600));

        assertThat(challengeService.isPlayerEligibleForChallenge(player, closed)).isFalse();
        assertThat(challengeService.isPlayerEligibleForChallenge(player, notOpenedYet)).isFalse();
        assertThat(challengeService.isOpenForAutomaticAward(closed)).isFalse();
        assertThat(challengeService.isOpenForAutomaticAward(notOpenedYet)).isFalse();
    }

    @Test
    void aDraftWithoutBothDatesIsNotOpenForAnAutomaticAward() {
        // Le panneau appelle brouillon un défi à qui manque une date. Le backend le
        // tenait pour ouvert, et l'attribuait dès sa création.
        UUID departmentId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        player.setDepartment(departmentId);
        Challenge noDates = challenge(UUID.randomUUID());
        Challenge noEnd = challenge(UUID.randomUUID());
        noEnd.setStartDate(Instant.now().minusSeconds(3600));
        Challenge noStart = challenge(UUID.randomUUID());
        noStart.setEndDate(Instant.now().plusSeconds(3600));

        for (Challenge draft : List.of(noDates, noEnd, noStart)) {
            assertThat(challengeService.isOpenForAutomaticAward(draft)).isFalse();
            assertThat(challengeService.isPlayerEligibleForChallenge(player, draft)).isFalse();
        }
        verify(challengeVisibility, never()).hidden();
    }

    @Test
    void anOpenAutomaticChallengeClosesWhileChallengesAreHidden() {
        Challenge open = challenge(UUID.randomUUID());
        open.setStartDate(Instant.now().minusSeconds(3600));
        open.setEndDate(Instant.now().plusSeconds(3600));
        when(challengeVisibility.hidden()).thenReturn(false, true);

        assertThat(challengeService.isOpenForAutomaticAward(open)).isTrue();
        assertThat(challengeService.isOpenForAutomaticAward(open)).isFalse();
    }

    @Test
    void aScheduledChallengeInItsWindowStaysEligibleForAnAutomaticAward() {
        AppUser player = player(UUID.randomUUID());
        player.setDepartment(UUID.randomUUID());
        Challenge open = challenge(UUID.randomUUID());
        open.setStartDate(Instant.now().minusSeconds(3600));
        open.setEndDate(Instant.now().plusSeconds(3600));

        assertThat(challengeService.isPlayerEligibleForChallenge(player, open)).isTrue();
    }

    @Test
    void getPlayerChallengesHidesAChallengeThatHasNotBeenPublishedYet() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        UUID unpublishedId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge published = challenge(publishedId);
        published.setPublishedAt(Instant.now().minusSeconds(3600));
        Challenge unpublished = challenge(unpublishedId);
        unpublished.setPublishedAt(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId))
                .thenReturn(List.of(published, unpublished));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(publishedId);
    }

    /**
     * Sans parution renseignée, c'est l'activation qui décide.
     *
     * <p>Un défi programmé pour dans trois jours s'affichait dès sa création. Il fallait
     * penser à remplir la parution pour qu'il ne soit pas annoncé avant l'heure, et
     * l'oublier ne levait rien : le défi apparaissait simplement trop tôt.</p>
     */
    @Test
    void aChallengeThatHasNotOpenedYetIsNotAnnouncedEither() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID openId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge open = challenge(openId);
        open.setStartDate(Instant.now().minusSeconds(3600));
        Challenge notOpenedYet = challenge(UUID.randomUUID());
        notOpenedYet.setStartDate(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId))
                .thenReturn(List.of(open, notOpenedYet));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(openId);
    }

    /**
     * Une parution renseignée prime sur l'activation, dans les deux sens : elle sert à
     * annoncer plus tôt, ce que le défaut ne permet pas.
     */
    @Test
    void anExplicitPublicationDateWinsOverTheOpeningDate() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID announcedId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);

        // Ouvre demain, mais annoncé depuis hier : il doit se voir.
        Challenge announced = challenge(announcedId);
        announced.setStartDate(Instant.now().plusSeconds(3600));
        announced.setPublishedAt(Instant.now().minusSeconds(3600));

        // Ouvert depuis hier, mais annoncé pour demain : il doit rester caché.
        Challenge embargoed = challenge(UUID.randomUUID());
        embargoed.setStartDate(Instant.now().minusSeconds(3600));
        embargoed.setPublishedAt(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId))
                .thenReturn(List.of(announced, embargoed));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(announcedId);
    }

    /** Sans aucune des deux dates, rien ne change : le défi est paru. */
    @Test
    void aChallengeWithoutAPublicationDateIsPublished() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge neverScheduled = challenge(challengeId);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId))
                .thenReturn(List.of(neverScheduled));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).extracting(fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto::id)
                .containsExactly(challengeId);
    }

    /**
     * Un défi caché ne doit pas être soumissible en devinant son identifiant : sans
     * cette garde, la date de parution ne serait qu'un filtre d'affichage.
     */
    @Test
    void refusesSubmissionOnAChallengeThatHasNotBeenPublishedYet() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge unpublished = challenge(challengeId);
        unpublished.setPublishedAt(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", "preuve")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi n'est pas encore disponible.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void aChallengeThatHasNotBeenPublishedYetCannotBeAddedToFavorites() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge unpublished = challenge(challengeId);
        unpublished.setPublishedAt(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> challengeService.addFavoriteChallenge("player-sub", challengeId))
                .isInstanceOf(ForbiddenException.class);

        verify(favoriteRepository, never()).save(any());
    }

    /**
     * La parution décide de l'affichage, la fenêtre de la soumission. Un défi paru
     * mais pas encore ouvert s'affiche donc, sans pouvoir être complété.
     */
    @Test
    void aPublishedChallengeStillWaitingForItsWindowIsShownButNotCompletable() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge announced = challenge(challengeId);
        announced.setPublishedAt(Instant.now().minusSeconds(3600));
        announced.setStartDate(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId)).thenReturn(List.of());
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(announced));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().canStillComplete()).isFalse();
    }

    @Test
    void favoriteChallengesOnlyReturnsVisibleFavorites() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID favoriteChallengeId = UUID.randomUUID();
        UUID hiddenFavoriteChallengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge favoriteChallenge = challenge(favoriteChallengeId);
        Challenge hiddenFavoriteChallenge = challenge(hiddenFavoriteChallengeId);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(favoriteRepository.findByUserId(playerId))
                .thenReturn(List.of(favorite(player, favoriteChallenge), favorite(player, hiddenFavoriteChallenge)));
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(List.of(favoriteChallenge));
        when(submissionRepository.findByUserIdOrParticipantUserId(playerId))
                .thenReturn(List.of());

        var result = challengeService.getFavoriteChallenges("player-sub");

        assertThat(result).extracting("id").containsExactly(favoriteChallengeId);
    }

    @Test
    void addFavoriteRequiresChallengeToBeVisibleToPlayer() {
        UUID playerId = UUID.randomUUID();
        UUID playerDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(playerDepartmentId);
        Department otherDepartment = new Department();
        otherDepartment.setId(otherDepartmentId);
        Challenge challenge = challenge(challengeId);
        challenge.setTargetDepartments(java.util.Set.of(otherDepartment));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.addFavoriteChallenge("player-sub", challengeId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Ce défi n'est pas disponible pour ce joueur.");

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavoriteCreatesFavoriteWhenChallengeIsVisible() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        Challenge challenge = challenge(challengeId);
        ArgumentCaptor<ChallengeFavorite> favoriteCaptor = ArgumentCaptor.forClass(ChallengeFavorite.class);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(favoriteRepository.existsByUserIdAndChallengeId(playerId, challengeId)).thenReturn(false);

        challengeService.addFavoriteChallenge("player-sub", challengeId);

        verify(favoriteRepository).save(favoriteCaptor.capture());
        assertThat(favoriteCaptor.getValue().getUser()).isEqualTo(player);
        assertThat(favoriteCaptor.getValue().getChallenge()).isEqualTo(challenge);
    }

    @Test
    void refusesSubmissionWhenChallengeWasAlreadyValidatedByPlayer() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = challenge(challengeId);
        ChallengeSubmission validatedSubmission = submission("VALIDATED");

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId))
                .thenReturn(List.of(validatedSubmission));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", "preuve")
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Un membre du groupe a déjà validé ce défi.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void refusesAutomaticSubmissionBeforeResolvingParticipantsOrConsumingProof() {
        UUID challengeId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID proofUploadId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        Challenge challenge = challenge(challengeId);
        challenge.setValidationMode("AUTO");

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("MEDIA", List.of(proofUploadId), List.of(participantId), "preuve", null)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Les défis automatiques ne peuvent pas être soumis par un joueur.")
                .satisfies(exception -> assertThat(((BadRequestException) exception).getFieldErrors())
                        .containsExactly(new fr.insastronaute.webapi.common.web.ApiErrorResponse.FieldViolation(
                                "validationMode",
                                "La validation de ce défi est effectuée automatiquement."
                        )));

        verify(userRepository, never()).findAllById(any());
        verify(proofStorageService, never()).attachSubmittedUploads(any(), any(), any(), any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void allowsRetryWhenPreviousSubmissionWasRejected() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = textChallenge(challengeId);
        ArgumentCaptor<ChallengeSubmission> submissionCaptor = ArgumentCaptor.forClass(ChallengeSubmission.class);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId))
                .thenReturn(List.of());

        challengeService.submitChallenge("player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "preuve"));

        verify(submissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo("PENDING");
    }

    /**
     * Le type de preuve appartient au défi, pas au client.
     *
     * <p>Il n'existait que sur la soumission : le joueur annonçait lui-même ce
     * qu'il déposait, et rien ne vérifiait que c'était ce que le défi demandait.
     * Une phrase suffisait donc à valider un défi qui réclame des fichiers, et la
     * soumission arrivait en modération sans aucune preuve à examiner.</p>
     */
    @Test
    void aTextProofIsRefusedOnAChallengeThatWantsFiles() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = challenge(challengeId);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("TEXT", "je l'ai fait, promis")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi se valide par des fichiers.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void filesAreRefusedOnAChallengeThatWantsAWrittenProof() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = textChallenge(challengeId);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub", challengeId, new ChallengeSubmissionDto("MEDIA", "une photo")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi se valide par une preuve écrite.");

        verify(proofStorageService, never()).attachSubmittedUploads(any(), any(), any(), any());
        verify(submissionRepository, never()).save(any());
    }

    /** Le champ est neuf : un défi enregistré avant lui attend des fichiers. */
    @Test
    void aChallengeWithoutADeclaredProofTypeStillWantsFiles() {
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminChallengeDto created = challengeService.createChallenge(new ChallengeCreateDto(
                "Photo", "Description", 10, "photo", null,
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null));

        assertThat(created.proofType()).isEqualTo("MEDIA");
    }

    @Test
    void aChallengeCanAskForAWrittenProof() {
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminChallengeDto created = challengeService.createChallenge(new ChallengeCreateDto(
                "Raconte", "Description", 10, "photo", null,
                null, null, null, null, "ADMIN", "TEXT", null, null, false, false, 1, 1, List.of(), List.of(), null));

        assertThat(created.proofType()).isEqualTo("TEXT");
    }

    @Test
    void storesTextProofInProofData() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = textChallenge(challengeId);
        ArgumentCaptor<ChallengeSubmission> submissionCaptor = ArgumentCaptor.forClass(ChallengeSubmission.class);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId)).thenReturn(List.of());

        challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", "Photo prise pendant l'event")
        );

        verify(submissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getProofData()).isEqualTo("Photo prise pendant l'event");
        assertThat(submissionCaptor.getValue().getComment()).isNull();
    }

    @Test
    void getSubmissionResultsReturnsMinimalPlayerSubmissionHistory() {
        UUID playerId = UUID.randomUUID();
        UUID rejectedSubmissionId = UUID.randomUUID();
        UUID pendingSubmissionId = UUID.randomUUID();
        UUID rejectedChallengeId = UUID.randomUUID();
        UUID pendingChallengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge rejectedChallenge = challenge(rejectedChallengeId);
        Challenge pendingChallenge = challenge(pendingChallengeId);
        ChallengeSubmission rejectedSubmission = submission("REJECTED");
        rejectedSubmission.setId(rejectedSubmissionId);
        rejectedSubmission.setChallenge(rejectedChallenge);
        rejectedSubmission.setUser(player);
        rejectedSubmission.setModerationComment("Preuve illisible");
        ChallengeSubmission pendingSubmission = submission("PENDING");
        pendingSubmission.setId(pendingSubmissionId);
        pendingSubmission.setChallenge(pendingChallenge);
        pendingSubmission.setUser(player(UUID.randomUUID()));
        pendingSubmission.getParticipants().add(player);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(submissionRepository.findByUserIdOrParticipantUserIdOrderByCreatedAtDesc(playerId))
                .thenReturn(List.of(pendingSubmission, rejectedSubmission));

        var result = challengeService.getSubmissionResults("player-sub");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().submissionId()).isEqualTo(pendingSubmissionId);
        assertThat(result.getFirst().challengeId()).isEqualTo(pendingChallengeId);
        assertThat(result.getFirst().status()).isEqualTo("PENDING");
        assertThat(result.getFirst().moderationComment()).isNull();
        assertThat(result.getFirst().participantOnly()).isTrue();
        assertThat(result.get(1).challengeId()).isEqualTo(rejectedChallengeId);
        assertThat(result.get(1).status()).isEqualTo("REJECTED");
        assertThat(result.get(1).moderationComment()).isEqualTo("Preuve illisible");
    }

    @Test
    void getSubmissionsWaitingConfirmationFiltersThatStatusForSubmissionMembers() {
        UUID playerId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setChallenge(challenge(challengeId));
        submission.setUser(player(UUID.randomUUID()));
        submission.getParticipants().add(player);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(submissionRepository.findByUserIdOrParticipantUserIdAndStatusOrderByCreatedAtDesc(
                playerId,
                "WAITING_CONFIRMATION"
        )).thenReturn(List.of(submission));

        var result = challengeService.getSubmissionsWaitingConfirmation("player-sub");

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.submissionId()).isEqualTo(submissionId);
            assertThat(dto.challengeId()).isEqualTo(challengeId);
            assertThat(dto.status()).isEqualTo("WAITING_CONFIRMATION");
            assertThat(dto.participantOnly()).isTrue();
        });
    }

    @Test
    void getSubmissionsPendingValidationFiltersPendingStatusForSubmissionMembers() {
        UUID playerId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setChallenge(challenge(challengeId));
        submission.setUser(player);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(submissionRepository.findByUserIdOrParticipantUserIdAndStatusOrderByCreatedAtDesc(
                playerId,
                "PENDING"
        )).thenReturn(List.of(submission));

        var result = challengeService.getSubmissionsPendingValidation("player-sub");

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.submissionId()).isEqualTo(submissionId);
            assertThat(dto.challengeId()).isEqualTo(challengeId);
            assertThat(dto.status()).isEqualTo("PENDING");
            assertThat(dto.participantOnly()).isFalse();
        });
    }

    @Test
    void refusesSubmissionWhenChallengeHasNotStarted() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = challenge(challengeId);
        challenge.setStartDate(Instant.now().plusSeconds(3600));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", "preuve")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ce défi n'est pas encore disponible.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void refusesSubmissionWhenParticipantAlreadyHasPendingSubmissionForChallenge() {
        UUID playerId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        AppUser participant = player(participantId);
        Challenge challenge = challenge(challengeId);
        challenge.setMaxGroupSize(2);
        ChallengeSubmission pendingSubmission = submission("PENDING");

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(java.util.Set.of(participantId))).thenReturn(List.of(participant));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(playerId, participantId),
                challengeId
        )).thenReturn(List.of(pendingSubmission));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", null, List.of(participantId), "preuve", null)
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Un membre du groupe a déjà une soumission en attente pour ce défi.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void refusesSubmissionWhenParticipantHasNotCompletedPrerequisite() {
        UUID playerId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID prerequisiteId = UUID.randomUUID();
        AppUser player = player(playerId);
        AppUser participant = player(participantId);
        participant.setDisplayName("Chloé");
        Challenge prerequisite = challenge(prerequisiteId);
        prerequisite.setTitle("Monter la tour");
        Challenge challenge = challenge(challengeId);
        challenge.setMaxGroupSize(2);
        challenge.setPrerequisiteChallenges(java.util.Set.of(prerequisite));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(java.util.Set.of(participantId))).thenReturn(List.of(participant));
        when(submissionRepository.existsValidatedSubmissionForUserAndChallenge(playerId, prerequisiteId)).thenReturn(true);
        when(submissionRepository.existsValidatedSubmissionForUserAndChallenge(participantId, prerequisiteId)).thenReturn(false);

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", null, List.of(participantId), "preuve", null)
        ))
                .isInstanceOf(BadRequestException.class)
                // Nommer qui bloque et ce qui manque : sinon il fallait ouvrir la
                // console du navigateur pour le comprendre.
                .hasMessage("Chloé n'a pas encore validé « Monter la tour ».");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void eligibilitySaysWhoCannotBeInvitedAndWhy() {
        UUID playerId = UUID.randomUUID();
        UUID readyId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID prerequisiteId = UUID.randomUUID();
        AppUser player = player(playerId);
        AppUser ready = player(readyId);
        ready.setDisplayName("Yanis");
        AppUser blocked = player(blockedId);
        blocked.setDisplayName("Chloé");
        Challenge prerequisite = challenge(prerequisiteId);
        prerequisite.setTitle("Monter la tour");
        Challenge challenge = challenge(challengeId);
        challenge.setPrerequisiteChallenges(java.util.Set.of(prerequisite));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(List.of(readyId, blockedId))).thenReturn(List.of(ready, blocked));
        when(submissionRepository.existsValidatedSubmissionForUserAndChallenge(readyId, prerequisiteId)).thenReturn(true);
        when(submissionRepository.existsValidatedSubmissionForUserAndChallenge(blockedId, prerequisiteId)).thenReturn(false);
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(readyId), challengeId))
                .thenReturn(List.of());

        var result = challengeService.participantEligibility("player-sub", challengeId, List.of(readyId, blockedId));

        assertThat(result).extracting(dto -> dto.userId(), dto -> dto.eligible(), dto -> dto.reason())
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(readyId, true, null),
                        org.assertj.core.api.Assertions.tuple(blockedId, false, "N'a pas encore validé « Monter la tour ».")
                );
    }

    @Test
    void eligibilityFlagsSomeoneWhoAlreadyValidatedTheChallenge() {
        UUID playerId = UUID.randomUUID();
        UUID doneId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        AppUser done = player(doneId);
        done.setDisplayName("Yanis");
        Challenge challenge = challenge(challengeId);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(List.of(doneId))).thenReturn(List.of(done));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(doneId), challengeId))
                .thenReturn(List.of(submission("VALIDATED")));

        var result = challengeService.participantEligibility("player-sub", challengeId, List.of(doneId));

        assertThat(result.getFirst().eligible()).isFalse();
        assertThat(result.getFirst().reason()).isEqualTo("A déjà validé ce défi.");
        assertThat(result.getFirst().displayName()).isEqualTo("Yanis");
    }

    @Test
    void groupSubmissionWaitsForParticipantConfirmationAndSendsInvite() {
        UUID playerId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setEmail("player@example.com");
        AppUser participant = player(participantId);
        Challenge challenge = textChallenge(challengeId);
        challenge.setTitle("Photo equipe");
        challenge.setMaxGroupSize(2);
        ArgumentCaptor<ChallengeSubmission> submissionCaptor = ArgumentCaptor.forClass(ChallengeSubmission.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(java.util.Set.of(participantId))).thenReturn(List.of(participant));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(playerId, participantId),
                challengeId
        )).thenReturn(List.of());
        when(submissionRepository.save(any())).thenAnswer(invocation -> {
            ChallengeSubmission submission = invocation.getArgument(0);
            submission.setId(submissionId);
            return submission;
        });

        challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", null, List.of(participantId), "preuve", null)
        );

        verify(submissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getParticipants())
                .extracting(AppUser::getId)
                .containsExactly(participantId);
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo("WAITING_CONFIRMATION");
        verify(notificationService).notifyUser(
                eq(participantId),
                eq(playerId),
                eq(NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED),
                eq("Invitation à un défi"),
                eq("player@example.com t'a ajouté au défi \"Photo equipe\"."),
                payloadCaptor.capture(),
                eq("CHALLENGE_GROUP_INVITE"),
                eq(submissionId)
        );
        assertThat(payloadCaptor.getValue())
                .containsEntry("challengeId", challengeId.toString())
                .containsEntry("submissionId", submissionId.toString())
                .containsEntry("submitterUserId", playerId.toString())
                .containsEntry("action", "CONFIRM_GROUP_CHALLENGE");
    }

    @Test
    void acceptingLastGroupInviteMovesSubmissionToPendingModeration() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        submitter.setEmail("submitter@example.com");
        AppUser participant = player(UUID.randomUUID());
        participant.setKeycloakSub("participant-sub");
        participant.setEmail("participant@example.com");
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(participant);
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Photo equipe");
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("participant-sub")).thenReturn(Optional.of(participant));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.updateParticipantConfirmation(submissionId, participant.getId(), "ACCEPTED")).thenReturn(1);
        when(submissionRepository.countParticipantsNotAccepted(submissionId)).thenReturn(0L);

        challengeService.respondToGroupSubmission(submissionId, "participant-sub", true);

        assertThat(submission.getStatus()).isEqualTo("PENDING");
        verify(notificationService).notifyUser(
                eq(submitter.getId()),
                eq(participant.getId()),
                eq(NotificationType.CHALLENGE_GROUP_INVITE_ACCEPTED),
                eq("Invitation acceptée"),
                eq("participant@example.com a accepté de participer au défi \"Photo equipe\"."),
                anyMap(),
                eq("CHALLENGE_GROUP_INVITE"),
                eq(submissionId)
        );
        verify(submissionRepository).save(submission);
    }

    /**
     * Refuser, c'est se retirer -- pas annuler le travail des autres.
     *
     * <p>Un seul « non » emportait toute la soumission : la preuve etait perdue avec
     * elle, les acceptations aussi, et le chef devait tout re-televerser. Le groupe
     * restant tient dans les clous du defi, donc la preuve part.</p>
     */
    @Test
    void decliningLeavesTheGroupAndLetsTheProofGoWhenItStillFits() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        AppUser accepted = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        participant.setKeycloakSub("participant-sub");
        participant.setEmail("participant@example.com");
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Photo equipe");
        challenge.setMinGroupSize(2);
        challenge.setMaxGroupSize(4);
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(accepted);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("participant-sub")).thenReturn(Optional.of(participant));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.updateParticipantConfirmation(submissionId, participant.getId(), "DECLINED")).thenReturn(1);
        when(submissionRepository.countParticipantsNotAccepted(submissionId)).thenReturn(0L);

        challengeService.respondToGroupSubmission(submissionId, "participant-sub", false);

        assertThat(submission.getParticipants()).containsExactly(accepted);
        assertThat(submission.getStatus()).isEqualTo("PENDING");
        assertThat(submission.getModerationComment()).isNull();
        verify(notificationService).notifyUser(
                eq(submitter.getId()),
                eq(participant.getId()),
                eq(NotificationType.CHALLENGE_GROUP_INVITE_DECLINED),
                eq("Invitation refusée"),
                eq("participant@example.com a refusé de participer au défi \"Photo equipe\"."),
                anyMap(),
                eq("CHALLENGE_GROUP_INVITE"),
                eq(submissionId)
        );
    }

    /**
     * Le depart fait tomber le groupe sous le minimum : la soumission attend qu'on
     * la resolve, plutot que de partir amputee ou de mourir.
     */
    @Test
    void decliningBelowTheMinimumLeavesTheSubmissionWaitingForResolution() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        participant.setKeycloakSub("participant-sub");
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setMinGroupSize(3);
        challenge.setMaxGroupSize(4);
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("participant-sub")).thenReturn(Optional.of(participant));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.updateParticipantConfirmation(submissionId, participant.getId(), "DECLINED")).thenReturn(1);

        challengeService.respondToGroupSubmission(submissionId, "participant-sub", false);

        assertThat(submission.getParticipants()).isEmpty();
        assertThat(submission.getStatus()).isEqualTo("WAITING_CONFIRMATION");
        // Personne n'a ete compte : c'est la taille qui bloque, pas une reponse manquante.
        verify(submissionRepository, never()).countParticipantsNotAccepted(submissionId);
    }

    /** Quelqu'un n'a pas encore repondu : le depart d'un autre ne precipite rien. */
    @Test
    void decliningWaitsForTheMembersWhoHaveNotAnsweredYet() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        AppUser silent = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        participant.setKeycloakSub("participant-sub");
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setMinGroupSize(2);
        challenge.setMaxGroupSize(4);
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(silent);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("participant-sub")).thenReturn(Optional.of(participant));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.updateParticipantConfirmation(submissionId, participant.getId(), "DECLINED")).thenReturn(1);
        when(submissionRepository.countParticipantsNotAccepted(submissionId)).thenReturn(1L);

        challengeService.respondToGroupSubmission(submissionId, "participant-sub", false);

        assertThat(submission.getStatus()).isEqualTo("WAITING_CONFIRMATION");
    }

    /**
     * Le chef recompose son equipe d'un bloc.
     *
     * <p>Un appel par geste aurait fait subir au groupe des etats que personne ne
     * voulait : retirer le dernier membre en attente aurait envoye la preuve a
     * l'instant meme, alors qu'il restait quelqu'un a ajouter.</p>
     */
    @Test
    void theLeaderRecomposesTheTeamInOneGo() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        submitter.setKeycloakSub("submitter-sub");
        AppUser leaving = player(UUID.randomUUID());
        AppUser newcomer = player(UUID.randomUUID());
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setMinGroupSize(2);
        challenge.setMaxGroupSize(4);
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(leaving);
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("submitter-sub")).thenReturn(Optional.of(submitter));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(userRepository.findAllById(List.of(newcomer.getId()))).thenReturn(List.of(newcomer));
        when(submissionRepository.countParticipantsNotAccepted(submissionId)).thenReturn(1L);

        challengeService.replaceGroupParticipants(submissionId, "submitter-sub", List.of(newcomer.getId()));

        assertThat(submission.getParticipants()).containsExactly(newcomer);
        // L'un est invite, l'autre apprend qu'il sort : un retrait silencieux
        // ressemble a une panne pour qui avait deja accepte.
        verify(notificationService).notifyUser(eq(newcomer.getId()), any(),
                eq(NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED), any(), any(), anyMap(), any(), eq(submissionId));
        verify(notificationService).notifyUser(eq(leaving.getId()), any(),
                eq(NotificationType.CHALLENGE_GROUP_MEMBER_REMOVED), any(), any(), anyMap(), any(), eq(submissionId));
        assertThat(submission.getStatus()).isEqualTo("WAITING_CONFIRMATION");
    }

    /** Une equipe finale trop petite est refusee : le defi en exige plus. */
    @Test
    void aTeamBelowTheMinimumIsRefused() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        submitter.setKeycloakSub("submitter-sub");
        AppUser participant = player(UUID.randomUUID());
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setMinGroupSize(3);
        challenge.setMaxGroupSize(4);
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("submitter-sub")).thenReturn(Optional.of(submitter));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        assertThatThrownBy(() -> challengeService.replaceGroupParticipants(
                submissionId, "submitter-sub", List.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getPlayerSubmissionDetailReturnsParticipantConfirmationStatuses() {
        UUID submissionId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser submitter = player(submitterId);
        submitter.setKeycloakSub("submitter-sub");
        submitter.setEmail("submitter@example.com");
        AppUser participant = player(participantId);
        participant.setDisplayName("Participant A");
        participant.setEmail("participant@example.com");
        Challenge challenge = challenge(challengeId);
        challenge.setTitle("Photo equipe");
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);
        Instant invitedAt = Instant.parse("2026-06-16T12:01:00Z");

        when(userRepository.findByKeycloakSub("submitter-sub")).thenReturn(Optional.of(submitter));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findParticipantConfirmations(submissionId))
                .thenReturn(List.of(participantConfirmation(participantId, "Participant A", invitedAt)));

        var detail = challengeService.getPlayerSubmissionDetail(submissionId, "submitter-sub");

        assertThat(detail.id()).isEqualTo(submissionId);
        assertThat(detail.challengeId()).isEqualTo(challengeId);
        assertThat(detail.currentUserSubmitter()).isTrue();
        assertThat(detail.participants()).hasSize(2);
        assertThat(detail.participants().getFirst().userId()).isEqualTo(submitterId);
        assertThat(detail.participants().getFirst().confirmationStatus()).isEqualTo("ACCEPTED");
        assertThat(detail.participants().get(1).userId()).isEqualTo(participantId);
        assertThat(detail.participants().get(1).confirmationStatus()).isEqualTo("PENDING");
        assertThat(detail.participants().get(1).invitedAt()).isEqualTo(invitedAt);
    }

    @Test
    void submitterCanCancelWaitingGroupSubmission() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        submitter.setKeycloakSub("submitter-sub");
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.getParticipants().add(player(UUID.randomUUID()));

        when(userRepository.findByKeycloakSub("submitter-sub")).thenReturn(Optional.of(submitter));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        challengeService.cancelGroupSubmission(submissionId, "submitter-sub");

        assertThat(submission.getStatus()).isEqualTo("REJECTED");
        assertThat(submission.getModerationComment()).isEqualTo("Soumission de groupe annulée par l'initiateur.");
        verify(submissionRepository).save(submission);
    }

    @Test
    void participantCannotCancelGroupSubmission() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        participant.setKeycloakSub("participant-sub");
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setUser(submitter);
        submission.getParticipants().add(participant);

        when(userRepository.findByKeycloakSub("participant-sub")).thenReturn(Optional.of(participant));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.cancelGroupSubmission(submissionId, "participant-sub"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Accès refusé : vous n'êtes pas l'initiateur de cette soumission.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitterCanResendPendingGroupInvites() {
        UUID submissionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        submitter.setKeycloakSub("submitter-sub");
        submitter.setEmail("submitter@example.com");
        AppUser participant = player(participantId);
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Photo equipe");
        ChallengeSubmission submission = submission("WAITING_CONFIRMATION");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.setChallenge(challenge);
        submission.getParticipants().add(participant);

        when(userRepository.findByKeycloakSub("submitter-sub")).thenReturn(Optional.of(submitter));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findParticipantConfirmations(submissionId))
                .thenReturn(List.of(participantConfirmation(participantId, null, Instant.now())));

        challengeService.resendGroupSubmissionInvites(submissionId, "submitter-sub");

        verify(submissionRepository).touchPendingParticipantInvites(submissionId);
        verify(notificationService).notifyUser(
                eq(participantId),
                eq(submitter.getId()),
                eq(NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED),
                eq("Invitation à un défi"),
                eq("submitter@example.com t'a relancé pour le défi \"Photo equipe\"."),
                anyMap(),
                eq("CHALLENGE_GROUP_INVITE"),
                eq(submissionId)
        );
    }

    @Test
    void refusesSubmissionWhenGroupSizeIsBelowChallengeMinimum() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = challenge(challengeId);
        challenge.setMinGroupSize(2);
        challenge.setMaxGroupSize(3);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", "preuve")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Taille de groupe invalide.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void refusesSubmissionWhenParticipantDepartmentIsNotAllowedForChallenge() {
        UUID playerId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID forbiddenDepartmentId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(allowedDepartmentId);
        AppUser participant = player(participantId);
        participant.setDepartment(forbiddenDepartmentId);
        Department allowedDepartment = new Department();
        allowedDepartment.setId(allowedDepartmentId);
        Challenge challenge = challenge(challengeId);
        challenge.setMaxGroupSize(2);
        challenge.setTargetDepartments(java.util.Set.of(allowedDepartment));

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(java.util.Set.of(participantId))).thenReturn(List.of(participant));

        assertThatThrownBy(() -> challengeService.submitChallenge(
                "player-sub",
                challengeId,
                new ChallengeSubmissionDto("TEXT", null, List.of(participantId), "preuve", null)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Un membre du groupe n'est pas dans un département autorisé.");
    }

    @Test
    void creditsChallengePointsToAllGroupMembersWhenPendingSubmissionIsValidated() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(15);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of(submission));
        when(userRepository.findAll()).thenReturn(List.of(player, participant));

        challengeService.decideSubmission(submissionId, "admin-sub", List.of("ROLE_ADMIN"), "VALIDATED", null, null);

        assertThat(player.getChallengePoints()).isEqualTo(15);
        assertThat(participant.getChallengePoints()).isEqualTo(15);
        assertThat(player.getTokens()).isEqualTo(15);
        assertThat(participant.getTokens()).isEqualTo(15);
        // Les tokens d'un défi rapportent le double en points Events.
        assertThat(player.getEventPoints()).isEqualTo(30);
        assertThat(participant.getEventPoints()).isEqualTo(30);
        assertThat(submission.getReviewLockedBy()).isNull();
        assertThat(submission.getReviewStartedAt()).isNull();
    }

    @Test
    void challengeTokensAlreadySpentAreNotRestoredByARecalculation() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        // 15 points were already paid out as tokens, and the player spent 10 of them.
        player.setChallengeTokensGranted(15L);
        player.setTokens(5L);

        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(25);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of(submission));
        when(userRepository.findAll()).thenReturn(List.of(player));

        challengeService.decideSubmission(submissionId, "admin-sub", List.of("ROLE_ADMIN"), "VALIDATED", null, null);

        assertThat(player.getChallengePoints()).isEqualTo(25);
        // Only the 10 newly earned points are credited; the 10 already spent stay spent.
        assertThat(player.getTokens()).isEqualTo(15);
        assertThat(player.getChallengeTokensGranted()).isEqualTo(25);
    }

    @Test
    void losingChallengePointsAfterSpendingFloorsTheTokenBalanceAtZero() {
        AppUser player = player(UUID.randomUUID());
        // 20 points were paid out as tokens, and all but 2 have been spent since.
        player.setChallengeTokensGranted(20L);
        player.setTokens(2L);

        // Those points are gone: nothing counts towards the player any more.
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(player));

        challengeService.recalculateChallengePoints();

        assertThat(player.getChallengePoints()).isZero();
        // Debiting 20 from a balance of 2 must not leave the player owing tokens.
        assertThat(player.getTokens()).isZero();
        assertThat(player.getChallengeTokensGranted()).isZero();
    }

    @Test
    void validatedSubmissionBonusPointsAreAddedToChallengePoints() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(15);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of(submission));
        when(userRepository.findAll()).thenReturn(List.of(player));

        challengeService.decideSubmission(submissionId, "admin-sub", List.of("ROLE_ADMIN"),
                "VALIDATED", "Preuve particulièrement complète.", 5);

        assertThat(submission.getBonusPoints()).isEqualTo(5);
        assertThat(player.getChallengePoints()).isEqualTo(20);
        verify(journal).record(
                eq("admin-sub"),
                eq(player.getId()),
                eq(AdminActionType.CHALLENGE_VALIDATED),
                argThat(details -> submissionId.toString().equals(details.get("submissionId"))
                        && challenge.getId().toString().equals(details.get("challengeId"))
                        && Integer.valueOf(5).equals(details.get("bonusPoints")))
        );
    }

    @Test
    void validatedSubmissionMalusReducesAwardAndKeepsItsContext() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(15);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of(submission));
        when(userRepository.findAll()).thenReturn(List.of(player));

        challengeService.decideSubmission(submissionId, "admin-sub", List.of("ROLE_ADMIN"),
                "VALIDATED", "Preuve partielle : cinq points retirés.", -5);

        assertThat(submission.getBonusPoints()).isEqualTo(-5);
        assertThat(submission.getModerationComment())
                .isEqualTo("Preuve partielle : cinq points retirés.");
        assertThat(player.getChallengePoints()).isEqualTo(10);
    }

    @Test
    void requiresValidationCommentWhenPointsAreAdjusted() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.decideSubmission(
                submissionId, "admin-sub", List.of("ROLE_ADMIN"), "VALIDATED", "  ", 5))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Commentaire de validation requis.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void moderatorCanAddAnEligibleParticipantAsAccepted() {
        UUID submissionId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        participant.setDisplayName("Chloé");
        participant.setEmail("chloe@example.com");
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setMaxGroupSize(3);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.setChallenge(challenge);
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(userRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(participant.getId()), challenge.getId())).thenReturn(List.of());
        when(submissionRepository.updateParticipantConfirmation(
                submissionId, participant.getId(), "ACCEPTED")).thenReturn(1);

        var added = challengeService.addModerationParticipant(
                submissionId, participant.getId(), "admin-sub", List.of("ROLE_ADMIN"));

        assertThat(submission.getParticipants()).containsExactly(participant);
        assertThat(added.confirmationStatus()).isEqualTo("ACCEPTED");
        verify(submissionRepository).saveAndFlush(submission);
        verify(submissionRepository).updateParticipantConfirmation(
                submissionId, participant.getId(), "ACCEPTED");
    }

    @Test
    void createsApprovedManualSubmissionAndRecalculatesPlayerPoints() {
        UUID playerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        AppUser player = player(playerId);
        Challenge challenge = challenge(challengeId);
        challenge.setPointsReward(15);
        ArgumentCaptor<ChallengeSubmission> submissionCaptor = ArgumentCaptor.forClass(ChallengeSubmission.class);
        AtomicReference<ChallengeSubmission> savedSubmission = new AtomicReference<>();

        when(userRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(List.of(playerId), challengeId))
                .thenReturn(List.of());
        when(submissionRepository.save(any(ChallengeSubmission.class))).thenAnswer(invocation -> {
            ChallengeSubmission submission = invocation.getArgument(0);
            submission.setId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
            savedSubmission.set(submission);
            return submission;
        });
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenAnswer(ignored -> List.of(savedSubmission.get()));
        when(userRepository.findAll()).thenReturn(List.of(player));

        challengeService.createApprovedManualSubmission(playerId, challengeId, "Validation sur stand", "sub-admin");

        verify(submissionRepository).save(submissionCaptor.capture());
        ChallengeSubmission saved = submissionCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(player);
        assertThat(saved.getChallenge()).isEqualTo(challenge);
        assertThat(saved.getStatus()).isEqualTo("VALIDATED");
        assertThat(saved.getProofType()).isEqualTo("TEXT");
        assertThat(saved.getProofData()).isEmpty();
        assertThat(saved.getComment()).isEqualTo("Validation sur stand");
        assertThat(saved.getValidatedAt()).isNotNull();
        assertThat(player.getChallengePoints()).isEqualTo(15);
        verify(journal).record(
                eq("sub-admin"),
                eq(playerId),
                eq(AdminActionType.CHALLENGE_GRANTED),
                argThat(details -> saved.getId().toString().equals(details.get("submissionId"))
                        && challengeId.toString().equals(details.get("challengeId"))
                        && "Validation sur stand".equals(details.get("comment")))
        );
    }

    @Test
    void refusesBonusPointsOnRejectedDecision() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.decideSubmission(
                submissionId,
                "admin-sub",
                List.of("ROLE_ADMIN"),
                "REJECTED",
                "Preuve insuffisante",
                5
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Points bonus invalides.");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void globalQuotaControlsAwardedPointsChronologicallyWhenChallengeIsUpdated() {
        UUID challengeId = UUID.randomUUID();
        AppUser firstPlayer = player(UUID.randomUUID());
        AppUser secondPlayer = player(UUID.randomUUID());
        Challenge challenge = challenge(challengeId);
        challenge.setGlobalQuota(2);
        ChallengeSubmission firstSubmission = submission("VALIDATED");
        firstSubmission.setUser(firstPlayer);
        firstSubmission.setChallenge(challenge);
        firstSubmission.setValidatedAt(Instant.now().minusSeconds(60));
        ChallengeSubmission secondSubmission = submission("VALIDATED");
        secondSubmission.setUser(secondPlayer);
        secondSubmission.setChallenge(challenge);
        secondSubmission.setValidatedAt(Instant.now());
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo equipe",
                "Une preuve en groupe",
                10,
                "photo",
                null,
                null,
                null,
                null,
                1,
                "ADMIN",
                null,
                null,
                null,
                false, false,
                1,
                1,
                List.of(),
                List.of()
        , null);

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(firstSubmission, secondSubmission));
        when(userRepository.findAll()).thenReturn(List.of(firstPlayer, secondPlayer));

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        assertThat(firstPlayer.getChallengePoints()).isEqualTo(10);
        assertThat(secondPlayer.getChallengePoints()).isZero();
    }

    @Test
    void zeroGlobalQuotaAwardsPointsToEveryValidatedSubmission() {
        UUID challengeId = UUID.randomUUID();
        AppUser firstPlayer = player(UUID.randomUUID());
        AppUser secondPlayer = player(UUID.randomUUID());
        Challenge challenge = challenge(challengeId);
        challenge.setGlobalQuota(1);
        ChallengeSubmission firstSubmission = submission("VALIDATED");
        firstSubmission.setUser(firstPlayer);
        firstSubmission.setChallenge(challenge);
        ChallengeSubmission secondSubmission = submission("VALIDATED");
        secondSubmission.setUser(secondPlayer);
        secondSubmission.setChallenge(challenge);
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo equipe",
                "Une preuve en groupe",
                10,
                "photo",
                null,
                null,
                null,
                null,
                0,
                "ADMIN",
                null,
                null,
                null,
                false, false,
                1,
                1,
                List.of(),
                List.of()
        , null);

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(firstSubmission, secondSubmission));
        when(userRepository.findAll()).thenReturn(List.of(firstPlayer, secondPlayer));

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        assertThat(firstPlayer.getChallengePoints()).isEqualTo(10);
        assertThat(secondPlayer.getChallengePoints()).isEqualTo(10);
    }

    @Test
    void approvalSendsValidatedNotificationToSubmitterAndParticipants() {
        UUID submissionId = UUID.randomUUID();
        AppUser moderator = player(UUID.randomUUID());
        moderator.setKeycloakSub("respos-defis-sub");
        AppUser player = player(UUID.randomUUID());
        AppUser participant = player(UUID.randomUUID());
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Photo equipe");
        challenge.setPointsReward(15);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.getParticipants().add(participant);
        submission.setChallenge(challenge);
        submission.setReviewLockedBy("respos-defis-sub");
        submission.setReviewStartedAt(Instant.now());

        when(userRepository.findByKeycloakSub("respos-defis-sub")).thenReturn(Optional.of(moderator));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        challengeService.decideSubmission(
                submissionId,
                "respos-defis-sub",
                List.of("ROLE_RESPOS_DEFIS"),
                "VALIDATED",
                null,
                null
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService, times(2)).notifyUser(
                any(UUID.class),
                eq(moderator.getId()),
                eq(NotificationType.CHALLENGE_SUBMISSION_VALIDATED),
                eq("Défi validé"),
                eq("Ta preuve pour \"Photo equipe\" a été validée."),
                payloadCaptor.capture(),
                eq("CHALLENGE_SUBMISSION"),
                eq(submissionId)
        );
        verify(notificationService).notifyUser(
                eq(player.getId()),
                eq(moderator.getId()),
                eq(NotificationType.CHALLENGE_SUBMISSION_VALIDATED),
                anyString(),
                anyString(),
                anyMap(),
                eq("CHALLENGE_SUBMISSION"),
                eq(submissionId)
        );
        verify(notificationService).notifyUser(
                eq(participant.getId()),
                eq(moderator.getId()),
                eq(NotificationType.CHALLENGE_SUBMISSION_VALIDATED),
                anyString(),
                anyString(),
                anyMap(),
                eq("CHALLENGE_SUBMISSION"),
                eq(submissionId)
        );
        assertThat(payloadCaptor.getValue())
                .containsEntry("challengeId", challenge.getId().toString())
                .containsEntry("submissionId", submissionId.toString())
                .containsEntry("status", "VALIDATED")
                .containsEntry("pointsReward", 15)
                .containsEntry("bonusPoints", 0);
    }

    @Test
    void pendingSubmissionsForLocalModeratorsRequireDepartmentVerificationLink() {
        UUID moderatorDepartmentId = UUID.randomUUID();
        UUID playerDepartmentId = UUID.randomUUID();
        AppUser moderator = player(UUID.randomUUID());
        moderator.setKeycloakSub("moderator-sub");
        moderator.setDepartment(moderatorDepartmentId);
        AppUser player = player(UUID.randomUUID());
        player.setDepartment(playerDepartmentId);
        ChallengeSubmission allowedSubmission = submission("PENDING");
        allowedSubmission.setId(UUID.randomUUID());
        allowedSubmission.setUser(player);
        allowedSubmission.setChallenge(challenge(UUID.randomUUID()));
        allowedSubmission.setProofType("TEXT");
        allowedSubmission.setProofData("preuve");

        when(userRepository.findByKeycloakSub("moderator-sub")).thenReturn(Optional.of(moderator));
        when(submissionRepository.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(allowedSubmission));
        when(verificationLinkRepository.existsByVerifyingDepartmentIdAndVerifiedDepartmentId(
                moderatorDepartmentId,
                playerDepartmentId
        )).thenReturn(true);

        var result = challengeService.getPendingSubmissions("moderator-sub", List.of("ROLE_SOUS_RESPO_DEFIS"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().submissionId()).isEqualTo(allowedSubmission.getId());
    }

    @Test
    void pendingSubmissionsHideSensitiveChallengeWhenModeratorGenderDiffers() {
        UUID moderatorDepartmentId = UUID.randomUUID();
        UUID playerDepartmentId = UUID.randomUUID();
        AppUser moderator = player(UUID.randomUUID());
        moderator.setKeycloakSub("moderator-sub");
        moderator.setDepartment(moderatorDepartmentId);
        moderator.setGender(Gender.MALE);
        AppUser player = player(UUID.randomUUID());
        player.setDepartment(playerDepartmentId);
        player.setGender(Gender.FEMALE);
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setSensitive(true);
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player);
        submission.setChallenge(challenge);

        when(userRepository.findByKeycloakSub("moderator-sub")).thenReturn(Optional.of(moderator));
        when(submissionRepository.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(submission));

        var result = challengeService.getPendingSubmissions("moderator-sub", List.of("ROLE_SOUS_RESPO_DEFIS"));

        assertThat(result).isEmpty();
    }

    @Test
    void grandChallengeResponsibleCanModerateAnyDepartmentButStillRespectsSensitiveGenderRule() {
        AppUser moderator = player(UUID.randomUUID());
        moderator.setKeycloakSub("respos-defis-sub");
        moderator.setGender(Gender.FEMALE);
        AppUser player = player(UUID.randomUUID());
        player.setGender(Gender.FEMALE);
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setSensitive(true);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(UUID.randomUUID());
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setProofType("TEXT");
        submission.setProofData("preuve");

        when(userRepository.findByKeycloakSub("respos-defis-sub")).thenReturn(Optional.of(moderator));
        when(submissionRepository.findByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(submission));

        var result = challengeService.getPendingSubmissions("respos-defis-sub", List.of("ROLE_RESPOS_DEFIS"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().submissionId()).isEqualTo(submission.getId());
    }

    @Test
    void rejectionStoresModerationComment() {
        UUID submissionId = UUID.randomUUID();
        AppUser moderator = player(UUID.randomUUID());
        moderator.setKeycloakSub("respos-defis-sub");
        AppUser player = player(UUID.randomUUID());
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("respos-defis-sub");
        submission.setReviewStartedAt(Instant.now());

        when(userRepository.findByKeycloakSub("respos-defis-sub")).thenReturn(Optional.of(moderator));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        challengeService.decideSubmission(
                submissionId,
                "respos-defis-sub",
                List.of("ROLE_RESPOS_DEFIS"),
                "REJECTED",
                "Preuve illisible",
                null
        );

        assertThat(submission.getStatus()).isEqualTo("REJECTED");
        assertThat(submission.getModerationComment()).isEqualTo("Preuve illisible");
        verify(journal).record(
                eq("respos-defis-sub"),
                eq(player.getId()),
                eq(AdminActionType.CHALLENGE_REJECTED),
                argThat(details -> submissionId.toString().equals(details.get("submissionId"))
                        && submission.getChallenge().getId().toString().equals(details.get("challengeId"))
                        && "Preuve illisible".equals(details.get("comment")))
        );
    }

    @Test
    void locksSubmissionForCurrentReviewer() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        challengeService.lockSubmissionForReview(submissionId, "admin-sub", List.of("ROLE_ADMIN"));

        assertThat(submission.getReviewLockedBy()).isEqualTo("admin-sub");
        assertThat(submission.getReviewStartedAt()).isNotNull();
        assertThat(submission.getReviewExpiresAt()).isAfter(submission.getReviewStartedAt());
        verify(moderationAuditService).record(submissionId, "admin-sub", "LOCK_ACQUIRED", "SUCCESS", Map.of());
    }

    @Test
    void heartbeatRenewsOwnedActiveReviewLock() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now().minusSeconds(120));
        submission.setReviewExpiresAt(Instant.now().plusSeconds(60));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        Instant expiresAt = challengeService.heartbeatSubmissionReviewLock(
                submissionId, "admin-sub", List.of("ROLE_ADMIN"));

        assertThat(expiresAt).isEqualTo(submission.getReviewExpiresAt());
        assertThat(expiresAt).isAfter(Instant.now().plusSeconds(14 * 60));
        verify(moderationAuditService).record(submissionId, "admin-sub", "LOCK_RENEWED", "SUCCESS", Map.of());
    }

    @Test
    void heartbeatRejectsRenewalAfterMaximumContinuousReview() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now().minusSeconds(60 * 60));
        submission.setReviewExpiresAt(Instant.now().plusSeconds(60));
        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.heartbeatSubmissionReviewLock(
                submissionId, "admin-sub", List.of("ROLE_ADMIN")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesToLockSubmissionAlreadyLockedByAnotherActiveReviewer() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("first-reviewer");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.lockSubmissionForReview(
                submissionId,
                "second-reviewer",
                List.of("ROLE_ADMIN")
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cette preuve est déjà en cours de modération par un autre membre du staff. "
                        + "Choisis une autre preuve ou réessaie lorsqu'elle sera libérée.");
    }

    @Test
    void allowsTakingOverExpiredReviewLock() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("first-reviewer");
        submission.setReviewStartedAt(Instant.now().minusSeconds(16 * 60));

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        challengeService.lockSubmissionForReview(submissionId, "second-reviewer", List.of("ROLE_ADMIN"));

        assertThat(submission.getReviewLockedBy()).isEqualTo("second-reviewer");
    }

    @Test
    void refusesDecisionWithoutCurrentReviewerLock() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("first-reviewer");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.decideSubmission(
                submissionId,
                "second-reviewer",
                List.of("ROLE_ADMIN"),
                "VALIDATED",
                null,
                null
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Vous devez prendre cette preuve en review avant de la juger.");
    }

    @Test
    void refusesDecisionWhenSubmissionWasAlreadyDecided() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("VALIDATED");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.decideSubmission(
                submissionId,
                "admin-sub",
                List.of("ROLE_ADMIN"),
                "REJECTED",
                "Correction",
                null
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cette soumission a déjà été traitée.");
    }

    @Test
    void playerMessagesAnonymizeModerators() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        player.setEmail("player@example.com");
        player.setDisplayName("Player");
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player);
        ChallengeSubmissionMessage moderatorMessage = message(
                submission,
                null,
                "admin-sub",
                "Admin Real Name",
                ChallengeSubmissionMessage.AUTHOR_MODERATOR,
                "Il manque un détail."
        );

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMessageRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
                .thenReturn(List.of(moderatorMessage));

        var messages = challengeService.getPlayerSubmissionMessages(submissionId, "player-sub");

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.authorType()).isEqualTo(ChallengeSubmissionMessage.AUTHOR_MODERATOR);
            assertThat(message.authorUserId()).isNull();
            assertThat(message.authorName()).isEqualTo("Équipe de modération");
            assertThat(message.content()).isEqualTo("Il manque un détail.");
        });
    }

    @Test
    void moderationMessagesExposeRealAuthors() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        player.setEmail("player@example.com");
        player.setDisplayName("Player");
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player);
        submission.setChallenge(challenge(UUID.randomUUID()));
        ChallengeSubmissionMessage playerMessage = message(
                submission,
                player,
                "player-sub",
                "Player",
                ChallengeSubmissionMessage.AUTHOR_PLAYER,
                "Voici une précision."
        );

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMessageRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
                .thenReturn(List.of(playerMessage));

        var messages = challengeService.getModerationSubmissionMessages(submissionId, "admin-sub", List.of("ROLE_ADMIN"));

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.authorType()).isEqualTo(ChallengeSubmissionMessage.AUTHOR_PLAYER);
            assertThat(message.authorUserId()).isEqualTo(player.getId());
            assertThat(message.authorName()).isEqualTo("Player");
            assertThat(message.content()).isEqualTo("Voici une précision.");
        });
    }

    @Test
    void playerCanCreateSubmissionMessage() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        player.setEmail("player@example.com");
        player.setDisplayName("Player");
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player);
        submission.setMessagesEnabled(true);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMessageRepository.save(any(ChallengeSubmissionMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        challengeService.createPlayerSubmissionMessage(
                submissionId,
                "player-sub",
                new SubmissionMessageCreateDto("  Bonjour  ")
        );

        ArgumentCaptor<ChallengeSubmissionMessage> messageCaptor = ArgumentCaptor.forClass(ChallengeSubmissionMessage.class);
        verify(submissionMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getAuthorType()).isEqualTo(ChallengeSubmissionMessage.AUTHOR_PLAYER);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("Bonjour");
    }

    @Test
    void playerCannotCreateMessageAfterModeratorClosesDiscussion() {
        UUID submissionId = UUID.randomUUID();
        AppUser player = player(UUID.randomUUID());
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player);
        submission.setMessagesEnabled(false);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.createPlayerSubmissionMessage(
                submissionId,
                "player-sub",
                new SubmissionMessageCreateDto("Bonjour")
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("La discussion n'est pas ouverte aux joueurs.");
    }

    @Test
    void newSubmissionOpensMessagesByDefault() {
        assertThat(new ChallengeSubmission().getMessagesEnabled()).isTrue();
    }

    @Test
    void moderatorMessageKeepsClosedDiscussionClosed() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setMessagesEnabled(false);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMessageRepository.save(any(ChallengeSubmissionMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        challengeService.createModerationSubmissionMessage(
                submissionId,
                "admin-sub",
                List.of("ROLE_ADMIN"),
                new SubmissionMessageCreateDto("Peux-tu préciser ?")
        );

        assertThat(submission.getMessagesEnabled()).isFalse();
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void cannotCreateMessageOnRejectedSubmission() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("REJECTED");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.createModerationSubmissionMessage(
                submissionId,
                "admin-sub",
                List.of("ROLE_ADMIN"),
                new SubmissionMessageCreateDto("Message")
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Impossible de commenter une soumission refusée.");
    }

    @Test
    void moderatorCanDisableSubmissionMessages() {
        UUID submissionId = UUID.randomUUID();
        ChallengeSubmission submission = submission("PENDING");
        submission.setUser(player(UUID.randomUUID()));
        submission.setChallenge(challenge(UUID.randomUUID()));
        submission.setMessagesEnabled(true);

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        challengeService.updateSubmissionMessagesEnabled(
                submissionId,
                "admin-sub",
                List.of("ROLE_ADMIN"),
                false
        );

        assertThat(submission.getMessagesEnabled()).isFalse();
        verify(submissionRepository).save(submission);
    }

    @Test
    void moderationSubmissionDetailAggregatesChallengeProofParticipantsAndMessages() {
        UUID submissionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID submitterDepartmentId = UUID.randomUUID();
        AppUser submitter = player(UUID.randomUUID());
        submitter.setEmail("alice@example.com");
        submitter.setDisplayName("Alice");
        submitter.setDepartment(submitterDepartmentId);
        AppUser participant = player(UUID.randomUUID());
        participant.setEmail("bob@example.com");
        participant.setDisplayName("Bob");
        Challenge challenge = challenge(challengeId);
        challenge.setTitle("Équations linéaires");
        challenge.setDescription("Résoudre correctement l'exercice.");
        challenge.setCategory("Mathématiques");
        challenge.setPointsReward(150);
        challenge.setMinGroupSize(1);
        challenge.setMaxGroupSize(3);
        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(submitter);
        submission.setParticipants(java.util.Set.of(participant));
        submission.setChallenge(challenge);
        submission.setProofType("MEDIA");
        submission.setProofData("proofs/file.jpg");
        submission.setComment("Voici ma preuve");
        submission.setBonusPoints(10);
        submission.setMessagesEnabled(true);
        submission.setReviewLockedBy("admin-sub");
        submission.setReviewStartedAt(Instant.now());
        Department department = new Department();
        department.setId(submitterDepartmentId);
        department.setName("Département Alpha");
        ChallengeSubmissionMessage message = message(
                submission,
                participant,
                "player-sub",
                "Bob",
                ChallengeSubmissionMessage.AUTHOR_PLAYER,
                "Je vais corriger."
        );

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(departmentRepository.findById(submitterDepartmentId)).thenReturn(Optional.of(department));
        when(proofStorageService.parseProofFiles("proofs/file.jpg")).thenReturn(List.of("proofs/file.jpg"));
        when(submissionMessageRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId))
                .thenReturn(List.of(message));

        var detail = challengeService.getModerationSubmissionDetail(submissionId, "admin-sub", List.of("ROLE_ADMIN"));

        assertThat(detail.submissionId()).isEqualTo(submissionId);
        assertThat(detail.submitterName()).isEqualTo("Alice");
        assertThat(detail.submitterDepartment()).isEqualTo("Département Alpha");
        assertThat(detail.proofData()).isNull();
        assertThat(detail.proofMedia()).hasSize(1);
        assertThat(detail.proofMedia().getFirst().index()).isZero();
        assertThat(detail.bonusPoints()).isEqualTo(10);
        assertThat(detail.messagesEnabled()).isTrue();
        assertThat(detail.reviewLocked()).isTrue();
        assertThat(detail.reviewLockedByCurrentUser()).isTrue();
        assertThat(detail.challenge().id()).isEqualTo(challengeId);
        assertThat(detail.challenge().title()).isEqualTo("Équations linéaires");
        assertThat(detail.challenge().pointsReward()).isEqualTo(150);
        assertThat(detail.participants()).extracting("displayName").containsExactlyInAnyOrder("Alice", "Bob");
        assertThat(detail.messages()).singleElement().satisfies(dto -> {
            assertThat(dto.authorName()).isEqualTo("Bob");
            assertThat(dto.content()).isEqualTo("Je vais corriger.");
        });
    }

    @Test
    void refusesChallengeWhenMinGroupSizeIsGreaterThanMaxGroupSize() {
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo equipe",
                "Une preuve en groupe",
                10,
                "photo",
                null,
                null,
                null,
                null,
                null,
                "ADMIN",
                null,
                null,
                null,
                false, false,
                3,
                2,
                List.of(),
                List.of()
        , null);

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Configuration de groupe invalide.")
                .satisfies(exception -> assertThat(((BadRequestException) exception).getFieldErrors())
                        .containsExactly(new fr.insastronaute.webapi.common.web.ApiErrorResponse.FieldViolation(
                                "maxGroupSize",
                                "La taille maximale du groupe doit être supérieure ou égale à la taille minimale."
                        )));

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void refusesChallengeWhenValidationModeIsInvalid() {
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo equipe",
                "Une preuve en groupe",
                10,
                "photo",
                null,
                null,
                null,
                null,
                null,
                "UNKNOWN",
                null,
                null,
                null,
                false, false,
                1,
                1,
                List.of(),
                List.of()
        , null);

        assertThatThrownBy(() -> challengeService.createChallenge(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mode de validation invalide.");

        verify(challengeRepository, never()).save(any());
    }

    // ── Récompense partagée ──────────────────────────────────────────────────

    private ChallengeSubmission sharedSubmission(Challenge challenge, AppUser author, AppUser... teammates) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setStatus("VALIDATED");
        submission.setParticipants(new java.util.LinkedHashSet<>(java.util.List.of(teammates)));
        return submission;
    }

    private AppUser named(String name) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        user.setChallengePoints(0L);
        user.setChallengeTokensGranted(0L);
        user.setTokens(0L);
        return user;
    }

    /**
     * Dix points pour trois joueurs font 4, 3 et 3 — pas 3, 3 et 3.
     *
     * <p>Le reste va aux premiers plutôt que de s'évaporer. Un point valant un token, le
     * même partage vaut pour les deux.</p>
     */
    @Test
    void aSharedRewardIsDividedAndTheRemainderGoesToTheFirst() {
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(10);
        challenge.setRewardShared(true);
        AppUser alice = named("Alice");
        AppUser bruno = named("Bruno");
        AppUser chloe = named("Chloe");

        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(sharedSubmission(challenge, alice, bruno, chloe)));
        when(userRepository.findAll()).thenReturn(List.of(alice, bruno, chloe));

        challengeService.recalculateChallengePoints();

        assertThat(alice.getChallengePoints()).isEqualTo(4L);
        assertThat(bruno.getChallengePoints()).isEqualTo(3L);
        assertThat(chloe.getChallengePoints()).isEqualTo(3L);
        // Un point vaut un token : le partage vaut pour les deux, sans réglage séparé.
        assertThat(alice.getTokens()).isEqualTo(4L);
        assertThat(bruno.getTokens()).isEqualTo(3L);
        // Et rien ne se perd : la somme fait bien la mise.
        assertThat(alice.getChallengePoints() + bruno.getChallengePoints() + chloe.getChallengePoints())
                .isEqualTo(10L);
    }

    /**
     * Deux recalculs successifs donnent le même partage.
     *
     * <p>Les points sont recalculés de zéro à chaque validation. Un partage qui dépendrait
     * d'un ordre instable ferait bouger les soldes sans raison visible, et l'écart serait
     * introuvable.</p>
     */
    @Test
    void theSameShareIsComputedTwice() {
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(10);
        challenge.setRewardShared(true);
        AppUser alice = named("Alice");
        AppUser bruno = named("Bruno");
        AppUser chloe = named("Chloe");

        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(sharedSubmission(challenge, alice, bruno, chloe)));
        when(userRepository.findAll()).thenReturn(List.of(alice, bruno, chloe));

        challengeService.recalculateChallengePoints();
        challengeService.recalculateChallengePoints();

        assertThat(alice.getChallengePoints()).isEqualTo(4L);
        assertThat(alice.getTokens()).isEqualTo(4L);
    }

    /** Sans le drapeau, chacun touche la mise entière : rien ne change pour les autres défis. */
    @Test
    void anUnsharedRewardStillGoesWholeToEveryMember() {
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(10);
        AppUser alice = named("Alice");
        AppUser bruno = named("Bruno");

        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(sharedSubmission(challenge, alice, bruno)));
        when(userRepository.findAll()).thenReturn(List.of(alice, bruno));

        challengeService.recalculateChallengePoints();

        assertThat(alice.getChallengePoints()).isEqualTo(10L);
        assertThat(bruno.getChallengePoints()).isEqualTo(10L);
    }

    /** Seul, on ne partage avec personne. */
    @Test
    void aSoloPlayerKeepsTheWholeSharedReward() {
        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setPointsReward(10);
        challenge.setRewardShared(true);
        AppUser alice = named("Alice");

        when(submissionRepository.findValidatedSubmissionsForPointRecalculation())
                .thenReturn(List.of(sharedSubmission(challenge, alice)));
        when(userRepository.findAll()).thenReturn(List.of(alice));

        challengeService.recalculateChallengePoints();

        assertThat(alice.getChallengePoints()).isEqualTo(10L);
    }

    private static AppUser player(UUID playerId) {
        AppUser player = new AppUser();
        player.setId(playerId);
        player.setKeycloakSub("player-sub");
        player.setDepartment(UUID.randomUUID());

        return player;
    }

    /**
     * Changer les points d'un defi recredite tous ses participants : la ligne
     * du registre nomme le defi et combien de joueurs sont credites, pas un
     * joueur.
     */
    @Test
    void changingChallengePointsWritesOneGlobalRowNamingTheChallenge() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo equipe", "Une preuve en groupe", 30, "photo", null,
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(submissionRepository.countPlayersCreditedByChallenge(challengeId)).thenReturn(17L);

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(journal).recordGlobal(
                eq(ADMIN_SUB),
                eq(AdminActionType.CHALLENGE_POINTS_CHANGED),
                details.capture());
        assertThat(details.getValue())
                .containsEntry("scope", "CHALLENGE")
                .containsEntry("challengeId", challengeId.toString())
                .containsEntry("challengeTitle", "Photo equipe")
                .containsEntry("fromPoints", 10)
                .containsEntry("toPoints", 30)
                .containsEntry("affectedPlayers", 17L);
    }

    /**
     * Un defi reenregistre avec les memes points n'a deplace aucun point, meme
     * si son titre a bouge : rien a journaliser.
     */
    @Test
    void reSavingAChallengeWithTheSamePointsWritesNothing() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        ChallengeCreateDto dto = new ChallengeCreateDto(
                "Photo equipe renommee", "Une preuve en groupe", 10, "photo", null,
                null, null, null, null, "ADMIN", null, null, null, false, false, 1, 1, List.of(), List.of(), null);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(challenge)).thenReturn(challenge);

        challengeService.updateChallenge(challengeId, dto, ADMIN_SUB);

        verify(journal, never()).recordGlobal(any(), any(), any());
        verify(submissionRepository, never()).countPlayersCreditedByChallenge(any());
    }

    /**
     * Supprimer un defi retire ses points a chacun de ses participants : le
     * recalcul qui suit recompte le score de chacun sans lui. Une ligne de
     * portee globale, comme un changement de points.
     */
    @Test
    void deletingAChallengeWritesOneGlobalRowNamingTheChallenge() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.countPlayersCreditedByChallenge(challengeId)).thenReturn(23L);

        challengeService.deleteChallenge(challengeId, ADMIN_SUB);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(journal).recordGlobal(
                eq(ADMIN_SUB),
                eq(AdminActionType.CHALLENGE_DELETED),
                details.capture());
        assertThat(details.getValue())
                .containsEntry("scope", "CHALLENGE")
                .containsEntry("challengeId", challengeId.toString())
                .containsEntry("challengeTitle", "Defi")
                .containsEntry("points", 10)
                .containsEntry("affectedPlayers", 23L);
    }

    /**
     * La ligne est comptee et ecrite avant que le defi sorte des listes.
     *
     * <p>Compter apres donnerait zero joueur touche sur un geste qui en a appauvri
     * vingt-trois : le recalcul qui suit la marque cesse de compter ce defi, donc
     * {@code countPlayersCreditedByChallenge} repondrait sur un defi deja hors
     * jeu.</p>
     */
    @Test
    void theDeletionRowIsCountedAndWrittenBeforeTheChallengeLeaves() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(submissionRepository.countPlayersCreditedByChallenge(challengeId)).thenReturn(23L);

        challengeService.deleteChallenge(challengeId, ADMIN_SUB);

        InOrder order = inOrder(submissionRepository, journal, challengeRepository);
        order.verify(submissionRepository).countPlayersCreditedByChallenge(challengeId);
        order.verify(journal).recordGlobal(eq(ADMIN_SUB), eq(AdminActionType.CHALLENGE_DELETED), anyMap());
        order.verify(challengeRepository).save(challenge);
    }

    /**
     * Rien n'est efface, et c'est tout l'enjeu.
     *
     * <p>Le geste effacait le defi et la base cascadait : preuves, conversations,
     * participations, reclamations. Un clic de trop etait sans retour. La suppression
     * pose une date, et la restauration la retire.</p>
     */
    @Test
    void deletingAChallengeErasesNothing() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        challengeService.deleteChallenge(challengeId, ADMIN_SUB);

        verify(challengeRepository, never()).deleteById(any());
        verify(challengeRepository, never()).delete(any());
        assertThat(challenge.getDeletedAt()).isNotNull();
        assertThat(challenge.getDeletedByKeycloakSub()).isEqualTo(ADMIN_SUB);
    }

    /**
     * Restaurer rend le defi, ses preuves et les points qu'elles donnaient.
     *
     * <p>Rien a reconstruire : la suppression n'avait rien efface. Le recalcul qui
     * suit rend leurs points a ceux qui avaient valide, puisque la requete cesse
     * d'ecarter ce defi.</p>
     */
    @Test
    void restoringAChallengeBringsBackItsPointsAndWritesItsOwnRow() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = challenge(challengeId);
        challenge.setDeletedAt(java.time.Instant.parse("2026-09-11T10:00:00Z"));
        challenge.setDeletedByKeycloakSub("autre-admin");
        when(challengeRepository.findDeletedById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(submissionRepository.countPlayersCreditedByChallenge(challengeId)).thenReturn(23L);

        challengeService.restoreChallenge(challengeId, ADMIN_SUB);

        assertThat(challenge.getDeletedAt()).isNull();
        assertThat(challenge.getDeletedByKeycloakSub()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(journal).recordGlobal(
                eq(ADMIN_SUB), eq(AdminActionType.CHALLENGE_RESTORED), details.capture());
        // Le meme chiffre que la ligne de suppression : les deux se lisent en miroir.
        assertThat(details.getValue())
                .containsEntry("challengeId", challengeId.toString())
                .containsEntry("affectedPlayers", 23L);
    }

    /** Ce qui n'a jamais ete supprime ne se restaure pas. */
    @Test
    void restoringAChallengeThatIsNotDeletedWritesNothing() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findDeletedById(challengeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> challengeService.restoreChallenge(challengeId, ADMIN_SUB))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(journal);
    }

    /** Un defi introuvable n'est pas un geste : rien n'est marque, rien n'est ecrit. */
    @Test
    void deletingAMissingChallengeWritesNothing() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> challengeService.deleteChallenge(challengeId, ADMIN_SUB))
                .isInstanceOf(NotFoundException.class);

        verify(challengeRepository, never()).save(any());
        verifyNoInteractions(journal);
    }

    /**
     * Un defi neuf n'a aucune soumission validee : le recalcul qui suit ne
     * recredite personne, et le bareme n'a pas d'avant.
     */
    @Test
    void creatingAChallengeWritesNothing() {
        ChallengeCreateDto dto = challengeDto("ADMIN", null, (java.util.Map<String, Object>) null);
        when(categoryRepository.existsById("photo")).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        challengeService.createChallenge(dto);

        // Pas seulement "aucun recordGlobal" : aucune ecriture du tout, quelle
        // que soit la porte empruntee - c'est ce que dit le nom du test.
        verifyNoInteractions(journal);
    }

    /**
     * Les paramètres traversent la couche HTTP en table, pas en arbre Jackson 2 :
     * les tests continuent de les écrire en JSON, et la conversion se fait ici comme
     * elle se fait dans le convertisseur HTTP.
     */
    private static ChallengeCreateDto challengeDto(
            String validationMode,
            String autoChallengeType,
            JsonNode autoChallengeParameters
    ) {
        return challengeDto(validationMode, autoChallengeType,
                fr.insastronaute.webapi.common.json.JsonPayloads.toMap(autoChallengeParameters));
    }

    private static ChallengeCreateDto challengeDto(
            String validationMode,
            String autoChallengeType,
            java.util.Map<String, Object> autoChallengeParameters
    ) {
        return new ChallengeCreateDto(
                "Photo equipe",
                "Une preuve en groupe",
                10,
                "photo",
                null,
                null,
                null,
                null,
                null,
                validationMode,
                null,
                autoChallengeType,
                autoChallengeParameters,
                false, false,
                1,
                1,
                List.of(),
                List.of()
        , null);
    }

    private record PermanentQrScannedParameters() {
    }

    /** Paramètres d'un type qui compte : « en être à `current` sur `target` ». */
    private record CountingParameters(int current, int target) {
    }

    /**
     * Un type automatique dont l'avancement se lit dans ses paramètres.
     *
     * <p>Le compteur réel dépend de la base ; ici on veut seulement vérifier ce
     * que la mise en avant fait d'un compteur donné.</p>
     */
    private static final class CountingType implements AutoChallengeType<CountingParameters> {

        @Override
        public String key() {
            return "COUNTING";
        }

        @Override
        public String publicName() {
            return "Compteur";
        }

        @Override
        public String description() {
            return "Type de test qui sait dire ou en est le joueur.";
        }

        @Override
        public Class<CountingParameters> parametersType() {
            return CountingParameters.class;
        }

        @Override
        public List<AutoChallengeParameter> parameters() {
            return List.of();
        }

        @Override
        public AutoChallengeCandidatePage findCandidates(CountingParameters parameters, AutoChallengeCandidateQuery query) {
            return new AutoChallengeCandidatePage(List.of(), null);
        }

        @Override
        public AutoChallengeProgress progress(CountingParameters parameters, UUID userId) {
            return AutoChallengeProgress.of(parameters.current(), parameters.target());
        }
    }

    private static final class PermanentQrScannedType implements AutoChallengeType<PermanentQrScannedParameters> {

        @Override
        public String key() {
            return "PERMANENT_QR_SCANNED";
        }

        @Override
        public String publicName() {
            return "QR permanent scanne";
        }

        @Override
        public String description() {
            return "Valide le defi lorsque le QR permanent est scanne.";
        }

        @Override
        public Class<PermanentQrScannedParameters> parametersType() {
            return PermanentQrScannedParameters.class;
        }

        @Override
        public List<AutoChallengeParameter> parameters() {
            return List.of();
        }

        @Override
        public void validate(PermanentQrScannedParameters parameters) {
        }

        @Override
        public AutoChallengeCandidatePage findCandidates(PermanentQrScannedParameters parameters, AutoChallengeCandidateQuery query) {
            return new AutoChallengeCandidatePage(List.of(), null);
        }
    }

    /** Un défi qui se valide par une preuve écrite. */
    private static Challenge textChallenge(UUID challengeId) {
        Challenge challenge = challenge(challengeId);
        challenge.setProofType(ProofType.TEXT.name());
        return challenge;
    }

    private static Challenge challenge(UUID challengeId) {
        Challenge challenge = new Challenge();
        challenge.setId(challengeId);
        // NOT NULL en base (V4__challenges_create.sql) : un titre absent ne
        // devrait jamais arriver en production, decideSubmission ne le protege
        // donc pas quand il journalise la decision.
        challenge.setTitle("Defi");
        challenge.setValidationMode("ADMIN");
        challenge.setPointsReward(10);

        return challenge;
    }

    /**
     * Le cout d'une page de defis ne doit pas dependre du nombre de defis.
     *
     * <p>Chaque ligne coutait deux requetes au moins - le chargement paresseux de ses
     * prerequis, ses soumissions - plus une par prerequis. A quatre-vingts defis, cela
     * faisait pres de deux cents requetes pour une ouverture, par joueur, sur la page
     * la plus consultee d'une inte.</p>
     */
    @Test
    void aPageOfChallengesCostsTheSameWhateverItsLength() {
        UUID playerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setDepartment(departmentId);
        List<Challenge> challenges = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> challenge(UUID.randomUUID()))
                .toList();

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(challengeRepository.findChallengesForDepartment(departmentId)).thenReturn(challenges);

        var result = challengeService.getPlayerChallenges("player-sub");

        assertThat(result).hasSize(15);
        verify(submissionRepository, times(1)).findByUserIdOrParticipantUserId(playerId);
        verify(submissionRepository, times(1)).validatedChallengeIdsForUser(playerId);
        verify(submissionRepository, never()).findByUserIdOrParticipantUserIdAndChallengeId(any(), any());
        verify(submissionRepository, never()).existsValidatedSubmissionForUserAndChallenge(any(), any());
    }

    @Test
    void revokeValidatedSubmissionRejectsAndRecalculatesPointsAndTokens() {
        UUID submissionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);
        player.setChallengePoints(20L);
        player.setChallengeTokensGranted(20L);
        player.setTokens(20L);

        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Défi Test");
        challenge.setPointsReward(15);

        ChallengeSubmission submission = submission("VALIDATED");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setBonusPoints(5);
        submission.setValidatedAt(Instant.now());
        submission.setReviewLockedBy("admin-sub");

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        // When rejected, findValidatedSubmissionsForPointRecalculation will not return it anymore
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(player));

        challengeService.revokeValidatedSubmission(playerId, submissionId, "Preuve non recevable", "admin-sub");

        assertThat(submission.getStatus()).isEqualTo("REJECTED");
        assertThat(submission.getBonusPoints()).isEqualTo(0);
        assertThat(submission.getValidatedAt()).isNull();
        assertThat(submission.getModerationComment()).isEqualTo("Preuve non recevable");
        assertThat(submission.getReviewLockedBy()).isNull();

        // Check moderation message created
        ArgumentCaptor<ChallengeSubmissionMessage> msgCaptor = ArgumentCaptor.forClass(ChallengeSubmissionMessage.class);
        verify(submissionMessageRepository).save(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getContent()).isEqualTo("Preuve non recevable");
        assertThat(msgCaptor.getValue().getAuthorType()).isEqualTo(ChallengeSubmissionMessage.AUTHOR_MODERATOR);

        // Check journal recorded
        verify(journal).record(
                eq("admin-sub"),
                eq(playerId),
                eq(AdminActionType.CHALLENGE_REJECTED),
                argThat(map -> "Défi Test".equals(map.get("challengeTitle"))
                        && Integer.valueOf(15).equals(map.get("points"))
                        && "Preuve non recevable".equals(map.get("comment")))
        );

        // Check points and tokens recalculated to 0
        assertThat(player.getChallengePoints()).isEqualTo(0L);
        assertThat(player.getTokens()).isEqualTo(0L);
    }

    @Test
    void revokeValidatedSubmissionFailsIfStatusNotValidated() {
        UUID submissionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        AppUser player = player(playerId);

        ChallengeSubmission submission = submission("PENDING");
        submission.setId(submissionId);
        submission.setUser(player);
        submission.setChallenge(challenge(UUID.randomUUID()));

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.revokeValidatedSubmission(playerId, submissionId, "Commentaire", "admin-sub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Seule une soumission validée peut être refusée.");
    }

    @Test
    void revokeValidatedSubmissionFailsIfUserNotMember() {
        UUID submissionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        AppUser owner = player(ownerId);

        ChallengeSubmission submission = submission("VALIDATED");
        submission.setId(submissionId);
        submission.setUser(owner);
        submission.setChallenge(challenge(UUID.randomUUID()));

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> challengeService.revokeValidatedSubmission(otherUserId, submissionId, "Commentaire", "admin-sub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La soumission n'appartient pas à cet utilisateur.");
    }

    @Test
    void revokeValidatedSubmissionSucceedsWhenUserIsParticipant() {
        UUID submissionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        AppUser owner = player(ownerId);
        AppUser participant = player(participantId);

        Challenge challenge = challenge(UUID.randomUUID());
        challenge.setTitle("Défi d'équipe");
        challenge.setPointsReward(10);

        ChallengeSubmission submission = submission("VALIDATED");
        submission.setId(submissionId);
        submission.setUser(owner);
        submission.setParticipants(Set.of(participant));
        submission.setChallenge(challenge);

        when(submissionRepository.findByIdForUpdate(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(owner, participant));

        challengeService.revokeValidatedSubmission(participantId, submissionId, "Refus depuis fiche coéquipier", "admin-sub");

        assertThat(submission.getStatus()).isEqualTo("REJECTED");
        assertThat(submission.getModerationComment()).isEqualTo("Refus depuis fiche coéquipier");
        verify(submissionRepository).save(submission);
    }

    private static ChallengeSubmission submission(String status) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setStatus(status);

        return submission;
    }

    /**
     * Une soumission rattachee a son defi.
     *
     * <p>La page lit desormais toutes les soumissions du joueur en une fois et les
     * regroupe par defi : une soumission sans defi, que la colonne interdit en base,
     * n'a plus de sens ici.</p>
     */
    private static ChallengeSubmission submission(String status, Challenge challenge) {
        ChallengeSubmission submission = submission(status);
        submission.setChallenge(challenge);

        return submission;
    }

    private static ChallengeSubmissionMessage message(
            ChallengeSubmission submission,
            AppUser author,
            String keycloakSub,
            String displayName,
            String authorType,
            String content
    ) {
        ChallengeSubmissionMessage message = new ChallengeSubmissionMessage();
        message.setSubmission(submission);
        message.setAuthor(author);
        message.setAuthorKeycloakSub(keycloakSub);
        message.setAuthorDisplayName(displayName);
        message.setAuthorType(authorType);
        message.setContent(content);
        return message;
    }

    private static ChallengeSubmissionRepository.ParticipantConfirmationProjection participantConfirmation(
            UUID userId,
            String displayName,
            Instant invitedAt
    ) {
        return new ChallengeSubmissionRepository.ParticipantConfirmationProjection() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public String getEmail() {
                return "participant@example.com";
            }

            @Override
            public String getConfirmationStatus() {
                return "PENDING";
            }

            @Override
            public Instant getInvitedAt() {
                return invitedAt;
            }

            @Override
            public Instant getRespondedAt() {
                return null;
            }
        };
    }

    private static ChallengeFavorite favorite(AppUser player, Challenge challenge) {
        ChallengeFavorite favorite = new ChallengeFavorite();
        favorite.setUser(player);
        favorite.setChallenge(challenge);

        return favorite;
    }

    @Test
    void getChallengeIdsFiltersMatchingCriteria() {
        Instant now = Instant.parse("2026-09-07T12:00:00Z");
        TimeTravelContext.setForcedTime(now);

        Challenge challenge1 = challenge(UUID.randomUUID());
        challenge1.setTitle("Défi Photo");
        challenge1.setStartDate(now.minus(Duration.ofDays(1)));
        challenge1.setEndDate(now.plus(Duration.ofDays(2)));
        challenge1.setCategory("art");
        challenge1.setValidationMode("ADMIN");

        Challenge challenge2 = challenge(UUID.randomUUID());
        challenge2.setTitle("Grand Concours Photo");
        challenge2.setStartDate(now.minus(Duration.ofDays(3)));
        challenge2.setEndDate(now.plus(Duration.ofDays(1)));
        challenge2.setCategory("art");
        challenge2.setValidationMode("ADMIN");

        Challenge challenge3 = challenge(UUID.randomUUID());
        challenge3.setTitle("Tournoi Sport");
        challenge3.setStartDate(now.minus(Duration.ofDays(1)));
        challenge3.setEndDate(now.plus(Duration.ofDays(1)));
        challenge3.setCategory("art");
        challenge3.setValidationMode("ADMIN");

        Challenge challenge4 = challenge(UUID.randomUUID());
        challenge4.setTitle("Photo Tech");
        challenge4.setStartDate(now.minus(Duration.ofDays(1)));
        challenge4.setEndDate(now.plus(Duration.ofDays(1)));
        challenge4.setCategory("tech");
        challenge4.setValidationMode("ADMIN");

        Challenge challenge5 = challenge(UUID.randomUUID());
        challenge5.setTitle("Photo Draft");
        challenge5.setStartDate(now.minus(Duration.ofDays(1)));
        challenge5.setEndDate(null);
        challenge5.setCategory("art");
        challenge5.setValidationMode("ADMIN");

        when(challengeRepository.findAll()).thenReturn(List.of(challenge3, challenge2, challenge5, challenge1, challenge4));

        List<UUID> ids = challengeService.getChallengeIds("photo", "active", "art", "ADMIN");

        assertThat(ids).containsExactly(challenge1.getId(), challenge2.getId());
    }

    @Test
    void bulkUpdateChallengesAppliesSelectiveFields() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Instant initialStart = Instant.parse("2026-09-01T10:00:00Z");
        Instant initialEnd = Instant.parse("2026-09-10T10:00:00Z");
        Instant initialPublishedAt = Instant.parse("2026-08-31T10:00:00Z");

        Challenge challenge1 = challenge(id1);
        challenge1.setStartDate(initialStart);
        challenge1.setEndDate(initialEnd);
        challenge1.setPublishedAt(initialPublishedAt);
        challenge1.setCategory("sport");

        Challenge challenge2 = challenge(id2);
        challenge2.setStartDate(initialStart);
        challenge2.setEndDate(initialEnd);
        challenge2.setPublishedAt(initialPublishedAt);
        challenge2.setCategory("sport");

        when(challengeRepository.findAllById(List.of(id1, id2))).thenReturn(List.of(challenge1, challenge2));
        when(categoryRepository.existsById("culture")).thenReturn(true);
        when(categoryRepository.existsById("fun")).thenReturn(true);

        Instant newStart = Instant.parse("2026-09-05T10:00:00Z");
        Instant ignoredEnd = Instant.parse("2026-09-20T10:00:00Z");
        Instant ignoredPublishedAt = Instant.parse("2026-09-04T10:00:00Z");

        ChallengeBulkUpdateDto dto = new ChallengeBulkUpdateDto(
                List.of(id1, id2),
                true, newStart,
                false, ignoredEnd,
                false, ignoredPublishedAt,
                true, List.of("culture", "fun")
        , false);

        ChallengeBulkUpdateResultDto result = challengeService.bulkUpdateChallenges(dto, ADMIN_SUB);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.updatedIds()).containsExactly(id1, id2);

        assertThat(challenge1.getStartDate()).isEqualTo(newStart);
        assertThat(challenge1.getEndDate()).isEqualTo(initialEnd);
        assertThat(challenge1.getPublishedAt()).isEqualTo(initialPublishedAt);
        assertThat(challenge1.getCategory()).isEqualTo("culture");
        assertThat(challenge1.getExtraCategories()).containsExactly("fun");

        assertThat(challenge2.getStartDate()).isEqualTo(newStart);
        assertThat(challenge2.getEndDate()).isEqualTo(initialEnd);
        assertThat(challenge2.getPublishedAt()).isEqualTo(initialPublishedAt);
        assertThat(challenge2.getCategory()).isEqualTo("culture");
        assertThat(challenge2.getExtraCategories()).containsExactly("fun");

        verify(challengeRepository).save(challenge1);
        verify(challengeRepository).save(challenge2);
    }

    @Test
    void bulkUpdateChallengesClearsDateWhenRequested() {
        UUID id = UUID.randomUUID();
        Challenge challenge = challenge(id);
        challenge.setStartDate(Instant.parse("2026-09-01T10:00:00Z"));
        challenge.setEndDate(Instant.parse("2026-09-10T10:00:00Z"));
        challenge.setPublishedAt(Instant.parse("2026-08-31T10:00:00Z"));
        challenge.setCategory("photo");

        when(challengeRepository.findAllById(List.of(id))).thenReturn(List.of(challenge));

        ChallengeBulkUpdateDto dto = new ChallengeBulkUpdateDto(
                List.of(id),
                true, null,
                true, null,
                true, null,
                false, null
        , false);

        ChallengeBulkUpdateResultDto result = challengeService.bulkUpdateChallenges(dto, ADMIN_SUB);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.updatedIds()).containsExactly(id);
        assertThat(challenge.getStartDate()).isNull();
        assertThat(challenge.getEndDate()).isNull();
        assertThat(challenge.getPublishedAt()).isNull();

        verify(challengeRepository).save(challenge);
    }

    @Test
    void bulkUpdateChallengesRollsBackWhenDailyMissingEndDate() {
        UUID id = UUID.randomUUID();
        Challenge dailyChallenge = challenge(id);
        dailyChallenge.setCategory(ChallengeCategory.DAILY_NAME);
        dailyChallenge.setStartDate(Instant.parse("2026-09-01T10:00:00Z"));
        dailyChallenge.setEndDate(Instant.parse("2026-09-02T10:00:00Z"));

        when(challengeRepository.findAllById(List.of(id))).thenReturn(List.of(dailyChallenge));

        ChallengeBulkUpdateDto dto = new ChallengeBulkUpdateDto(
                List.of(id),
                false, null,
                true, null,
                false, null,
                false, null
        , false);

        assertThatThrownBy(() -> challengeService.bulkUpdateChallenges(dto, ADMIN_SUB))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Date de fin requise pour un défi du jour");

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void bulkUpdateChallengesRollsBackWhenStartDateAfterEndDate() {
        UUID id = UUID.randomUUID();
        Challenge challenge = challenge(id);
        challenge.setStartDate(Instant.parse("2026-09-01T10:00:00Z"));
        challenge.setEndDate(Instant.parse("2026-09-05T10:00:00Z"));

        when(challengeRepository.findAllById(List.of(id))).thenReturn(List.of(challenge));

        ChallengeBulkUpdateDto dto = new ChallengeBulkUpdateDto(
                List.of(id),
                true, Instant.parse("2026-09-10T10:00:00Z"),
                false, null,
                false, null,
                false, null
        , false);

        assertThatThrownBy(() -> challengeService.bulkUpdateChallenges(dto, ADMIN_SUB))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Fenêtre de défi invalide");

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void bulkUpdateChallengesRollsBackWhenAnyChallengeNotFound() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Challenge challenge1 = challenge(id1);

        when(challengeRepository.findAllById(List.of(id1, id2))).thenReturn(List.of(challenge1));

        ChallengeBulkUpdateDto dto = new ChallengeBulkUpdateDto(
                List.of(id1, id2),
                false, null,
                false, null,
                false, null,
                false, null
        , false);

        assertThatThrownBy(() -> challengeService.bulkUpdateChallenges(dto, ADMIN_SUB))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Un ou plusieurs défis sont introuvables.");

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void bulkUpdateChallengesHandlesDuplicateIdsInRequestGracefully() {
        UUID id1 = UUID.randomUUID();
        Challenge challenge1 = challenge(id1);
        challenge1.setCategory("photo");

        when(challengeRepository.findAllById(List.of(id1, id1))).thenReturn(List.of(challenge1));
        when(categoryRepository.existsById("sport")).thenReturn(true);

        ChallengeBulkUpdateDto dto = new ChallengeBulkUpdateDto(
                List.of(id1, id1),
                false, null,
                false, null,
                false, null,
                true, List.of("sport")
        , false);

        ChallengeBulkUpdateResultDto result = challengeService.bulkUpdateChallenges(dto, ADMIN_SUB);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.updatedIds()).containsExactly(id1);
        assertThat(challenge1.getCategory()).isEqualTo("sport");
        verify(challengeRepository).save(challenge1);
    }

    // ---- Le quota d'un defi absorbe la recompense ------------------------------

    @Test
    void auDelaDuQuotaLaSoumissionNEstPasRecompensee() {
        UUID challengeId = UUID.randomUUID();
        Challenge quotaChallenge = challenge(challengeId);
        quotaChallenge.setGlobalQuota(2);

        ChallengeSubmission first = validatedSubmission(quotaChallenge);
        ChallengeSubmission second = validatedSubmission(quotaChallenge);
        ChallengeSubmission mine = validatedSubmission(quotaChallenge);

        when(submissionRepository.findValidatedSubmissionsForChallenge(challengeId))
                .thenReturn(List.of(first, second, mine));

        assertThat(challengeService.unrewardedSubmissionIds(List.of(mine)))
                .containsExactly(mine.getId());
    }

    @Test
    void dansLeQuotaLaSoumissionEstRecompensee() {
        UUID challengeId = UUID.randomUUID();
        Challenge quotaChallenge = challenge(challengeId);
        quotaChallenge.setGlobalQuota(2);

        ChallengeSubmission mine = validatedSubmission(quotaChallenge);
        ChallengeSubmission later = validatedSubmission(quotaChallenge);

        when(submissionRepository.findValidatedSubmissionsForChallenge(challengeId))
                .thenReturn(List.of(mine, later));

        assertThat(challengeService.unrewardedSubmissionIds(List.of(mine))).isEmpty();
    }

    @Test
    void sansQuotaAucuneRequeteEtToutEstRecompense() {
        Challenge freeChallenge = challenge(UUID.randomUUID());
        ChallengeSubmission mine = validatedSubmission(freeChallenge);

        assertThat(challengeService.unrewardedSubmissionIds(List.of(mine))).isEmpty();
        verify(submissionRepository, never()).findValidatedSubmissionsForChallenge(any());
    }

    // Une preuve encore en attente n'a rien gagne ni rien perdu : la question du
    // quota ne se pose pas, et la poser couterait une requete pour rien.
    @Test
    void uneSoumissionNonValideeNInterrogePasLeQuota() {
        Challenge quotaChallenge = challenge(UUID.randomUUID());
        quotaChallenge.setGlobalQuota(1);

        ChallengeSubmission pending = validatedSubmission(quotaChallenge);
        pending.setStatus("PENDING");

        assertThat(challengeService.unrewardedSubmissionIds(List.of(pending))).isEmpty();
        verify(submissionRepository, never()).findValidatedSubmissionsForChallenge(any());
    }

    private static ChallengeSubmission validatedSubmission(Challenge challenge) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setStatus("VALIDATED");
        return submission;
    }
}
