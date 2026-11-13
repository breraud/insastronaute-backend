package fr.insastronaute.webapi.economy.service;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.economy.domain.CurrencyMovement;
import fr.insastronaute.webapi.economy.domain.CurrencySource;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto;
import fr.insastronaute.webapi.economy.repository.CurrencyMovementRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyStatsServiceTest {

    private final CurrencyMovementRepository movements = mock(CurrencyMovementRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ChallengeSubmissionRepository submissions = mock(ChallengeSubmissionRepository.class);
    private final StsRunRepository runs = mock(StsRunRepository.class);

    private EconomyStatsService service;
    private final List<AppUser> players = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new EconomyStatsService(movements, users, departments, submissions, runs);
        when(users.findAll()).thenReturn(players);
        when(departments.findAllByVirtualFalse()).thenReturn(List.of());
        when(submissions.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of());
        when(runs.findRetirementPayouts()).thenReturn(List.of());
    }

    private AppUser player(String name, long tokens) {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setDisplayName(name);
        player.setTokens(tokens);
        players.add(player);
        return player;
    }

    private static CurrencyMovement gain(AppUser player, CurrencySource source, long tokens, Duration ago) {
        return new CurrencyMovement(player.getId(), source, tokens, source.eventPointsFor(tokens), null,
                Instant.now().minus(ago));
    }

    private static boolean flagged(EconomyStatsDto stats, String type) {
        return stats.anomalies().stream().anyMatch(anomaly -> anomaly.type().equals(type));
    }

    /** Une source qui rapporte presque tout : les autres activités ne pèsent plus. */
    @Test
    void aSourceThatCrushesTheOthersIsFlagged() {
        AppUser alice = player("Alice", 0);
        when(movements.findInWindow(any(), any())).thenReturn(List.of(
                gain(alice, CurrencySource.GAME_RUN, 900, Duration.ofHours(2)),
                gain(alice, CurrencySource.CHALLENGE, 100, Duration.ofHours(1))));

        var stats = service.stats(30);

        assertThat(stats.totals().tokensIn()).isEqualTo(1000);
        var game = stats.sources().stream().filter(source -> source.source().equals("GAME_RUN")).findFirst().orElseThrow();
        assertThat(game.shareOfInflow()).isEqualTo(0.9);
        assertThat(flagged(stats, "DOMINANT_SOURCE")).isTrue();
    }

    /**
     * Le seuil repose sur la médiane : sur treize joueurs, le farmeur tirerait la
     * moyenne vers lui et se cacherait sous le seuil qu'il aurait dû franchir.
     */
    @Test
    void aPlayerWhoFarmsInOneDayStandsOut() {
        List<CurrencyMovement> window = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            window.add(gain(player("Joueur " + index, 0), CurrencySource.CHALLENGE, 20, Duration.ofHours(3)));
        }
        AppUser farmer = player("Farmeur", 0);
        window.add(gain(farmer, CurrencySource.CHALLENGE, 600, Duration.ofHours(3)));
        when(movements.findInWindow(any(), any())).thenReturn(window);

        var stats = service.stats(30);

        assertThat(stats.anomalies())
                .anySatisfy(anomaly -> {
                    assertThat(anomaly.type()).isEqualTo("PLAYER_DAY_SPIKE");
                    assertThat(anomaly.userId()).isEqualTo(farmer.getId());
                });
    }

    /** Le registre ne peut pas créer plus de PE que la règle n'en donne : sinon, c'est une fuite. */
    @Test
    void eventPointsCreatedOutsideTheRuleAreCritical() {
        AppUser alice = player("Alice", 0);
        when(movements.findInWindow(any(), any())).thenReturn(List.of(
                new CurrencyMovement(alice.getId(), CurrencySource.CHALLENGE, 10, 50, null, Instant.now())));

        var stats = service.stats(30);

        assertThat(stats.anomalies()).first().satisfies(anomaly -> {
            assertThat(anomaly.type()).isEqualTo("EVENT_POINTS_OUT_OF_RULE");
            assertThat(anomaly.severity()).isEqualTo("CRITICAL");
        });
    }

    @Test
    void eventPointsThatFollowTheRuleRaiseNothing() {
        AppUser alice = player("Alice", 0);
        when(movements.findInWindow(any(), any())).thenReturn(List.of(
                gain(alice, CurrencySource.CHALLENGE, 10, Duration.ofMinutes(5))));

        assertThat(flagged(service.stats(30), "EVENT_POINTS_OUT_OF_RULE")).isFalse();
    }

    /**
     * Les gains par défi suivent les règles du recalcul : quota dans l'ordre,
     * promo sans récompense, récompense partagée qui se divise.
     */
    @Test
    void challengePayoutsFollowTheRecalculationRules() {
        when(movements.findInWindow(any(), any())).thenReturn(List.of());
        AppUser alice = player("Alice", 0);
        AppUser bob = player("Bob", 0);
        AppUser chloe = player("Chloé", 0);

        Challenge capped = challenge("Plafonné", 10, 1, false, false);
        Challenge shared = challenge("Partagé", 30, null, true, false);
        Challenge promo = challenge("Promo", 40, null, false, true);

        when(submissions.findValidatedSubmissionsForPointRecalculation()).thenReturn(List.of(
                submission(capped, alice, Set.of()),
                submission(capped, bob, Set.of()),
                submission(shared, alice, Set.of(bob, chloe)),
                submission(promo, chloe, Set.of())));

        var payouts = service.stats(30).challengePayouts();

        assertThat(payouts).filteredOn(payout -> payout.title().equals("Plafonné")).singleElement().satisfies(payout -> {
            assertThat(payout.validations()).isEqualTo(2);
            assertThat(payout.rewardedValidations()).isEqualTo(1);
            assertThat(payout.tokensDistributed()).isEqualTo(10);
        });
        assertThat(payouts).filteredOn(payout -> payout.title().equals("Partagé")).singleElement()
                .satisfies(payout -> assertThat(payout.tokensDistributed()).isEqualTo(30));
        assertThat(payouts).filteredOn(payout -> payout.title().equals("Promo")).singleElement().satisfies(payout -> {
            assertThat(payout.validations()).isEqualTo(1);
            assertThat(payout.tokensDistributed()).isZero();
        });
    }

    @Test
    void balancesShowWhoHoldsTheSupply() {
        when(movements.findInWindow(any(), any())).thenReturn(List.of());
        player("Baleine", 10_000);
        for (int index = 0; index < 99; index++) {
            player("Joueur " + index, 10);
        }

        var stats = service.stats(30);

        assertThat(stats.balances().tokenSupply()).isEqualTo(10_990);
        assertThat(stats.balances().top1PercentShare()).isGreaterThan(0.9);
        assertThat(flagged(stats, "TOKEN_CONCENTRATION")).isTrue();
        // Les 99 joueurs à 10 tokens tombent dans la tranche 10–49.
        assertThat(stats.balances().tokenHistogram()).anySatisfy(bucket -> {
            assertThat(bucket.from()).isEqualTo(10);
            assertThat(bucket.players()).isEqualTo(99);
        });
    }

    /** Un solde négatif est impossible par les règles : s'il existe, une écriture les a contournées. */
    @Test
    void aNegativeBalanceIsCritical() {
        when(movements.findInWindow(any(), any())).thenReturn(List.of());
        player("Cassé", -5);

        assertThat(service.stats(30).anomalies()).first()
                .satisfies(anomaly -> assertThat(anomaly.type()).isEqualTo("NEGATIVE_BALANCE"));
    }

    @Test
    void theWindowIsCappedAtNinetyDays() {
        when(movements.findInWindow(any(), any())).thenReturn(List.of());
        assertThat(service.stats(500).days()).isEqualTo(90);
        assertThat(service.stats(0).days()).isEqualTo(1);
    }

    private static Challenge challenge(String title, int reward, Integer quota, boolean shared, boolean promo) {
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        challenge.setTitle(title);
        challenge.setPointsReward(reward);
        challenge.setGlobalQuota(quota);
        challenge.setRewardShared(shared);
        challenge.setPromo(promo);
        return challenge;
    }

    private static ChallengeSubmission submission(Challenge challenge, AppUser author, Set<AppUser> participants) {
        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setChallenge(challenge);
        submission.setUser(author);
        submission.setParticipants(new java.util.HashSet<>(participants));
        return submission;
    }
}
