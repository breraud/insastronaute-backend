package fr.insastronaute.webapi.challenge.auto.award;

import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoChallengeAwardServiceTest {

    @Mock private ChallengeRepository challengeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChallengeSubmissionRepository submissionRepository;
    @Mock private ChallengeService challengeService;
    @Mock private AutoChallengeAwardAttemptService attemptService;

    private AutoChallengeAwardService service;

    @Mock
    private CardTeamMemberRepository teamMemberRepository;

    @BeforeEach
    void setUp() {
        service = new AutoChallengeAwardService(
                challengeRepository, userRepository, submissionRepository, challengeService, attemptService,
                teamMemberRepository
        );
        // Le cas courant : un défi ouvert. Les tests qui ferment le défi le disent.
        lenient().when(challengeService.isOpenForAutomaticAward(any())).thenReturn(true);
    }

    @Test
    void aChallengeThatIsNotOpenAwardsNobodyAndReadsNoCandidate() {
        Challenge scheduled = new Challenge();
        scheduled.setId(UUID.randomUUID());
        when(challengeRepository.findById(scheduled.getId())).thenReturn(Optional.of(scheduled));
        when(challengeService.isOpenForAutomaticAward(scheduled)).thenReturn(false);

        assertThat(service.awardBatch(scheduled.getId(), List.of(UUID.randomUUID()))).isZero();

        verify(userRepository, never()).findAllById(any());
        verify(challengeService, never()).isPlayerEligibleForChallenge(any(), any());
        verify(attemptService, never()).award(any(), any(), any());
    }

    @Test
    void decidesOnTheRealClockEvenUnderTimeTravelAndGivesTheForcedTimeBack() {
        // Sous un voyage d'un mois en avant, les défis du mois prochain paraissaient
        // ouverts : l'attribution qui en sortait, elle, était bien réelle.
        Fixture fixture = fixture();
        Instant forced = Instant.now().plus(Duration.ofDays(30));
        List<Instant> clockSeenByTheGate = new ArrayList<>();
        when(challengeService.isOpenForAutomaticAward(fixture.challenge())).thenAnswer(invocation -> {
            clockSeenByTheGate.add(TimeTravelContext.getForcedTime());
            return true;
        });
        when(challengeService.isPlayerEligibleForChallenge(fixture.user(), fixture.challenge())).thenAnswer(invocation -> {
            clockSeenByTheGate.add(TimeTravelContext.getForcedTime());
            return false;
        });

        TimeTravelContext.setForcedTime(forced);
        try {
            service.awardBatch(fixture.challenge().getId(), List.of(fixture.user().getId()));
            assertThat(TimeTravelContext.getForcedTime()).isEqualTo(forced);
        } finally {
            TimeTravelContext.clear();
        }

        assertThat(clockSeenByTheGate).hasSize(2).containsOnlyNulls();
    }

    @Test
    void ignoresDeletedChallenge() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.empty());

        assertThat(service.awardBatch(challengeId, List.of(UUID.randomUUID()))).isZero();

        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void ignoresInactiveChallengeThroughCommonEligibilityRules() {
        Fixture fixture = fixture();
        when(challengeService.isPlayerEligibleForChallenge(fixture.user(), fixture.challenge())).thenReturn(false);

        assertThat(service.awardBatch(fixture.challenge().getId(), List.of(fixture.user().getId()))).isZero();

        verify(attemptService, never()).award(any(), any(), any());
        verify(challengeService, never()).recalculateChallengePoints();
    }

    @Test
    void ignoresDepartmentMismatchThroughCommonEligibilityRules() {
        Fixture fixture = fixture();
        when(challengeService.isPlayerEligibleForChallenge(fixture.user(), fixture.challenge())).thenReturn(false);

        assertThat(service.awardBatch(fixture.challenge().getId(), List.of(fixture.user().getId()))).isZero();

        verify(attemptService, never()).award(any(), any(), any());
    }

    @Test
    void ignoresMissingPrerequisiteThroughCommonEligibilityRules() {
        Fixture fixture = fixture();
        when(challengeService.isPlayerEligibleForChallenge(fixture.user(), fixture.challenge())).thenReturn(false);

        assertThat(service.awardBatch(fixture.challenge().getId(), List.of(fixture.user().getId()))).isZero();

        verify(attemptService, never()).award(any(), any(), any());
    }

    @Test
    void ignoresCandidateWithExistingBlockingSubmissionUsingBulkReads() {
        Fixture fixture = fixture();
        ChallengeSubmission existing = new ChallengeSubmission();
        existing.setUser(fixture.user());
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(fixture.user().getId()), fixture.challenge().getId()
        )).thenReturn(List.of(existing));

        assertThat(service.awardBatch(fixture.challenge().getId(), List.of(fixture.user().getId()))).isZero();

        verify(userRepository, times(1)).findAllById(List.of(fixture.user().getId()));
        verify(submissionRepository, times(1)).findBlockingSubmissionsForUsersAndChallenge(
                List.of(fixture.user().getId()), fixture.challenge().getId()
        );
        verify(attemptService, never()).award(any(), any(), any());
    }

    @Test
    void preservesSuccessesBeforeAndAfterAnExpectedRolledBackCollision() {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        AppUser first = user();
        AppUser collision = user();
        AppUser third = user();
        List<UUID> candidateIds = List.of(first.getId(), collision.getId(), third.getId());
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(candidateIds)).thenReturn(List.of(first, collision, third));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(candidateIds, challenge.getId()))
                .thenReturn(List.of());
        when(challengeService.isPlayerEligibleForChallenge(any(), eq(challenge))).thenReturn(true);
        org.mockito.Mockito.doNothing().when(attemptService).award(challenge.getId(), first.getId(), java.util.List.of());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException(
                "duplicate challenge_active_memberships_pkey"
        )).when(attemptService).award(challenge.getId(), collision.getId(), java.util.List.of());
        org.mockito.Mockito.doNothing().when(attemptService).award(challenge.getId(), third.getId(), java.util.List.of());

        assertThat(service.awardBatch(challenge.getId(), candidateIds)).isEqualTo(2);

        verify(attemptService).award(challenge.getId(), first.getId(), java.util.List.of());
        verify(attemptService).award(challenge.getId(), collision.getId(), java.util.List.of());
        verify(attemptService).award(challenge.getId(), third.getId(), java.util.List.of());
        verify(challengeService, never()).recalculateChallengePoints();
    }

    @Test
    void emptyBatchDoesNotReadCandidatesOrRecalculatePoints() {
        assertThat(service.awardBatch(UUID.randomUUID(), List.of())).isZero();

        verify(challengeRepository, never()).findById(any());
        verify(userRepository, never()).findAllById(any());
        verify(challengeService, never()).recalculateChallengePoints();
    }

    @Test
    void awardsEachBatchInANewTransaction() throws NoSuchMethodException {
        Transactional transactional = AutoChallengeAwardAttemptService.class
                .getMethod("award", UUID.class, UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void keepsLazyEligibilityReadsInsideABoundedReadOnlyTransaction() throws NoSuchMethodException {
        Transactional transactional = AutoChallengeAwardService.class
                .getMethod("awardBatch", UUID.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.readOnly()).isTrue();
    }

    private Fixture fixture() {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        AppUser user = user();
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(userRepository.findAllById(List.of(user.getId()))).thenReturn(List.of(user));
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(user.getId()), challenge.getId()
        )).thenReturn(List.of());
        return new Fixture(challenge, user);
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        return user;
    }

    private record Fixture(Challenge challenge, AppUser user) {}

    /**
     * Une equipe, une soumission -- et non une par membre.
     *
     * <p>La collection physique est mutualisee : l'objectif est atteint collectivement.
     * Attribuer a chacun ferait encaisser a l'equipe autant de fois la mise qu'elle compte
     * de joueurs, pour un seul accomplissement.</p>
     */
    @Test
    void aSharedChallengeAwardsOneSubmissionPerTeam() {
        UUID challengeId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();

        Challenge challenge = new Challenge();
        challenge.setId(challengeId);
        challenge.setRewardShared(true);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        AppUser aliceUser = new AppUser();
        aliceUser.setId(alice);
        AppUser brunoUser = new AppUser();
        brunoUser.setId(bruno);
        when(userRepository.findAllById(any())).thenReturn(List.of(aliceUser, brunoUser));
        when(challengeService.isPlayerEligibleForChallenge(any(), any())).thenReturn(true);
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(any(), any()))
                .thenReturn(List.of());

        fr.insastronaute.webapi.cardteam.domain.CardTeam team = new fr.insastronaute.webapi.cardteam.domain.CardTeam();
        team.setId(teamId);
        for (UUID userId : List.of(alice, bruno)) {
            fr.insastronaute.webapi.cardteam.domain.CardTeamMember member =
                    new fr.insastronaute.webapi.cardteam.domain.CardTeamMember();
            member.setTeam(team);
            when(teamMemberRepository.findByUserId(userId)).thenReturn(Optional.of(member));
        }

        int awarded = service.awardBatch(challengeId, List.of(alice, bruno));

        assertThat(awarded).isEqualTo(2);
        // Une seule attribution, portant les deux joueurs.
        verify(attemptService).award(challengeId, alice, List.of(bruno));
        verify(attemptService, never()).award(challengeId, bruno, List.of());
    }

    /** Sans le drapeau, chacun garde sa propre soumission. */
    @Test
    void anUnsharedChallengeStillAwardsEachPlayerSeparately() {
        UUID challengeId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();

        Challenge challenge = new Challenge();
        challenge.setId(challengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        AppUser aliceUser = new AppUser();
        aliceUser.setId(alice);
        AppUser brunoUser = new AppUser();
        brunoUser.setId(bruno);
        when(userRepository.findAllById(any())).thenReturn(List.of(aliceUser, brunoUser));
        when(challengeService.isPlayerEligibleForChallenge(any(), any())).thenReturn(true);
        when(submissionRepository.findBlockingSubmissionsForUsersAndChallenge(any(), any()))
                .thenReturn(List.of());

        service.awardBatch(challengeId, List.of(alice, bruno));

        verify(attemptService).award(challengeId, alice, List.of());
        verify(attemptService).award(challengeId, bruno, List.of());
        // L'annuaire des equipes n'est meme pas interroge.
        verify(teamMemberRepository, never()).findByUserId(any());
    }
}
