package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerCheckpointProgress;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultCode;
import fr.insastronaute.webapi.treasurehunt.domain.PlayerVaultUnlock;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntPath;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayerResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayersResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPathStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerPathStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntStepStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntValidatedStepResponse;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultCodeRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultUnlockRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Deux voies, trois joueurs : Alice termine l'Informatique et entame le Genie
 * mecanique, Bob s'arrete a la deuxieme etape de l'Informatique, Carol n'a rien
 * valide. Les dates sont forcees, parce que les ecarts entre elles sont
 * precisement ce que les statistiques mesurent.
 *
 * <p>Un membre du staff joue aussi, et avant tout le monde : il termine
 * l'Informatique et ouvre le coffre. Il ne doit peser sur aucun chiffre agrege --
 * ceux des tests qui ne parlent pas de lui ne restent justes que s'il est bien
 * ecarte.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(TreasureHuntStatsService.class)
class TreasureHuntStatsServiceTest {
    private static final Instant T0 = Instant.parse("2026-09-20T10:00:00Z");

    @Autowired private TreasureHuntStatsService service;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TreasureHuntPathRepository pathRepository;
    @Autowired private PlayerCheckpointProgressRepository progressRepository;
    @Autowired private PlayerVaultCodeRepository vaultCodeRepository;
    @Autowired private PlayerVaultUnlockRepository vaultUnlockRepository;
    @MockitoBean private LeaderboardService leaderboardService;

    private TreasureHuntPath info;
    private TreasureHuntPath gm;
    private AppUser alice;
    private AppUser bob;
    private AppUser carol;
    private AppUser staff;

    @BeforeEach
    void seed() {
        info = path("INFO", "Voie Informatique", 1, 3);
        gm = path("GM", "Voie Genie mecanique", 2, 2);
        alice = player("alice", "Alice", info);
        bob = player("bob", null, gm);
        carol = player("carol", "Carol", info);
        staff = player("staff", "Respo", info);
        // La regle des classements, que les statistiques reprennent telle quelle.
        when(leaderboardService.isRankable(any()))
                .thenAnswer(invocation -> !"staff-sub".equals(((AppUser) invocation.getArgument(0)).getKeycloakSub()));

        validate(staff, info, 1, T0.minus(Duration.ofHours(2)));
        validate(staff, info, 2, T0.minus(Duration.ofHours(2)).plusSeconds(60));
        validate(staff, info, 3, T0.minus(Duration.ofHours(2)).plusSeconds(120));
        issueVaultCode(staff, info, "STF999", T0.minus(Duration.ofHours(2)).plusSeconds(120));
        unlockVault(staff, T0.minus(Duration.ofHours(1)));

        validate(alice, info, 1, T0);
        validate(alice, info, 2, T0.plus(Duration.ofHours(1)));
        validate(alice, info, 3, T0.plus(Duration.ofHours(3)));
        issueVaultCode(alice, info, "ABC123", T0.plus(Duration.ofHours(3)));
        validate(alice, gm, 1, T0.plus(Duration.ofHours(4)));
        unlockVault(alice, T0.plus(Duration.ofHours(5)));

        validate(bob, info, 1, T0.plus(Duration.ofMinutes(10)));
        validate(bob, info, 2, T0.plus(Duration.ofMinutes(130)));
    }

    @AfterEach
    void clearClock() {
        TimeTravelContext.clear();
    }

    @Test
    void countsParticipantsAndOpenedVaults() {
        TreasureHuntCheckpointStatsResponse stats = service.checkpointStats();

        // Carol a un compte mais aucune validation : elle ne participe pas.
        assertThat(stats.participantCount()).isEqualTo(2);
        assertThat(stats.vaultUnlockCount()).isEqualTo(1);
    }

    @Test
    void listsPathsInVaultOrderWithTheirStartersAndFinishers() {
        TreasureHuntCheckpointStatsResponse stats = service.checkpointStats();

        assertThat(stats.paths()).extracting(TreasureHuntPathStatsResponse::departmentShortName)
                .containsExactly("INFO", "GM");
        TreasureHuntPathStatsResponse infoStats = stats.paths().getFirst();
        assertThat(infoStats.pathId()).isEqualTo(info.getId());
        assertThat(infoStats.position()).isEqualTo(1);
        assertThat(infoStats.name()).isEqualTo("Voie Informatique");
        assertThat(infoStats.startedCount()).isEqualTo(2);
        assertThat(infoStats.completedCount()).isEqualTo(1);
        assertThat(stats.paths().get(1).startedCount()).isEqualTo(1);
        assertThat(stats.paths().get(1).completedCount()).isZero();
    }

