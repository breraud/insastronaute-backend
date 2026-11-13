package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentContributionsDto;
import fr.insastronaute.webapi.place.dto.PlaceResultsDto;
import fr.insastronaute.webapi.place.repository.PlacePixelCount;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceResultsServiceTest {

    private final PlaceConfigService configService = mock(PlaceConfigService.class);
    private final PlaceDepartmentFresqueService departmentFresque = mock(PlaceDepartmentFresqueService.class);
    private final PlacePixelPlacementRepository placements = mock(PlacePixelPlacementRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);

    private static final String GM_COLOR = "#3b82f6";
    private static final String IF_COLOR = "#e50000";

    private PlaceConfig config;
    private PlaceResultsService service;

    private final UUID gmId = UUID.randomUUID();
    private final UUID ifId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PlaceResultsService(configService, departmentFresque, placements, users, departments);

        config = new PlaceConfig();
        config.setActive(true);
        config.setStartDate(Instant.now().minus(Duration.ofDays(2)));
        config.setEndDate(Instant.now().minus(Duration.ofHours(1)));
        config.setCanvasWidth(2);
        config.setCanvasHeight(2);
        config.setResultsVisible(true);
        when(configService.currentConfig()).thenReturn(config);

        when(departments.findAll()).thenReturn(List.of(
                department(gmId, "GM", GM_COLOR),
                department(ifId, "IF", IF_COLOR)));
        territory(3, entry(gmId, "GM", GM_COLOR, 2), entry(ifId, "IF", IF_COLOR, 1));
        when(placements.countPixelsByUserBetween(any(), any())).thenReturn(List.of());
        when(users.findAllById(anyList())).thenReturn(List.of());
    }

    /** Avant la publication, la page des resultats n'existe pas — pas meme pour la lire. */
    @Test
    void refusesToPublishBeforeTheAdminSwitchIsOn() {
        config.setResultsVisible(false);

        assertThat(catchThrowable(() -> service.results("player"))).isInstanceOf(NotFoundException.class);
    }

    /** Ni avant la fin : un classement fige pendant que les pixels bougent serait faux. */
    @Test
    void refusesToPublishBeforeTheEventEnds() {
        config.setEndDate(Instant.now().plus(Duration.ofHours(1)));

        assertThat(catchThrowable(() -> service.results("player"))).isInstanceOf(NotFoundException.class);
    }

    /**
     * Le territoire se lit la ou il se dessine.
     *
     * <p>La fresque des departements est une vue de la fresque libre, et son calcul
     * dit deja quelles cases chacun tient. Le refaire ici donnerait deux chiffres pour
     * la meme image, et l'un des deux serait faux le jour ou la vue changera.</p>
     */
    @Test
    void readsTerritoryFromTheDepartmentView() {
        PlaceResultsDto results = service.results("player");

        assertThat(results.departments()).hasSize(2);
        assertThat(results.departments().get(0).name()).isEqualTo("GM");
        assertThat(results.departments().get(0).territory()).isEqualTo(2);
        assertThat(results.departments().get(0).share()).isEqualTo(2d / 3d);
        assertThat(results.departments().get(1).name()).isEqualTo("IF");
        assertThat(results.departments().get(1).territory()).isEqualTo(1);
    }

    /** Les poses des membres et le territoire tenu sont deux colonnes, pas une. */
    @Test
    void countsPlacedPixelsPerDepartmentBesideTerritory() {
        UUID sonia = UUID.randomUUID();
        UUID tom = UUID.randomUUID();
        placedBy(count(sonia, 30), count(tom, 5));
        knownUsers(player(sonia, "Sonia", ifId), player(tom, "Tom", gmId));

        PlaceResultsDto results = service.results("player");

        assertThat(results.departments())
                .extracting(department -> department.name() + ":" + department.territory() + ":" + department.placed())
                .containsExactly("GM:2:5", "IF:1:30");
    }

    /** Le palmares joueur : les plus gros poseurs, tous canevas confondus. */
    @Test
    void ranksPlayersByPixelsPlaced() {
        UUID sonia = UUID.randomUUID();
        UUID tom = UUID.randomUUID();
        placedBy(count(tom, 5), count(sonia, 30));
        knownUsers(player(sonia, "Sonia", ifId), player(tom, "Tom", gmId));

        PlaceResultsDto results = service.results("sonia-sub");

        assertThat(results.players()).extracting(entry -> entry.rank() + ":" + entry.name() + ":" + entry.pixels())
                .containsExactly("1:Sonia:30", "2:Tom:5");
        assertThat(results.totalPixels()).isEqualTo(35);
    }

    /** Un ex aequo garde son rang partage, et se departage au nom pour l'affichage. */
    @Test
    void sharesTheRankOnATieAndOrdersTiesByName() {
        UUID sonia = UUID.randomUUID();
        UUID tom = UUID.randomUUID();
        UUID ana = UUID.randomUUID();
        placedBy(count(sonia, 10), count(tom, 10), count(ana, 3));
        knownUsers(player(sonia, "Sonia", ifId), player(tom, "Tom", gmId), player(ana, "Ana", gmId));

        PlaceResultsDto results = service.results("player");

        assertThat(results.players()).extracting(entry -> entry.rank() + ":" + entry.name())
                .containsExactly("1:Sonia", "1:Tom", "3:Ana");
    }

    /** Le classement s'arrete a dix lignes : au-dela, personne ne lit. */
    @Test
    void keepsOnlyTheTopTen() {
        List<PlacePixelCount> counts = new ArrayList<>();
        List<AppUser> people = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            UUID id = UUID.randomUUID();
            counts.add(count(id, 100 - index));
            people.add(player(id, "Joueur " + index, gmId));
        }
        placedBy(counts.toArray(new PlacePixelCount[0]));
        knownUsers(people.toArray(new AppUser[0]));

        PlaceResultsDto results = service.results("player");

        assertThat(results.players()).hasSize(10);
        assertThat(results.totalPixels()).isEqualTo(counts.stream().mapToLong(PlacePixelCount::pixels).sum());
    }

    /** Hors du podium, on veut quand meme son propre chiffre. */
    @Test
    void reportsTheCurrentPlayerEvenOutsideTheTopTen() {
        List<PlacePixelCount> counts = new ArrayList<>();
        List<AppUser> people = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            UUID id = UUID.randomUUID();
            counts.add(count(id, 100 - index));
            people.add(player(id, "Joueur " + index, gmId));
        }
        AppUser last = people.get(11);
        last.setKeycloakSub("me-sub");
        when(users.findByKeycloakSub("me-sub")).thenReturn(Optional.of(last));
        placedBy(counts.toArray(new PlacePixelCount[0]));
        knownUsers(people.toArray(new AppUser[0]));

        PlaceResultsDto results = service.results("me-sub");

        assertThat(results.me()).isNotNull();
        assertThat(results.me().rank()).isEqualTo(12);
        assertThat(results.me().pixels()).isEqualTo(89);
        assertThat(results.me().you()).isTrue();
        assertThat(results.players()).noneMatch(entry -> entry.you());
    }

    /** Dans le podium, la ligne du joueur est marquee la ou elle est. */
    @Test
    void marksTheCurrentPlayerInsideTheTopTen() {
        UUID sonia = UUID.randomUUID();
        placedBy(count(sonia, 30));
        AppUser player = player(sonia, "Sonia", ifId);
        player.setKeycloakSub("sonia-sub");
        knownUsers(player);
        when(users.findByKeycloakSub("sonia-sub")).thenReturn(Optional.of(player));

        PlaceResultsDto results = service.results("sonia-sub");

        assertThat(results.players()).singleElement().matches(entry -> entry.you());
        assertThat(results.me().rank()).isEqualTo(1);
    }

    /**
     * Le staff est hors classement, comme partout ailleurs.
     *
     * <p>Il garde son chiffre pour lui : sa ligne existe, sans rang.</p>
     */
    @Test
    void leavesUsersHiddenFromLeaderboardsOutOfTheRanking() {
        UUID sonia = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        placedBy(count(staff, 500), count(sonia, 30));
        AppUser hidden = player(staff, "Respo", gmId);
        hidden.setHiddenFromLeaderboards(true);
        hidden.setKeycloakSub("staff-sub");
        knownUsers(player(sonia, "Sonia", ifId), hidden);
        when(users.findByKeycloakSub("staff-sub")).thenReturn(Optional.of(hidden));

        PlaceResultsDto results = service.results("staff-sub");

        assertThat(results.players()).extracting(entry -> entry.name()).containsExactly("Sonia");
        assertThat(results.me().rank()).isNull();
        assertThat(results.me().pixels()).isEqualTo(500);
    }

    /** Un joueur qui n'a rien pose n'a pas de ligne a lui. */
    @Test
    void reportsNoLineForAPlayerWhoPlacedNothing() {
        AppUser newcomer = player(UUID.randomUUID(), "Curieux", gmId);
        newcomer.setKeycloakSub("curieux-sub");
        when(users.findByKeycloakSub("curieux-sub")).thenReturn(Optional.of(newcomer));

        assertThat(service.results("curieux-sub").me()).isNull();
    }

    /** Les poses comptees sont celles de l'evenement, pas celles d'avant ni d'apres. */
    @Test
    void countsOnlyPlacementsInsideTheEventWindow() {
        service.results("player");

        verify(placements).countPixelsByUserBetween(config.getStartDate(), config.getEndDate());
    }

    /**
     * L'evenement est fini : le resultat ne bouge plus.
     *
     * <p>Le recalculer a chaque ouverture relirait la fresque entiere et referait un
     * regroupement sur toute la table des poses, pour le meme chiffre.</p>
     */
    @Test
    void computesTheRankingOnceAndServesItFromMemory() {
        service.results("player");
        service.results("autre");

        verify(departmentFresque, times(1)).contributions();
        verify(placements, times(1)).countPixelsByUserBetween(any(), any());
    }

    /** Une fresque que personne n'a marquee : pas de territoire, pas de drame. */
    @Test
    void survivesAFrescoNoDepartmentHasMarked() {
        territory(0);

        PlaceResultsDto results = service.results("player");

        assertThat(results.departments()).isEmpty();
    }

    /**
     * Sans territoire, les poses parlent encore.
     *
     * <p>Une case recouverte n'est imputee a personne : un departement peut avoir
     * beaucoup peint et ne rien tenir a la fin.</p>
     */
    @Test
    void stillRanksDepartmentsThatHoldNothingButPlacedALot() {
        territory(0);
        UUID sonia = UUID.randomUUID();
        placedBy(count(sonia, 30));
        knownUsers(player(sonia, "Sonia", ifId));

        PlaceResultsDto results = service.results("player");

        assertThat(results.departments())
                .extracting(entry -> entry.name() + ":" + entry.territory() + ":" + entry.placed())
                .containsExactly("IF:0:30");
        assertThat(results.departments().get(0).share()).isEqualTo(0d);
    }

    // ── Outillage ───────────────────────────────────────────────────────────────

    private void territory(long attributed, PlaceDepartmentContributionsDto.Entry... entries) {
        when(departmentFresque.contributions()).thenReturn(
                new PlaceDepartmentContributionsDto("12", attributed, 0, List.of(entries)));
    }

    private static PlaceDepartmentContributionsDto.Entry entry(UUID id, String shortName, String color, long pixels) {
        return new PlaceDepartmentContributionsDto.Entry(id, shortName, shortName, color, pixels);
    }

    private void placedBy(PlacePixelCount... counts) {
        when(placements.countPixelsByUserBetween(any(), any())).thenReturn(List.of(counts));
    }

    private void knownUsers(AppUser... people) {
        when(users.findAllById(anyList())).thenReturn(List.of(people));
    }

    private static PlacePixelCount count(UUID userId, long pixels) {
        return new PlacePixelCount(userId, pixels);
    }

    private static AppUser player(UUID id, String name, UUID department) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setDisplayName(name);
        user.setDepartment(department);
        return user;
    }

    private static Department department(UUID id, String shortName, String color) {
        Department department = new Department();
        department.setId(id);
        department.setShortName(shortName);
        department.setColor(color);
        return department;
    }
}
