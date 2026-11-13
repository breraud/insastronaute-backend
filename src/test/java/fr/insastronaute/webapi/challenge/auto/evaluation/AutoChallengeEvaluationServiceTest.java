package fr.insastronaute.webapi.challenge.auto.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.award.AutoChallengeAwardService;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.auto.configuration.AutoChallengeProperties;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoChallengeEvaluationServiceTest {

    @Mock private ChallengeRepository challengeRepository;
    @Mock private AutoChallengeAwardService awardService;
    @Mock private fr.insastronaute.webapi.challenge.service.ChallengeService challengeService;

    private TestType type;
    private AutoChallengeEvaluationService service;

    @BeforeEach
    void setUp() {
        type = new TestType();
        AutoChallengeTypeRegistry registry = new AutoChallengeTypeRegistry(List.of(type), new ObjectMapper());
        AutoChallengeProperties properties = new AutoChallengeProperties();
        properties.setBatchSize(73);
        service = new AutoChallengeEvaluationService(challengeRepository, registry, awardService, challengeService, properties);
    }

    @Test
    void evaluatesActiveAutomaticChallengesPageByPageAndSummarizesAwards() {
        Challenge challenge = automaticChallenge("TEST", "{\"minimum\":2}");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        type.pages.add(new AutoChallengeCandidatePage(List.of(first, second), cursor));
        type.pages.add(new AutoChallengeCandidatePage(List.of(), null));
        when(challengeRepository.findActiveAutomaticChallenges(any(Instant.class), any(Instant.class))).thenReturn(List.of(challenge));
        when(awardService.awardBatch(challenge.getId(), List.of(first, second))).thenReturn(2);

        AutoChallengeEvaluationSummary summary = service.evaluateAll();

        assertThat(type.parametersSeen).containsExactly(new Parameters(2), new Parameters(2));
        assertThat(type.cursorsSeen).containsExactly(null, cursor);
        assertThat(type.limitsSeen).containsOnly(73);
        verify(awardService).awardBatch(challenge.getId(), List.of(first, second));
        verify(awardService, never()).awardBatch(challenge.getId(), List.of());
        assertThat(summary.examinedChallenges()).isEqualTo(1);
        assertThat(summary.awardedUsers()).isEqualTo(2);
        assertThat(summary.skippedChallenges()).isZero();
        assertThat(summary.errors()).isZero();
        assertThat(summary.duration().isNegative()).isFalse();
        verify(challengeService).recalculateChallengePoints();
    }

    @Test
    void evaluateOneLeavesAChallengeThatIsNotOpenUntouched() {
        // Enregistrer un défi programmé pour demain, ou encore brouillon, ne doit
        // valider personne aujourd'hui.
        Challenge challenge = automaticChallenge("TEST", "{\"minimum\":2}");
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(challengeService.isOpenForAutomaticAward(challenge)).thenReturn(false);

        assertThat(service.evaluateOne(challenge.getId())).isZero();

        assertThat(type.cursorsSeen).isEmpty();
        verify(awardService, never()).awardBatch(any(), any());
        verify(challengeService, never()).recalculateChallengePoints();
    }

    @Test
    void evaluateOneChecksOpeningOnTheRealClockUnderTimeTravel() {
        Challenge challenge = automaticChallenge("TEST", "{\"minimum\":2}");
        Instant forced = Instant.now().plus(java.time.Duration.ofDays(30));
        List<Instant> clockSeen = new ArrayList<>();
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(challengeService.isOpenForAutomaticAward(challenge)).thenAnswer(invocation -> {
            clockSeen.add(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.getForcedTime());
            return false;
        });

        fr.insastronaute.webapi.common.timetravel.TimeTravelContext.setForcedTime(forced);
        try {
            service.evaluateOne(challenge.getId());
            assertThat(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.getForcedTime()).isEqualTo(forced);
        } finally {
            fr.insastronaute.webapi.common.timetravel.TimeTravelContext.clear();
        }

        assertThat(clockSeen).hasSize(1).containsOnlyNulls();
    }

    @Test
    void isolatesMalformedMissingAndFailingTypesAndContinuesWithNextChallenge() throws Exception {
        Challenge malformed = automaticChallenge("TEST", "{\"minimum\":0}");
        Challenge missing = automaticChallenge("REMOVED", "{}");
        Challenge failing = automaticChallenge("TEST", "{\"minimum\":1}");
        Challenge succeeding = automaticChallenge("TEST", "{\"minimum\":3}");
        UUID awardedUser = UUID.randomUUID();
        type.failForMinimum = 1;
        type.pages.add(new AutoChallengeCandidatePage(List.of(awardedUser), null));
        when(challengeRepository.findActiveAutomaticChallenges(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(malformed, missing, failing, succeeding));
        when(awardService.awardBatch(succeeding.getId(), List.of(awardedUser))).thenReturn(1);

        AutoChallengeEvaluationSummary summary = service.evaluateAll();

        verify(awardService).awardBatch(succeeding.getId(), List.of(awardedUser));
        assertThat(summary.examinedChallenges()).isEqualTo(4);
        assertThat(summary.awardedUsers()).isEqualTo(1);
        assertThat(summary.skippedChallenges()).isEqualTo(2);
        assertThat(summary.errors()).isEqualTo(1);
    }

    @Test
    void rejectsANonAdvancingCursorAndStillEvaluatesTheNextChallenge() {
        Challenge broken = automaticChallenge("TEST", "{\"minimum\":1}");
        Challenge next = automaticChallenge("TEST", "{\"minimum\":2}");
        UUID cursor = UUID.randomUUID();
        UUID alreadyAwardedUser = UUID.randomUUID();
        UUID nextUser = UUID.randomUUID();
        type.pages.add(new AutoChallengeCandidatePage(List.of(alreadyAwardedUser), cursor));
        type.pages.add(new AutoChallengeCandidatePage(List.of(), cursor));
        type.pages.add(new AutoChallengeCandidatePage(List.of(nextUser), null));
        when(challengeRepository.findActiveAutomaticChallenges(any(Instant.class), any(Instant.class))).thenReturn(List.of(broken, next));
        when(awardService.awardBatch(broken.getId(), List.of(alreadyAwardedUser))).thenReturn(1);
        when(awardService.awardBatch(next.getId(), List.of(nextUser))).thenReturn(1);

        AutoChallengeEvaluationSummary summary = service.evaluateAll();

        verify(awardService).awardBatch(next.getId(), List.of(nextUser));
        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.awardedUsers()).isEqualTo(2);
    }

    @Test
    void isolatesAwardFailureFromFollowingChallenge() {
        Challenge failing = automaticChallenge("TEST", "{\"minimum\":1}");
        Challenge succeeding = automaticChallenge("TEST", "{\"minimum\":2}");
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        type.pages.add(new AutoChallengeCandidatePage(List.of(firstUser), null));
        type.pages.add(new AutoChallengeCandidatePage(List.of(secondUser), null));
        when(challengeRepository.findActiveAutomaticChallenges(any(Instant.class), any(Instant.class))).thenReturn(List.of(failing, succeeding));
        when(awardService.awardBatch(failing.getId(), List.of(firstUser))).thenThrow(new IllegalStateException("db"));
        when(awardService.awardBatch(succeeding.getId(), List.of(secondUser))).thenReturn(1);

        AutoChallengeEvaluationSummary summary = service.evaluateAll();

        InOrder order = inOrder(awardService);
        order.verify(awardService).awardBatch(failing.getId(), List.of(firstUser));
        order.verify(awardService).awardBatch(succeeding.getId(), List.of(secondUser));
        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.awardedUsers()).isEqualTo(1);
        verify(challengeService).recalculateChallengePoints();
    }

    @Test
    void recalculatesOnceAfterAChallengeWithAwardsEvenWhenALaterPageFails() {
        Challenge challenge = automaticChallenge("TEST", "{\"minimum\":1}");
        UUID first = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        type.pages.add(new AutoChallengeCandidatePage(List.of(first), cursor));
        type.failAfterPages = true;
        when(challengeRepository.findActiveAutomaticChallenges(any(Instant.class), any(Instant.class))).thenReturn(List.of(challenge));
        when(awardService.awardBatch(challenge.getId(), List.of(first))).thenReturn(1);

        AutoChallengeEvaluationSummary summary = service.evaluateAll();

        assertThat(summary.awardedUsers()).isOne();
        assertThat(summary.errors()).isOne();
        verify(challengeService).recalculateChallengePoints();
    }

    @Test
    void rejectsACursorThatMovesBackwards() {
        Challenge challenge = automaticChallenge("TEST", "{\"minimum\":1}");
        UUID forward = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID backward = UUID.fromString("00000000-0000-0000-0000-000000000001");
        type.pages.add(new AutoChallengeCandidatePage(List.of(), forward));
        type.pages.add(new AutoChallengeCandidatePage(List.of(), backward));
        when(challengeRepository.findActiveAutomaticChallenges(any(Instant.class), any(Instant.class))).thenReturn(List.of(challenge));

        assertThat(service.evaluateAll().errors()).isOne();
        verify(awardService, never()).awardBatch(any(), any());
    }

    private Challenge automaticChallenge(String key, String json) {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setValidationMode("AUTO");
        challenge.setAutoChallengeType(key);
        try {
            challenge.setAutoChallengeParameters(new ObjectMapper().readTree(json));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return challenge;
    }

    private record Parameters(int minimum) {}

    private static final class TestType implements AutoChallengeType<Parameters> {
        private final List<AutoChallengeCandidatePage> pages = new ArrayList<>();
        private final List<Parameters> parametersSeen = new ArrayList<>();
        private final List<UUID> cursorsSeen = new ArrayList<>();
        private final List<Integer> limitsSeen = new ArrayList<>();
        private Integer failForMinimum;
        private boolean failAfterPages;

        @Override public String key() { return "TEST"; }
        @Override public String publicName() { return "Test"; }
        @Override public String description() { return "Test"; }
        @Override public Class<Parameters> parametersType() { return Parameters.class; }
        @Override public List<AutoChallengeParameter> parameters() { return List.of(); }

        @Override
        public void validate(Parameters parameters) {
            if (parameters.minimum() < 1) throw new IllegalArgumentException("minimum");
        }

        @Override
        public AutoChallengeCandidatePage findCandidates(Parameters parameters, AutoChallengeCandidateQuery query) {
            parametersSeen.add(parameters);
            cursorsSeen.add(query.afterUserId());
            limitsSeen.add(query.limit());
            if (failForMinimum != null && parameters.minimum() == failForMinimum) {
                failForMinimum = null;
                throw new IllegalStateException("type failure");
            }
            if (failAfterPages && pages.isEmpty()) throw new IllegalStateException("later page");
            return pages.removeFirst();
        }
    }
}