    @Test
    void measuresEachStepFromItsValidations() {
        TreasureHuntPathStatsResponse infoStats = service.checkpointStats().paths().getFirst();

        assertThat(infoStats.steps()).extracting(TreasureHuntStepStatsResponse::stepNumber).containsExactly(1, 2, 3);
        TreasureHuntStepStatsResponse first = infoStats.steps().get(0);
        assertThat(first.checkpointId()).isEqualTo(info.getCheckpoints().get(0).getId());
        assertThat(first.validatedCount()).isEqualTo(2);
        assertThat(first.firstValidatedAt()).isEqualTo(T0);
        assertThat(first.lastValidatedAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        // Rien ne precede la premiere etape : pas de delai a mesurer.
        assertThat(first.medianSecondsFromPreviousStep()).isNull();

        // Alice met une heure, Bob deux : la mediane d'un nombre pair est leur moyenne.
        TreasureHuntStepStatsResponse second = infoStats.steps().get(1);
        assertThat(second.validatedCount()).isEqualTo(2);
        assertThat(second.medianSecondsFromPreviousStep()).isEqualTo(5400L);

        TreasureHuntStepStatsResponse third = infoStats.steps().get(2);
        assertThat(third.validatedCount()).isEqualTo(1);
        assertThat(third.medianSecondsFromPreviousStep()).isEqualTo(7200L);
    }

    @Test
    void takesTheMiddleDelayWhenTheCountIsOdd() {
        AppUser dave = player("dave");
        validate(dave, info, 1, T0);
        validate(dave, info, 2, T0.plus(Duration.ofHours(10)));

        // Une heure, deux heures, dix heures : la mediane est la valeur du milieu,
        // la ou une moyenne se laisserait tirer par le retardataire.
        assertThat(service.checkpointStats().paths().getFirst().steps().get(1).medianSecondsFromPreviousStep())
                .isEqualTo(7200L);
    }

    @Test
    void reportsAnUnreachedStepAsEmpty() {
        TreasureHuntStepStatsResponse unreached = service.checkpointStats().paths().get(1).steps().get(1);

        assertThat(unreached.validatedCount()).isZero();
        assertThat(unreached.firstValidatedAt()).isNull();
        assertThat(unreached.lastValidatedAt()).isNull();
        assertThat(unreached.medianSecondsFromPreviousStep()).isNull();
    }

    @Test
    void tracesAPlayersProgressPathByPath() {
        TreasureHuntPlayerStatsResponse stats = service.playerStats(alice.getId());

        assertThat(stats.userId()).isEqualTo(alice.getId());
        assertThat(stats.firstValidatedAt()).isEqualTo(T0);
        assertThat(stats.vaultUnlockedAt()).isEqualTo(T0.plus(Duration.ofHours(5)));
        assertThat(stats.paths()).extracting(TreasureHuntPlayerPathStatsResponse::departmentShortName)
                .containsExactly("INFO", "GM");

        TreasureHuntPlayerPathStatsResponse infoPath = stats.paths().getFirst();
        assertThat(infoPath.pathId()).isEqualTo(info.getId());
        assertThat(infoPath.position()).isEqualTo(1);
        assertThat(infoPath.name()).isEqualTo("Voie Informatique");
        assertThat(infoPath.totalSteps()).isEqualTo(3);
        assertThat(infoPath.validatedSteps()).isEqualTo(3);
        assertThat(infoPath.vaultCodeIssuedAt()).isEqualTo(T0.plus(Duration.ofHours(3)));
        assertThat(infoPath.steps()).containsExactly(
                new TreasureHuntValidatedStepResponse(1, T0),
                new TreasureHuntValidatedStepResponse(2, T0.plus(Duration.ofHours(1))),
                new TreasureHuntValidatedStepResponse(3, T0.plus(Duration.ofHours(3))));

        TreasureHuntPlayerPathStatsResponse gmPath = stats.paths().get(1);
        assertThat(gmPath.totalSteps()).isEqualTo(2);
        assertThat(gmPath.validatedSteps()).isEqualTo(1);
        assertThat(gmPath.vaultCodeIssuedAt()).isNull();
    }

    @Test
    void doesNotLeakAnotherPlayersProgress() {
        TreasureHuntPlayerStatsResponse stats = service.playerStats(bob.getId());

        assertThat(stats.firstValidatedAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        assertThat(stats.vaultUnlockedAt()).isNull();
        assertThat(stats.paths().getFirst().validatedSteps()).isEqualTo(2);
        assertThat(stats.paths().getFirst().vaultCodeIssuedAt()).isNull();
        assertThat(stats.paths().get(1).validatedSteps()).isZero();
    }

    @Test
    void showsEveryPathEvenToAPlayerWhoHasNotStarted() {
        TreasureHuntPlayerStatsResponse stats = service.playerStats(carol.getId());

        assertThat(stats.firstValidatedAt()).isNull();
        assertThat(stats.vaultUnlockedAt()).isNull();
        assertThat(stats.paths()).hasSize(2).allSatisfy(path -> {
            assertThat(path.validatedSteps()).isZero();
            assertThat(path.steps()).isEmpty();
        });
    }

    @Test
    void refusesAnUnknownPlayer() {
        assertThatThrownBy(() -> service.playerStats(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void stillShowsAStaffMembersOwnProgressOnTheirSheet() {
        // La fiche ne compte qu'une personne : l'exclure d'elle-meme ne voudrait rien dire.
        TreasureHuntPlayerStatsResponse stats = service.playerStats(staff.getId());

        assertThat(stats.paths().getFirst().validatedSteps()).isEqualTo(3);
        assertThat(stats.vaultUnlockedAt()).isEqualTo(T0.minus(Duration.ofHours(1)));
    }

    @Test
    void listsTheStepsPlayersInArrivalOrder() {
        TreasureHuntCheckpoint second = info.getCheckpoints().get(1);

        TreasureHuntCheckpointPlayersResponse players = service.checkpointPlayers(second.getId());

        assertThat(players.checkpointId()).isEqualTo(second.getId());
        assertThat(players.pathId()).isEqualTo(info.getId());
        assertThat(players.stepNumber()).isEqualTo(2);
        assertThat(players.players()).containsExactly(
                new TreasureHuntCheckpointPlayerResponse(alice.getId(), "Alice", "INFO",
                        T0.plus(Duration.ofHours(1)), 3600L),
                new TreasureHuntCheckpointPlayerResponse(bob.getId(), null, "GM",
                        T0.plus(Duration.ofMinutes(130)), 7200L));
    }

    @Test
    void measuresNoDelayBeforeTheFirstStep() {
        TreasureHuntCheckpointPlayersResponse players = service.checkpointPlayers(info.getCheckpoints().getFirst().getId());

        assertThat(players.players()).extracting(TreasureHuntCheckpointPlayerResponse::userId)
                .containsExactly(alice.getId(), bob.getId());
        assertThat(players.players()).allSatisfy(player -> assertThat(player.secondsFromPreviousStep()).isNull());
    }

    @Test
    void listsNobodyOnAnUnreachedStep() {
        assertThat(service.checkpointPlayers(gm.getCheckpoints().get(1).getId()).players()).isEmpty();
    }

    @Test
    void refusesAnUnknownStep() {
        assertThatThrownBy(() -> service.checkpointPlayers(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void countsEveryoneWhenTheRankingRuleLetsEveryoneThrough() {
        // Keycloak injoignable : la regle des classements laisse tout le monde passer
        // plutot que de vider les chiffres. Les statistiques la suivent, sans regle a elles.
        doReturn(true).when(leaderboardService).isRankable(any());

        TreasureHuntCheckpointStatsResponse stats = service.checkpointStats();

        assertThat(stats.participantCount()).isEqualTo(3);
        assertThat(stats.vaultUnlockCount()).isEqualTo(2);
        assertThat(stats.paths().getFirst().steps().getFirst().firstValidatedAt()).isEqualTo(T0.minus(Duration.ofHours(2)));
    }

    private TreasureHuntPath path(String shortName, String name, int position, int stepCount) {
        Department department = new Department();
        department.setName(name);
        department.setShortName(shortName);
        department = departmentRepository.saveAndFlush(department);

        TreasureHuntPath path = new TreasureHuntPath();
        path.setDepartment(department);
        path.setPosition(position);
        path.setName(name);
        for (int step = 1; step <= stepCount; step++) {
            TreasureHuntCheckpoint checkpoint = new TreasureHuntCheckpoint();
            checkpoint.setStepNumber(step);
            byte[] hash = new byte[32];
            hash[0] = (byte) position;
            hash[1] = (byte) step;
            checkpoint.setCodeHash(hash);
            path.addCheckpoint(checkpoint);
        }
        return pathRepository.saveAndFlush(path);
    }

    private AppUser player(String name) {
        return player(name, name, null);
    }

    private AppUser player(String name, String displayName, TreasureHuntPath departmentPath) {
        AppUser player = new AppUser();
        player.setKeycloakSub(name + "-sub");
        player.setEmail(name + "@example.com");
        player.setDisplayName(displayName);
        if (departmentPath != null) {
            player.setDepartment(departmentPath.getDepartment().getId());
        }
        return userRepository.saveAndFlush(player);
    }

    private void validate(AppUser player, TreasureHuntPath path, int stepNumber, Instant at) {
        TimeTravelContext.setForcedTime(at);
        PlayerCheckpointProgress progress = new PlayerCheckpointProgress();
        progress.setUser(player);
        progress.setCheckpoint(path.getCheckpoints().get(stepNumber - 1));
        progressRepository.saveAndFlush(progress);
    }

    private void issueVaultCode(AppUser player, TreasureHuntPath path, String code, Instant at) {
        TimeTravelContext.setForcedTime(at);
        PlayerVaultCode vaultCode = new PlayerVaultCode();
        vaultCode.setUser(player);
        vaultCode.setPath(path);
        vaultCode.setCode(code);
        vaultCodeRepository.saveAndFlush(vaultCode);
    }

    private void unlockVault(AppUser player, Instant at) {
        TimeTravelContext.setForcedTime(at);
        PlayerVaultUnlock unlock = new PlayerVaultUnlock();
        unlock.setUser(player);
        vaultUnlockRepository.saveAndFlush(unlock);
    }
}
