package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.card.repository.CardCollectionSettingsRepository;
import fr.insastronaute.webapi.card.service.CollectionPointsCalculator;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardConfig;
import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardEntryDto;
import fr.insastronaute.webapi.leaderboard.dto.LeaderboardConfigUpdateDto;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardConfigRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardSnapshotRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Test
    void adminLiveTotalAppliesConfiguredWeightsToCurrentUserScores() {
        UUID departmentId = UUID.randomUUID();
        AppUser alice = user("alice", UUID.randomUUID(), departmentId, 100, false);
        alice.setDisplayName("Alice");
        alice.setChallengePoints(200L);
        AppUser bob = user("bob", UUID.randomUUID(), departmentId, 200, false);
        bob.setDisplayName("Bob");
        bob.setChallengePoints(50L);

        LeaderboardConfig tournaments = userTokensConfig();
        tournaments.setTotalWeightPercent(25);
        LeaderboardConfig challenges = new LeaderboardConfig();
        challenges.setScope(LeaderboardScope.USER);
        challenges.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        challenges.setTotalWeightPercent(75);

        when(configRepository.findAll()).thenReturn(List.of(tournaments, challenges));
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        var total = leaderboardService.getAdminUserTotalLeaderboard(true);

        assertThat(total.entries()).extracting(UserLeaderboardEntryDto::name).containsExactly("Alice", "Bob");
        assertThat(total.entries()).extracting(UserLeaderboardEntryDto::score).containsExactly(175L, 88L);
    }

    @Test
    void updatesAllTotalLeaderboardWeightsAtomically() {
        LeaderboardConfig challenges = new LeaderboardConfig();
        challenges.setScope(LeaderboardScope.USER);
        challenges.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        LeaderboardConfig tournaments = new LeaderboardConfig();
        tournaments.setScope(LeaderboardScope.USER);
        tournaments.setMetric(LeaderboardMetric.TOURNAMENT_POINTS);
        when(configRepository.findAll()).thenReturn(java.util.List.of(challenges, tournaments));
        when(configRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        leaderboardService.updateTotalLeaderboardWeights(java.util.Map.of(
                LeaderboardMetric.CHALLENGE_POINTS, 20,
                LeaderboardMetric.TOURNAMENT_POINTS, 80));

        assertThat(challenges.getTotalWeightPercent()).isEqualTo(20);
        assertThat(tournaments.getTotalWeightPercent()).isEqualTo(80);
    }

    // ── Classement Elo ────────────────────────────────────────────────────────
    // Ce n'est pas un compte de points : il ne se pondere pas dans le total, et il n'existe pas
    // a l'echelle des departements. Les trois tests qui suivent tiennent ces deux regles.

    /**
     * Le formulaire des poids ne propose pas l'Elo ; s'il arrivait a zero dans la demande — un
     * ancien panneau l'enverrait tel qu'il le lit —, il ne doit ni faire echouer le total a 100 %
     * ni recevoir de poids.
     */
    @Test
    void theEloLeaderboardTakesNoPartInTheTotalWeights() {
        LeaderboardConfig challenges = userConfig(LeaderboardMetric.CHALLENGE_POINTS);
        LeaderboardConfig elo = userConfig(LeaderboardMetric.PVP_ELO);
        elo.setTotalWeightPercent(0);
        when(configRepository.findAll()).thenReturn(java.util.List.of(challenges, elo));
        when(configRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        leaderboardService.updateTotalLeaderboardWeights(java.util.Map.of(
                LeaderboardMetric.CHALLENGE_POINTS, 100,
                LeaderboardMetric.PVP_ELO, 0));

        assertThat(challenges.getTotalWeightPercent()).isEqualTo(100);
        assertThat(elo.getTotalWeightPercent()).isZero();
    }

    /** Un poids non nul sur l'Elo n'aurait aucun effet : l'accepter laisserait croire l'inverse. */
    @Test
    void refusesToWeighTheEloLeaderboardIntoTheTotal() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                leaderboardService.updateTotalLeaderboardWeights(java.util.Map.of(
                        LeaderboardMetric.CHALLENGE_POINTS, 80,
                        LeaderboardMetric.PVP_ELO, 20)));
    }

    /**
     * Un poids manquant doit tomber sur le refus explicite, pas sur une NullPointerException :
     * le filtrage des metriques additives passait par une collecte qui ne tolere pas les nulls.
     */
    @Test
    void aMissingWeightIsRefusedRatherThanCrashing() {
        when(configRepository.findAll()).thenReturn(java.util.List.of(userConfig(LeaderboardMetric.CHALLENGE_POINTS)));
        java.util.Map<LeaderboardMetric, Integer> weights = new java.util.HashMap<>();
        weights.put(LeaderboardMetric.CHALLENGE_POINTS, null);

        assertThatIllegalArgumentException().isThrownBy(() ->
                leaderboardService.updateTotalLeaderboardWeights(weights));
    }

    @Test
    void theEloMetricExistsForPlayersOnlyAndStaysOutOfTheTotal() {
        assertThat(LeaderboardMetric.PVP_ELO.supports(LeaderboardScope.USER)).isTrue();
        assertThat(LeaderboardMetric.PVP_ELO.supports(LeaderboardScope.DEPARTMENT)).isFalse();
        assertThat(LeaderboardMetric.PVP_ELO.countsTowardTotal()).isFalse();
        // Les autres gardent leur comportement : ce sont bien des points, partout.
        for (LeaderboardMetric metric : LeaderboardMetric.values()) {
            if (metric != LeaderboardMetric.PVP_ELO) {
                assertThat(metric.supports(LeaderboardScope.DEPARTMENT)).as(metric.name()).isTrue();
                assertThat(metric.countsTowardTotal()).as(metric.name()).isTrue();
            }
        }
    }

    /**
     * Le piege qu'evite le filtrage par echelle : les classements des departements, demandes sans
     * liste, parcouraient toutes les metriques, et la premiere sans configuration departement —
     * l'Elo — faisait lever une introuvable qui emportait la page entiere.
     */
    @Test
    @SuppressWarnings("unchecked")
    void listingEveryDepartmentLeaderboardLeavesTheEloOut() throws Exception {
        java.lang.reflect.Method normalize = LeaderboardService.class.getDeclaredMethod(
                "normalizeMetrics", LeaderboardScope.class, java.util.List.class);
        normalize.setAccessible(true);

        java.util.List<LeaderboardMetric> departments =
                (java.util.List<LeaderboardMetric>) normalize.invoke(leaderboardService, LeaderboardScope.DEPARTMENT, null);
        java.util.List<LeaderboardMetric> users =
                (java.util.List<LeaderboardMetric>) normalize.invoke(leaderboardService, LeaderboardScope.USER, null);

        assertThat(departments).doesNotContain(LeaderboardMetric.PVP_ELO).isNotEmpty();
        assertThat(users).contains(LeaderboardMetric.PVP_ELO);

        // Le site demande ses classements de departement par une liste explicite, et avale
        // l'erreur : l'Elo glisse dans la liste ne doit pas emporter les autres avec lui.
        java.util.List<LeaderboardMetric> explicit = (java.util.List<LeaderboardMetric>) normalize.invoke(
                leaderboardService, LeaderboardScope.DEPARTMENT,
                java.util.List.of(LeaderboardMetric.CHALLENGE_POINTS, LeaderboardMetric.PVP_ELO));
        assertThat(explicit).containsExactly(LeaderboardMetric.CHALLENGE_POINTS);
    }

    private static LeaderboardConfig userConfig(LeaderboardMetric metric) {
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.USER);
        config.setMetric(metric);
        return config;
    }

    @Test
    void updatesFormulaOnlyForDepartmentConfigurations() {
        LeaderboardConfig departmentConfig = new LeaderboardConfig();
        departmentConfig.setScope(LeaderboardScope.DEPARTMENT);
        departmentConfig.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(departmentConfig));
        when(configRepository.save(departmentConfig)).thenReturn(departmentConfig);

        leaderboardService.updateConfig(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS,
                new LeaderboardConfigUpdateDto(null, null, DepartmentScoringFormula.BALANCED_SQRT));

        assertThat(departmentConfig.getFormula()).isEqualTo(DepartmentScoringFormula.BALANCED_SQRT);

        LeaderboardConfig userConfig = new LeaderboardConfig();
        userConfig.setScope(LeaderboardScope.USER);
        userConfig.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(userConfig));
        assertThatIllegalArgumentException().isThrownBy(() -> leaderboardService.updateConfig(
                LeaderboardScope.USER, LeaderboardMetric.CHALLENGE_POINTS,
                new LeaderboardConfigUpdateDto(null, null, DepartmentScoringFormula.BALANCED_SQRT)
        ));
    }

    @Mock
    private LeaderboardConfigRepository configRepository;

    @Mock
    private LeaderboardSnapshotRepository snapshotRepository;

    @Mock
    private LeaderboardEntryRepository entryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private CardCollectionRepository cardCollectionRepository;

    @Mock
    private CardTeamMemberRepository cardTeamMemberRepository;

    @Mock
    private StsRunRepository stsRunRepository;

    @Mock
    private fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository pvpProfileRepository;

    @Mock
    private RealmRoleDirectory roleDirectory;

    @Mock
    private fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository challengeSubmissionRepository;

    @Mock
    private fr.insastronaute.webapi.leaderboard.repository.DepartmentLeaderboardSettingsRepository departmentSettingsRepository;

    // A real calculator, not a mock: these tests exist to check the point rules,
    // and a mock would assert nothing but its own stubbing. Its settings repository
    // returns empty, so the calculator falls back to the shipped defaults.
    @Spy
    private CollectionPointsCalculator collectionPointsCalculator =
            new CollectionPointsCalculator(org.mockito.Mockito.mock(CardCollectionSettingsRepository.class));

    @InjectMocks
    private LeaderboardService leaderboardService;


    // ── Classements de collection par equipe ──────────────────────────────────
    // Les cartes physiques d'une equipe sont mutualisees ; la ligne de classement
    // l'est aussi, sous le nom de l'equipe. Un joueur seul dans la sienne garde son
    // propre nom : « en equipe » commence quand quelqu'un vous a rejoint.

    private CardTeamMember membership(AppUser user, CardTeam team) {
        CardTeamMember member = new CardTeamMember();
        member.setUser(user);
        member.setTeam(team);
        return member;
    }

    private CardTeam team(UUID id, String name) {
        CardTeam team = new CardTeam();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private LeaderboardEntry entry(LeaderboardSnapshot snapshot, UUID subjectId, String name, String department, long score) {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setSnapshot(snapshot);
        entry.setSubjectId(subjectId);
        entry.setName(name);
        entry.setDepartment(department);
        entry.setScore(score);
        return entry;
    }

    private java.util.List<UserLeaderboardEntryDto> readCollectionLeaderboard(
            java.util.List<LeaderboardEntry> stored,
            java.util.List<CardTeamMember> memberships
    ) throws Exception {
        return readCollectionLeaderboard(stored, memberships, LeaderboardMetric.PHYSICAL_COLLECTION_POINTS);
    }

    private java.util.List<UserLeaderboardEntryDto> readCollectionLeaderboard(
            java.util.List<LeaderboardEntry> stored,
            java.util.List<CardTeamMember> memberships,
            LeaderboardMetric metric
    ) throws Exception {
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(stored);
        if (metric == LeaderboardMetric.PHYSICAL_COLLECTION_POINTS) {
            when(cardTeamMemberRepository.findAllWithTeam()).thenReturn(memberships);
        }

        java.lang.reflect.Method method = LeaderboardService.class.getDeclaredMethod(
                "toUserLeaderboardDto", LeaderboardSnapshot.class, LeaderboardMetric.class, java.util.Set.class);
        method.setAccessible(true);
        Object dto = method.invoke(leaderboardService, snapshot, metric, java.util.Set.of());
        return ((fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardDto) dto).entries();
    }

    @Test
    void collectionLeaderboardRanksTeamsUnderTheirName() throws Exception {
        UUID ada = UUID.randomUUID();
        UUID grace = UUID.randomUUID();
        UUID solo = UUID.randomUUID();
        CardTeam rockets = team(UUID.randomUUID(), "Les Fusées");
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();

        var entries = readCollectionLeaderboard(
                java.util.List.of(
                        entry(snapshot, solo, "Solo", "ITI", 90L),
                        entry(snapshot, ada, "Ada", "ITI", 60L),
                        entry(snapshot, grace, "Grace", "ITI", 50L)),
                java.util.List.of(
                        membership(userWithId(ada), rockets),
                        membership(userWithId(grace), rockets)));

        // 60 + 50 passe devant les 90 du joueur seul : c'est bien la somme qui classe.
        assertThat(entries).extracting(UserLeaderboardEntryDto::name).containsExactly("Les Fusées", "Solo");
        assertThat(entries).extracting(UserLeaderboardEntryDto::score).containsExactly(110L, 90L);
        assertThat(entries).extracting(UserLeaderboardEntryDto::rank).containsExactly(1, 2);
    }

    /**
     * Une ligne d'equipe dit qui elle represente.
     *
     * <p>Son nom ne designe personne : cliquer dessus cherchait un joueur ainsi nomme et
     * n'en trouvait aucun, donc « Impossible de retrouver ce joueur ». La composition
     * voyage avec la ligne, dans l'ordre du classement — du membre qui a le plus marque
     * au moins. Une ligne d'un seul joueur porte deja son nom : elle n'a rien a lister.</p>
     */
    @Test
    void aTeamRowCarriesWhoItStandsFor() throws Exception {
        UUID ada = UUID.randomUUID();
        UUID grace = UUID.randomUUID();
        UUID solo = UUID.randomUUID();
        CardTeam rockets = team(UUID.randomUUID(), "Les Fusées");
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();

        var entries = readCollectionLeaderboard(
                java.util.List.of(
                        entry(snapshot, solo, "Solo", "ITI", 90L),
                        entry(snapshot, ada, "Ada", "ITI", 60L),
                        entry(snapshot, grace, "Grace", "ITI", 50L)),
                java.util.List.of(
                        membership(userWithId(ada), rockets),
                        membership(userWithId(grace), rockets)));

        assertThat(entries.getFirst().members()).containsExactly("Ada", "Grace");
        assertThat(entries.get(1).members()).isEmpty();
    }

    @Test
    void aPlayerAloneInATeamKeepsTheirOwnName() throws Exception {
        UUID ada = UUID.randomUUID();
        CardTeam rockets = team(UUID.randomUUID(), "Les Fusées");
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();

        var entries = readCollectionLeaderboard(
                java.util.List.of(entry(snapshot, ada, "Ada", "ITI", 60L)),
                java.util.List.of(membership(userWithId(ada), rockets)));

        assertThat(entries).extracting(UserLeaderboardEntryDto::name).containsExactly("Ada");
    }

    @Test
    void aMixedTeamUsesItsCreatorsDepartment() throws Exception {
        UUID ada = UUID.randomUUID();
        UUID grace = UUID.randomUUID();
        CardTeam rockets = team(UUID.randomUUID(), "Les Fusées");
        rockets.setCreator(userWithId(grace));
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();

        var entries = readCollectionLeaderboard(
                java.util.List.of(
                        entry(snapshot, ada, "Ada", "ITI", 60L),
                        entry(snapshot, grace, "Grace", "CFI", 50L)),
                java.util.List.of(
                        membership(userWithId(ada), rockets),
                        membership(userWithId(grace), rockets)));

        assertThat(entries).singleElement()
                .satisfies(row -> assertThat(row.department()).isEqualTo("CFI"));
    }

    @Test
    void virtualCollectionLeaderboardKeepsPlayersIndividual() throws Exception {
        UUID ada = UUID.randomUUID();
        UUID grace = UUID.randomUUID();
        CardTeam rockets = team(UUID.randomUUID(), "Les Fusées");
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();

        var entries = readCollectionLeaderboard(
                java.util.List.of(
                        entry(snapshot, ada, "Ada", "ITI", 60L),
                        entry(snapshot, grace, "Grace", "CFI", 50L)),
                java.util.List.of(
                        membership(userWithId(ada), rockets),
                        membership(userWithId(grace), rockets)),
                LeaderboardMetric.VIRTUAL_COLLECTION_POINTS);

        assertThat(entries).extracting(UserLeaderboardEntryDto::name).containsExactly("Ada", "Grace");
        assertThat(entries).extracting(UserLeaderboardEntryDto::score).containsExactly(60L, 50L);
        assertThat(entries).allSatisfy(row -> assertThat(row.members()).isEmpty());
    }

    private AppUser userWithId(UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }


    @Test
    @SuppressWarnings("unchecked")
    void gameReleaseAddsUpWhatEachRunPaidOut() {
        // « Les points obtenus grâce au jeu » : les jetons versés en fin de partie,
        // et non le score interne dont ils sont tirés — deux parties de même score
        // ne versent pas forcément autant. Somme et non meilleure partie, comme les
        // trois autres classements qui cumulent eux aussi.
        UUID departmentId = UUID.randomUUID();
        AppUser regular = user(UUID.randomUUID(), departmentId, 0, false);
        AppUser oneShot = user(UUID.randomUUID(), departmentId, 0, false);
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.USER);
        config.setMetric(LeaderboardMetric.GAME_POINTS);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.GAME_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(regular, oneShot));
        when(stsRunRepository.sumRetirementTokensByOwner()).thenReturn(List.of(
                new Object[] { regular.getId(), 30L },
                new Object[] { oneShot.getId(), 120L }
        ));

        leaderboardService.release(LeaderboardScope.USER, LeaderboardMetric.GAME_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(120L, 30L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPlayerWhoNeverFinishedARunScoresZeroRatherThanDisappearing() {
        // Une partie ne verse rien tant qu'elle n'est pas finie ; celui qui n'en a
        // terminé aucune reste classé, en bas, plutôt que d'être absent.
        UUID departmentId = UUID.randomUUID();
        AppUser player = user(UUID.randomUUID(), departmentId, 0, false);
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.USER);
        config.setMetric(LeaderboardMetric.GAME_POINTS);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.GAME_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(player));
        when(stsRunRepository.sumRetirementTokensByOwner()).thenReturn(List.of());

        leaderboardService.release(LeaderboardScope.USER, LeaderboardMetric.GAME_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        assertThat(entries).singleElement()
                .satisfies(entry -> assertThat(entry.getScore()).isZero());
    }

    /**
     * Un défi de promo rapporte au département, et à personne.
     *
     * <p>Ses points entrent dans le total brut, avec ceux des joueurs : ils passent
     * donc la même formule que le reste. Mais ils n'ajoutent aucun membre — un défi
     * de promo n'est pas un joueur de plus dans l'effectif qui divise.</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void aPromoChallengeAddsItsPointsToTheDepartmentAndToNoOne() {
        // @InjectMocks passe par le constructeur : les dépendances optionnelles,
        // injectées par champ en production, restent nulles ici.
        org.springframework.test.util.ReflectionTestUtils.setField(
                leaderboardService, "challengeSubmissionRepository", challengeSubmissionRepository);
        UUID itiId = UUID.randomUUID();
        UUID cfiId = UUID.randomUUID();
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.DEPARTMENT);
        config.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        config.setRefreshDelayMinutes(60);
        config.setAutoRefreshEnabled(false);
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        AppUser iti = user(UUID.randomUUID(), itiId, 0, false);
        iti.setChallengePoints(10L);
        AppUser cfi = user(UUID.randomUUID(), cfiId, 0, false);
        cfi.setChallengePoints(20L);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(new LeaderboardSnapshot());
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(
                department(itiId, "ITI"),
                department(cfiId, "CFI")
        ));
        when(userRepository.findAll()).thenReturn(List.of(iti, cfi));
        // Le défi rapporte 100, plus 5 de bonus accordés à la validation.
        when(challengeSubmissionRepository.findValidatedPromoAwards())
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), itiId, 100, 5}));

        leaderboardService.release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());

        assertThat(entries).extracting(LeaderboardEntry::getName).containsExactly("ITI", "CFI");
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(115L, 20L);
    }

    /**
     * Les points de promo ne passent pas la formule.
     *
     * <p>Un défi relevé une fois par promo vaut la même chose pour une promo de
     * trente et une de deux cents : le diviser par l'effectif le nierait. Ici ITI
     * est compensé ×2 par la formule, et ses 100 points de promo restent 100.</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void promoPointsSkipTheDepartmentFormula() {
        UUID itiId = UUID.randomUUID();
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.DEPARTMENT);
        config.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        config.setFormula(DepartmentScoringFormula.PER_REGISTERED_NORMALIZED);
        config.setRefreshDelayMinutes(60);
        config.setAutoRefreshEnabled(false);
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        org.springframework.test.util.ReflectionTestUtils.setField(
                leaderboardService, "challengeSubmissionRepository", challengeSubmissionRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(
                leaderboardService, "departmentSettingsRepository", departmentSettingsRepository);

        AppUser iti = user(UUID.randomUUID(), itiId, 0, false);
        iti.setChallengePoints(50L);
        fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings settings =
                new fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings();
        // L'identifiant vient de l'association en base : ici on le pose à la main.
        org.springframework.test.util.ReflectionTestUtils.setField(settings, "departmentId", itiId);
        settings.setTheoreticalHeadcount(2);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(new LeaderboardSnapshot());
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(itiId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(iti));
        when(departmentSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(challengeSubmissionRepository.findValidatedPromoAwards())
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), itiId, 100, 0}));

        leaderboardService.release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        // Un seul inscrit sur deux attendus : la formule double ses 50 points de
        // joueur. Les 100 de promo, eux, s'ajoutent tels quels.
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(200L);
    }

    /**
     * « Je comprends pas pourquoi les points des départements ne sont pas la somme
     * de tous les points. » Pour le montrer sans recalculer — donc sans risquer
     * d'afficher d'autres chiffres que ceux publiés —, le classement garde le détail
     * de chaque score au moment où il est publié.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aReleasedDepartmentScoreKeepsTheDetailOfItsCalculation() {
        UUID itiId = UUID.randomUUID();
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.DEPARTMENT);
        config.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        config.setFormula(DepartmentScoringFormula.PER_REGISTERED_NORMALIZED);
        config.setRefreshDelayMinutes(60);
        config.setAutoRefreshEnabled(false);
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        org.springframework.test.util.ReflectionTestUtils.setField(
                leaderboardService, "challengeSubmissionRepository", challengeSubmissionRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(
                leaderboardService, "departmentSettingsRepository", departmentSettingsRepository);

        AppUser iti = user(UUID.randomUUID(), itiId, 0, false);
        iti.setChallengePoints(50L);
        fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings settings =
                new fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings();
        org.springframework.test.util.ReflectionTestUtils.setField(settings, "departmentId", itiId);
        settings.setTheoreticalHeadcount(2);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(new LeaderboardSnapshot());
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(itiId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(iti));
        when(departmentSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(challengeSubmissionRepository.findValidatedPromoAwards())
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), itiId, 100, 0}));

        leaderboardService.release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getScore()).isEqualTo(200L);
            assertThat(entry.getRawTotal()).isEqualTo(50L);
            assertThat(entry.getMemberCount()).isEqualTo(1);
            assertThat(entry.getTheoreticalHeadcount()).isEqualTo(2);
            assertThat(entry.getCoefficient()).isEqualTo(2.0);
            assertThat(entry.getPromoPoints()).isEqualTo(100L);
            assertThat(entry.getFormula()).isEqualTo("PER_REGISTERED_NORMALIZED");
            assertThat(entry.getReferenceHeadcount()).isEqualTo(2);
            // Le détail suffit à retrouver le score publié : somme × coefficient + promo.
            assertThat(Math.round(entry.getRawTotal() * entry.getCoefficient()) + entry.getPromoPoints())
                    .isEqualTo(entry.getScore());
        });
    }

    /** Deux preuves validées pour la même promo ne comptent qu'une fois. */
    @Test
    @SuppressWarnings("unchecked")
    void aPromoChallengeCountsOnceEvenIfTwoProofsWereValidated() {
        // @InjectMocks passe par le constructeur : les dépendances optionnelles,
        // injectées par champ en production, restent nulles ici.
        org.springframework.test.util.ReflectionTestUtils.setField(
                leaderboardService, "challengeSubmissionRepository", challengeSubmissionRepository);
        UUID itiId = UUID.randomUUID();
        UUID promoChallengeId = UUID.randomUUID();
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.DEPARTMENT);
        config.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        config.setRefreshDelayMinutes(60);
        config.setAutoRefreshEnabled(false);
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(new LeaderboardSnapshot());
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(itiId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(user(UUID.randomUUID(), itiId, 0, false)));
        when(challengeSubmissionRepository.findValidatedPromoAwards()).thenReturn(List.<Object[]>of(
                new Object[]{promoChallengeId, itiId, 100, 0},
                new Object[]{promoChallengeId, itiId, 100, 0}
        ));

        leaderboardService.release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(100L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void departmentReleaseExcludesHiddenUsersFromScores() {
        UUID itiId = UUID.randomUUID();
        UUID cfiId = UUID.randomUUID();
        LeaderboardConfig config = departmentTokensConfig();
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(
                department(itiId, "ITI"),
                department(cfiId, "CFI")
        ));
        when(userRepository.findAll()).thenReturn(List.of(
                user(UUID.randomUUID(), itiId, 10, false),
                user(UUID.randomUUID(), itiId, 1000, true),
                user(UUID.randomUUID(), cfiId, 20, false)
        ));

        leaderboardService.release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());

        assertThat(entries).extracting(LeaderboardEntry::getName).containsExactly("CFI", "ITI");
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(20L, 10L);
    }

    /**
     * L'ecran d'administration des formules decrit des filieres.
     *
     * <p>PERF n'est le departement de personne : le faire figurer, c'est offrir une
     * ligne a zero inscrit et un effectif theorique a renseigner qui ne correspond a
     * rien. La liste des departements se demande donc sans les audiences.</p>
     */
    @Test
    void theAdminFormulaPreviewDescribesFilieresAndNotAudiences() {
        UUID departmentId = UUID.randomUUID();
        when(departmentRepository.findAllByVirtualFalse())
                .thenReturn(List.of(department(departmentId, "GC")));
        when(userRepository.findAll())
                .thenReturn(List.of(user(UUID.randomUUID(), departmentId, 10, false)));

        var preview = leaderboardService.getDepartmentFormulaPreview(LeaderboardMetric.TOURNAMENT_POINTS);

        assertThat(preview.rows())
                .extracting(fr.insastronaute.webapi.leaderboard.dto.DepartmentFormulaPreviewRowDto::shortName)
                .containsExactly("GC");
    }

    /**
     * Un apprenti donne ses points a sa filiere sans peser sur son effectif.
     *
     * <p>C'est un choix, et il avantage sa filiere : les formules divisent par le
     * nombre d'inscrits, donc des points qui arrivent sans inscrit gonflent le
     * coefficient. Les apprentis sont assez peu nombreux pour que l'ecart reste petit,
     * et l'ecran d'administration l'affiche plutot que de le cacher.</p>
     */
    @Test
    void anApprenticeAddsTheirPointsToTheirDepartmentWithoutCountingInItsHeadcount() {
        UUID departmentId = UUID.randomUUID();
        AppUser classmate = user(UUID.randomUUID(), departmentId, 10, false);
        AppUser apprentice = user(UUID.randomUUID(), departmentId, 100, false);
        apprentice.setPerf(true);

        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "GC")));
        when(userRepository.findAll()).thenReturn(List.of(classmate, apprentice));

        var departments = leaderboardService.getLiveDepartmentLeaderboards(
                List.of(LeaderboardMetric.TOURNAMENT_POINTS)
        ).getFirst();

        assertThat(departments.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.score()).isEqualTo(110L);
            assertThat(entry.memberCount()).isEqualTo(1);
            // Le biais est assume, donc il s'affiche : l'administrateur voit combien de
            // points sont arrives sans inscrit derriere.
            assertThat(entry.perfCount()).isEqualTo(1);
        });
    }

    @Test
    void liveLeaderboardsExcludeLimitedStudentsFromPlayersAndDepartmentTotals() {
        UUID departmentId = UUID.randomUUID();
        AppUser regular = user("sub-student", UUID.randomUUID(), departmentId, 10, false);
        regular.setDisplayName("Normal student");
        AppUser limited = user("sub-limited", UUID.randomUUID(), departmentId, 1000, false);
        limited.setDisplayName("Limited student");

        when(roleDirectory.isKnown()).thenReturn(true);
        when(roleDirectory.students()).thenReturn(java.util.Set.of("sub-student", "sub-limited"));
        when(roleDirectory.teamStaff()).thenReturn(java.util.Set.of("sub-limited"));
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(regular, limited));

        var users = leaderboardService.getLiveUserLeaderboards(
                List.of(LeaderboardMetric.TOURNAMENT_POINTS)
        ).getFirst();
        var departments = leaderboardService.getLiveDepartmentLeaderboards(
                List.of(LeaderboardMetric.TOURNAMENT_POINTS)
        ).getFirst();

        assertThat(users.entries())
                .extracting(UserLeaderboardEntryDto::name)
                .containsExactly("Normal student");
        assertThat(users.entries())
                .extracting(UserLeaderboardEntryDto::score)
                .containsExactly(10L);
        assertThat(departments.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.name()).isEqualTo("ITI");
            assertThat(entry.score()).isEqualTo(10L);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedSnapshotsExcludeLimitedStudentsFromPlayersAndDepartmentTotals() {
        UUID departmentId = UUID.randomUUID();
        AppUser regular = user("sub-student", UUID.randomUUID(), departmentId, 10, false);
        regular.setDisplayName("Normal student");
        AppUser limited = user("sub-limited", UUID.randomUUID(), departmentId, 1000, false);
        limited.setDisplayName("Limited student");
        LeaderboardConfig userConfig = userTokensConfig();
        LeaderboardConfig departmentConfig = departmentTokensConfig();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(roleDirectory.isKnown()).thenReturn(true);
        when(roleDirectory.students()).thenReturn(java.util.Set.of("sub-student", "sub-limited"));
        when(roleDirectory.teamStaff()).thenReturn(java.util.Set.of("sub-limited"));
        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(userConfig));
        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(departmentConfig));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(regular, limited));

        leaderboardService.release(LeaderboardScope.USER, LeaderboardMetric.TOURNAMENT_POINTS);
        leaderboardService.release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS);

        verify(entryRepository, times(2)).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> userEntries = org.assertj.core.util.Lists.newArrayList(
                entriesCaptor.getAllValues().getFirst()
        );
        List<LeaderboardEntry> departmentEntries = org.assertj.core.util.Lists.newArrayList(
                entriesCaptor.getAllValues().get(1)
        );

        assertThat(userEntries).singleElement().satisfies(entry -> {
            assertThat(entry.getSubjectId()).isEqualTo(regular.getId());
            assertThat(entry.getScore()).isEqualTo(10L);
        });
        assertThat(departmentEntries).singleElement().satisfies(entry -> {
            assertThat(entry.getName()).isEqualTo("ITI");
            assertThat(entry.getScore()).isEqualTo(10L);
        });
    }

    @Test
    void latestDepartmentLeaderboardReleasesANewSnapshotAfterLimitedClassification() {
        UUID departmentId = UUID.randomUUID();
        AppUser regular = user("sub-student", UUID.randomUUID(), departmentId, 10, false);
        AppUser limited = user("sub-limited", UUID.randomUUID(), departmentId, 1000, false);
        LeaderboardConfig config = departmentTokensConfig();
        LeaderboardSnapshot preClassificationSnapshot = new LeaderboardSnapshot();
        preClassificationSnapshot.setConfig(config);
        preClassificationSnapshot.setReleasedAt(Instant.parse("2026-09-05T12:00:00Z"));
        List<LeaderboardEntry> preClassificationEntries = List.of(
                entry(preClassificationSnapshot, departmentId, "ITI", null, 1010L)
        );
        java.util.concurrent.atomic.AtomicReference<LeaderboardSnapshot> latestSnapshot =
                new java.util.concurrent.atomic.AtomicReference<>(preClassificationSnapshot);
        java.util.concurrent.atomic.AtomicReference<List<LeaderboardEntry>> latestEntries =
                new java.util.concurrent.atomic.AtomicReference<>(preClassificationEntries);

        when(roleDirectory.isKnown()).thenReturn(true);
        when(roleDirectory.students()).thenReturn(java.util.Set.of("sub-student", "sub-limited"));
        when(roleDirectory.teamStaff()).thenReturn(java.util.Set.of("sub-limited"));
        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config))
                .thenAnswer(invocation -> Optional.of(latestSnapshot.get()));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenAnswer(invocation -> {
                    LeaderboardSnapshot saved = invocation.getArgument(0);
                    latestSnapshot.set(saved);
                    return saved;
                });
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(regular, limited));
        when(entryRepository.saveAll(org.mockito.ArgumentMatchers.<Iterable<LeaderboardEntry>>any()))
                .thenAnswer(invocation -> {
                    Iterable<LeaderboardEntry> savedEntries = invocation.getArgument(0);
                    List<LeaderboardEntry> saved = org.assertj.core.util.Lists.newArrayList(savedEntries);
                    latestEntries.set(saved);
                    return saved;
                });
        when(entryRepository.findBySnapshotOrderByRankAsc(
                org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)
        )).thenAnswer(invocation -> invocation.getArgument(0) == preClassificationSnapshot
                ? preClassificationEntries
                : latestEntries.get());

        var leaderboard = leaderboardService.getLatestDepartmentLeaderboards(
                List.of(LeaderboardMetric.TOURNAMENT_POINTS)
        ).getFirst();

        assertThat(leaderboard.entries()).singleElement().satisfies(department -> {
            assertThat(department.name()).isEqualTo("ITI");
            assertThat(department.score()).isEqualTo(10L);
        });
        assertThat(leaderboard.calculatedAt()).isAfter(preClassificationSnapshot.getReleasedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void virtualCollectionReleaseCalculatesCurrentCardPointsAndExcludesGameCards() {
        UUID departmentId = UUID.randomUUID();
        AppUser collector = user(UUID.randomUUID(), departmentId, 0, false);
        AppUser gamer = user(UUID.randomUUID(), departmentId, 0, false);
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.USER);
        config.setMetric(LeaderboardMetric.VIRTUAL_COLLECTION_POINTS);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        CardCategory rare = cardCategory(false, 100, 10);
        Card collectible = card(rare, null, null);
        CardCategory game = cardCategory(true, 999, 999);
        Card gameCard = card(game, 500, 500);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.VIRTUAL_COLLECTION_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class))).thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(collector, gamer));
        when(cardCollectionRepository.findByCollectionTypeForScoring(CardCollectionType.VIRTUAL)).thenReturn(List.of(
                collection(collector, collectible, CardCollectionType.VIRTUAL, 3),
                collection(gamer, gameCard, CardCollectionType.VIRTUAL, 20)
        ));

        leaderboardService.release(LeaderboardScope.USER, LeaderboardMetric.VIRTUAL_COLLECTION_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(120L, 0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void physicalCollectionReleaseDoublesTheFirstAcquisitionAndIgnoresDuplicates() {
        UUID departmentId = UUID.randomUUID();
        AppUser collector = user(UUID.randomUUID(), departmentId, 0, false);
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.USER);
        config.setMetric(LeaderboardMetric.PHYSICAL_COLLECTION_POINTS);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        Card collectible = card(cardCategory(false, 100, 10), 150, 5);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.PHYSICAL_COLLECTION_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class))).thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(collector));
        when(cardCollectionRepository.findByCollectionTypeForScoring(CardCollectionType.PHYSICAL)).thenReturn(List.of(
                collection(collector, collectible, CardCollectionType.PHYSICAL, 2)
        ));

        leaderboardService.release(LeaderboardScope.USER, LeaderboardMetric.PHYSICAL_COLLECTION_POINTS);

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());
        // A physical card pays its first acquisition twice over and nothing for the
        // second copy: 150 x 2, not 150 + 5.
        assertThat(entries).extracting(LeaderboardEntry::getScore).containsExactly(300L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void latestUserLeaderboardCreatesZeroSnapshotWhenNoSnapshotExists() {
        UUID departmentId = UUID.randomUUID();
        LeaderboardConfig config = userTokensConfig();
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.empty());
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(
                user(UUID.randomUUID(), departmentId, 500, false),
                user(UUID.randomUUID(), departmentId, 800, true)
        ));
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(List.of());

        leaderboardService.getLatestUserLeaderboards(List.of(LeaderboardMetric.TOURNAMENT_POINTS));

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getScore()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroEloSnapshotListsNobodyRatherThanEveryoneAtZero() {
        UUID departmentId = UUID.randomUUID();
        LeaderboardConfig config = userConfig(LeaderboardMetric.PVP_ELO);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.PVP_ELO))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.empty());
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        org.mockito.Mockito.lenient().when(userRepository.findAll()).thenReturn(List.of(
                user(UUID.randomUUID(), departmentId, 500, false)
        ));
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(List.of());

        leaderboardService.getLatestUserLeaderboards(List.of(LeaderboardMetric.PVP_ELO));

        verify(entryRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).isEmpty();
    }

    /**
     * Le classement departements a zero suit la population du classement joueurs.
     *
     * <p>Il listait auparavant tous les departements de la base : l'ecran admin
     * montrait GM a 0 alors que le tableau des joueurs, lui, etait vide. Aucun
     * joueur, aucun departement.</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void zeroDepartmentSnapshotKeepsOnlyDepartmentsThatHaveAVisiblePlayer() {
        UUID staffed = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        LeaderboardConfig config = departmentTokensConfig();
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.empty());
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(
                department(staffed, "ITI"),
                department(empty, "GM")
        ));
        when(userRepository.findAll()).thenReturn(List.of(user(UUID.randomUUID(), staffed, 500, false)));
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(List.of());

        leaderboardService.getLatestDepartmentLeaderboards(List.of(LeaderboardMetric.TOURNAMENT_POINTS));

        verify(entryRepository).saveAll(entriesCaptor.capture());
        List<LeaderboardEntry> entries = org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue());

        assertThat(entries).extracting(LeaderboardEntry::getName).containsExactly("ITI");
        assertThat(entries.getFirst().getScore()).isZero();
    }

    /**
     * Le total des departements se pondere comme celui des joueurs.
     *
     * <p>Le poids vit sur la configuration joueur de la metrique ; il part avec chaque
     * classement departement, sans quoi le total des departements additionnerait des
     * points bruts quand celui des joueurs les pondere.</p>
     */
    @Test
    void departmentLeaderboardCarriesTheTotalWeightOfItsMetric() {
        LeaderboardConfig departmentConfig = new LeaderboardConfig();
        departmentConfig.setScope(LeaderboardScope.DEPARTMENT);
        departmentConfig.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        LeaderboardConfig userConfig = new LeaderboardConfig();
        userConfig.setScope(LeaderboardScope.USER);
        userConfig.setMetric(LeaderboardMetric.CHALLENGE_POINTS);
        userConfig.setTotalWeightPercent(40);
        userConfig.setTotalScaleFactor(10);
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        snapshot.setReleasedAt(Instant.parse("2026-09-23T12:00:00Z"));

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(departmentConfig));
        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(userConfig));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(departmentConfig)).thenReturn(Optional.of(snapshot));
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(List.of());

        var leaderboard = leaderboardService.getLatestDepartmentLeaderboards(
                List.of(LeaderboardMetric.CHALLENGE_POINTS)
        ).getFirst();

        assertThat(leaderboard.totalWeightPercent()).isEqualTo(40);
        // Le facteur part avec le poids : le total des departements doit mettre les
        // categories a la meme echelle que celui des joueurs.
        assertThat(leaderboard.totalScaleFactor()).isEqualTo(10);
    }

    // ── Facteur d'echelle du total ────────────────────────────────────────────
    // Les points de cartes se comptent en milliers quand ceux des defis se comptent en
    // centaines de milliers : sans facteur, leur poids ne pese presque rien. Le facteur
    // multiplie les points d'une categorie avant son poids, dans le total seulement.

    @Test
    void adminLiveTotalMultipliesPointsByTheirScaleFactorBeforeWeighing() {
        UUID departmentId = UUID.randomUUID();
        AppUser alice = user("alice", UUID.randomUUID(), departmentId, 100, false);
        alice.setDisplayName("Alice");
        alice.setChallengePoints(200L);
        AppUser bob = user("bob", UUID.randomUUID(), departmentId, 200, false);
        bob.setDisplayName("Bob");
        bob.setChallengePoints(50L);

        LeaderboardConfig tournaments = userTokensConfig();
        tournaments.setTotalWeightPercent(25);
        LeaderboardConfig challenges = userConfig(LeaderboardMetric.CHALLENGE_POINTS);
        challenges.setTotalWeightPercent(75);
        challenges.setTotalScaleFactor(10);

        when(configRepository.findAll()).thenReturn(List.of(tournaments, challenges));
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(departmentId, "ITI")));
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        var total = leaderboardService.getAdminUserTotalLeaderboard(true);

        // Alice : 100 x 25 % + 200 x 10 x 75 % ; Bob : 200 x 25 % + 50 x 10 x 75 %.
        assertThat(total.entries()).extracting(UserLeaderboardEntryDto::score).containsExactly(1525L, 425L);
    }

    @Test
    void snapshotTotalsMultiplyPointsByTheirScaleFactorBeforeWeighing() {
        AppUser requester = user(UUID.randomUUID(), UUID.randomUUID(), 0, false);
        Instant calculatedAt = Instant.parse("2026-09-27T12:00:00Z");
        for (LeaderboardMetric metric : totalMetrics()) {
            LeaderboardConfig config = userConfig(metric);
            config.setTotalWeightPercent(100);
            config.setTotalScaleFactor(metric == LeaderboardMetric.PHYSICAL_COLLECTION_POINTS ? 10 : 1);
            LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
            snapshot.setReleasedAt(calculatedAt);
            LeaderboardEntry entry = leaderboardEntry(snapshot, 1, requester.getId());
            entry.setScore(5L);

            when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, metric)).thenReturn(Optional.of(config));
            when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.of(snapshot));
            when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(List.of(entry));
        }
        when(friendshipRepository.findAcceptedFriendIds(requester.getId())).thenReturn(List.of());

        // 5 points dans chaque categorie, dont les cartes physiques comptees dix fois.
        long expected = 5L * (totalMetrics().size() - 1) + 50L;
        assertThat(leaderboardService.getLatestUserTotalLeaderboard(requester).entries().getFirst().score())
                .isEqualTo(expected);
        assertThat(leaderboardService.getAdminUserTotalLeaderboard(false).entries().getFirst().score())
                .isEqualTo(expected);
    }

    @Test
    void storesTheScaleFactorsAlongTheWeights() {
        LeaderboardConfig challenges = userConfig(LeaderboardMetric.CHALLENGE_POINTS);
        LeaderboardConfig cards = userConfig(LeaderboardMetric.PHYSICAL_COLLECTION_POINTS);
        when(configRepository.findAll()).thenReturn(List.of(challenges, cards));
        when(configRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        leaderboardService.updateTotalLeaderboardWeights(
                java.util.Map.of(LeaderboardMetric.CHALLENGE_POINTS, 40, LeaderboardMetric.PHYSICAL_COLLECTION_POINTS, 60),
                java.util.Map.of(LeaderboardMetric.CHALLENGE_POINTS, 1, LeaderboardMetric.PHYSICAL_COLLECTION_POINTS, 10));

        assertThat(challenges.getTotalScaleFactor()).isEqualTo(1);
        assertThat(cards.getTotalScaleFactor()).isEqualTo(10);
    }

    /** Un panneau qui ne connait pas encore les facteurs n'envoie que les poids : les facteurs restent. */
    @Test
    void weightsSentAloneLeaveTheScaleFactorsUntouched() {
        LeaderboardConfig cards = userConfig(LeaderboardMetric.PHYSICAL_COLLECTION_POINTS);
        cards.setTotalScaleFactor(10);
        when(configRepository.findAll()).thenReturn(List.of(cards));
        when(configRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        leaderboardService.updateTotalLeaderboardWeights(
                java.util.Map.of(LeaderboardMetric.PHYSICAL_COLLECTION_POINTS, 100), null);

        assertThat(cards.getTotalScaleFactor()).isEqualTo(10);
    }

    /** Un facteur nul effacerait la categorie : c'est le role du poids, pas du facteur. */
    @Test
    void refusesAScaleFactorBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> leaderboardService.updateTotalLeaderboardWeights(
                java.util.Map.of(LeaderboardMetric.CHALLENGE_POINTS, 100),
                java.util.Map.of(LeaderboardMetric.CHALLENGE_POINTS, 0)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroDepartmentSnapshotIsEmptyWhenNoPlayerExists() {
        LeaderboardConfig config = departmentTokensConfig();
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        ArgumentCaptor<Iterable<LeaderboardEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);

        when(configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.empty());
        when(snapshotRepository.save(org.mockito.ArgumentMatchers.any(LeaderboardSnapshot.class)))
                .thenReturn(snapshot);
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department(UUID.randomUUID(), "GM")));
        when(userRepository.findAll()).thenReturn(List.of());
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(List.of());

        leaderboardService.getLatestDepartmentLeaderboards(List.of(LeaderboardMetric.TOURNAMENT_POINTS));

        verify(entryRepository).saveAll(entriesCaptor.capture());
        assertThat(org.assertj.core.util.Lists.newArrayList(entriesCaptor.getValue())).isEmpty();
    }

    @Test
    void personalizedUserLeaderboardKeepsTopTenAcceptedFriendsAndRequesterWithOriginalRanks() {
        LeaderboardConfig config = userTokensConfig();
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        AppUser requester = user(UUID.randomUUID(), UUID.randomUUID(), 0, false);
        UUID friendId = UUID.randomUUID();
        List<LeaderboardEntry> entries = java.util.stream.IntStream.rangeClosed(1, 13)
                .mapToObj(rank -> leaderboardEntry(
                        snapshot,
                        rank,
                        rank == 11 ? friendId : rank == 12 ? requester.getId() : UUID.randomUUID()
                ))
                .toList();

        when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.TOURNAMENT_POINTS))
                .thenReturn(Optional.of(config));
        when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.of(snapshot));
        when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(entries);
        when(friendshipRepository.findAcceptedFriendIds(requester.getId())).thenReturn(List.of(friendId));

        var leaderboard = leaderboardService.getLatestUserLeaderboards(
                List.of(LeaderboardMetric.TOURNAMENT_POINTS),
                requester
        ).getFirst();

        assertThat(leaderboard.entries())
                .extracting(UserLeaderboardEntryDto::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void totalUserLeaderboardExcludesHiddenUsersEvenIfTheyAppearInExistingSnapshots() {
        AppUser requester = user(UUID.randomUUID(), UUID.randomUUID(), 0, false);
        UUID hiddenUserId = UUID.randomUUID();
        AppUser hiddenUser = user(hiddenUserId, UUID.randomUUID(), 9999, true);
        Instant calculatedAt = Instant.parse("2026-08-10T12:00:00Z");

        // Seuls les classements de points entrent dans le total : l'Elo n'y est ni lu ni pese.
        for (LeaderboardMetric metric : totalMetrics()) {
            LeaderboardConfig config = new LeaderboardConfig();
            config.setScope(LeaderboardScope.USER);
            config.setMetric(metric);
            // Ce test vérifie l'agrégation historique, pas la pondération : 100
            // conserve volontairement le total brut précédent pour chaque source.
            config.setTotalWeightPercent(100);
            LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
            snapshot.setReleasedAt(calculatedAt);
            // Le snapshot contient le compte de service (créé avant qu'il soit masqué).
            List<LeaderboardEntry> entries = List.of(
                    leaderboardEntry(snapshot, 1, hiddenUserId),
                    leaderboardEntry(snapshot, 2, requester.getId())
            );

            when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, metric))
                    .thenReturn(Optional.of(config));
            when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config))
                    .thenReturn(Optional.of(snapshot));
            when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(entries);
        }
        when(userRepository.findAll()).thenReturn(List.of(hiddenUser, requester));
        when(friendshipRepository.findAcceptedFriendIds(requester.getId())).thenReturn(List.of());

        var total = leaderboardService.getLatestUserTotalLeaderboard(requester);

        // Le compte de service masqué ne doit pas apparaître dans le classement.
        assertThat(total.entries()).hasSize(1);
        assertThat(total.entries().getFirst().rank()).isEqualTo(1);
    }

    @Test
    void totalUserLeaderboardSumsLatestMetricSnapshotsBeforeRankingAndPersonalizing() {
        AppUser requester = user(UUID.randomUUID(), UUID.randomUUID(), 0, false);
        UUID friendId = UUID.randomUUID();
        List<UUID> userIds = java.util.stream.IntStream.rangeClosed(1, 13)
                .mapToObj(rank -> rank == 11 ? friendId : rank == 12 ? requester.getId() : UUID.randomUUID())
                .toList();
        Instant calculatedAt = Instant.parse("2026-07-27T12:00:00Z");

        // Seuls les classements de points entrent dans le total : l'Elo n'y est ni lu ni pese.
        for (LeaderboardMetric metric : totalMetrics()) {
            LeaderboardConfig config = new LeaderboardConfig();
            config.setScope(LeaderboardScope.USER);
            config.setMetric(metric);
            config.setTotalWeightPercent(100);
            LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
            snapshot.setReleasedAt(calculatedAt);
            List<LeaderboardEntry> entries = java.util.stream.IntStream.rangeClosed(1, 13)
                    .mapToObj(rank -> {
                        LeaderboardEntry entry = leaderboardEntry(snapshot, rank, userIds.get(rank - 1));
                        entry.setScore(100L - rank);
                        return entry;
                    })
                    .toList();

            when(configRepository.findByScopeAndMetric(LeaderboardScope.USER, metric))
                    .thenReturn(Optional.of(config));
            when(snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config))
                    .thenReturn(Optional.of(snapshot));
            when(entryRepository.findBySnapshotOrderByRankAsc(snapshot)).thenReturn(entries);
        }
        when(friendshipRepository.findAcceptedFriendIds(requester.getId())).thenReturn(List.of(friendId));

        var total = leaderboardService.getLatestUserTotalLeaderboard(requester);

        assertThat(total.calculatedAt()).isEqualTo(calculatedAt);
        assertThat(total.entries()).extracting(UserLeaderboardEntryDto::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        // Le total additionne tous les classements de points : l'attente se déduit de leur
        // nombre, sinon ajouter une catégorie casserait ce test sans rien dire de
        // faux — c'est exactement ce qu'il a fait à l'arrivée du classement du jeu.
        // « De points » et non « tous » : l'Elo n'est pas un compte, et n'y entre pas.
        long metrics = totalMetrics().size();
        assertThat(total.entries().getFirst().score()).isEqualTo(99L * metrics);
        assertThat(total.entries().getLast().score()).isEqualTo(88L * metrics);

        var adminTotal = leaderboardService.getAdminUserTotalLeaderboard(false);
        assertThat(adminTotal.entries()).hasSize(13);
        assertThat(adminTotal.entries()).extracting(UserLeaderboardEntryDto::rank)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 13).boxed().toList());
    }

    /** Les classements que le total additionne. */
    private static List<LeaderboardMetric> totalMetrics() {
        return java.util.Arrays.stream(LeaderboardMetric.values())
                .filter(LeaderboardMetric::countsTowardTotal)
                .toList();
    }

    private static LeaderboardConfig departmentTokensConfig() {
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.DEPARTMENT);
        config.setMetric(LeaderboardMetric.TOURNAMENT_POINTS);
        config.setRefreshDelayMinutes(60);
        config.setAutoRefreshEnabled(false);

        return config;
    }

    private static LeaderboardConfig userTokensConfig() {
        LeaderboardConfig config = new LeaderboardConfig();
        config.setScope(LeaderboardScope.USER);
        config.setMetric(LeaderboardMetric.TOURNAMENT_POINTS);
        config.setRefreshDelayMinutes(60);
        config.setAutoRefreshEnabled(false);

        return config;
    }

    private static Department department(UUID id, String shortName) {
        Department department = new Department();
        department.setId(id);
        department.setShortName(shortName);

        return department;
    }

    private static AppUser user(UUID id, UUID departmentId, long tournamentPoints, boolean hidden) {
        return user(null, id, departmentId, tournamentPoints, hidden);
    }

    private static AppUser user(
            String keycloakSub,
            UUID id,
            UUID departmentId,
            long tournamentPoints,
            boolean hidden
    ) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        user.setEmail("user@example.com");
        user.setDisplayName("User");
        user.setDepartment(departmentId);
        user.setTournamentPoints(tournamentPoints);
        user.setHiddenFromLeaderboards(hidden);

        return user;
    }

    private static LeaderboardEntry leaderboardEntry(LeaderboardSnapshot snapshot, int rank, UUID subjectId) {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setSnapshot(snapshot);
        entry.setRank(rank);
        entry.setSubjectId(subjectId);
        entry.setName("User " + rank);
        entry.setDepartment("ITI");
        entry.setScore(100L - rank);
        return entry;
    }

    private static CardCategory cardCategory(boolean game, int firstAcquisitionPoints, int duplicatePoints) {
        CardCategory category = new CardCategory();
        category.setId(UUID.randomUUID());
        category.setName(game ? "Game" : "Rare");
        category.setGame(game);
        category.setFirstAcquisitionPoints(firstAcquisitionPoints);
        category.setDuplicatePoints(duplicatePoints);
        return category;
    }

    private static Card card(CardCategory category, Integer firstAcquisitionPoints, Integer duplicatePoints) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Card");
        card.setCategoryRef(category);
        card.setFirstAcquisitionPoints(firstAcquisitionPoints);
        card.setDuplicatePoints(duplicatePoints);
        return card;
    }

    private static CardCollection collection(
            AppUser user,
            Card card,
            CardCollectionType type,
            int quantity
    ) {
        CardCollection collection = new CardCollection();
        collection.setUser(user);
        collection.setCard(card);
        collection.setCollectionType(type);
        collection.setQuantity(quantity);
        return collection;
    }
}
