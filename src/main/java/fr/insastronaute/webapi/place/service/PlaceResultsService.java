package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentContributionsDto;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentResultDto;
import fr.insastronaute.webapi.place.dto.PlacePlayerResultDto;
import fr.insastronaute.webapi.place.dto.PlaceResultsDto;
import fr.insastronaute.webapi.place.repository.PlacePixelCount;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ce que la fresque laisse derriere elle.
 *
 * <p>Deux classements repondent a deux questions differentes. Le territoire dit ce
 * qu'on voit sur la fresque des departements a la derniere seconde ; les pixels poses
 * disent qui a travaille. Un departement recouvert juste avant la fin garde le second
 * sans avoir le premier, et afficher l'un pour l'autre ferait mentir la page.</p>
 */
@Service
public class PlaceResultsService {

    /** Au-dela, personne ne lit — et chacun garde sa propre ligne de toute facon. */
    private static final int TOP = 10;

    /**
     * L'evenement est fini, le chiffre ne bouge plus. La fenetre existe quand meme :
     * une image censuree apres la fin change le territoire, et rien ne previent ici.
     */
    private static final Duration FRESHNESS = Duration.ofMinutes(5);

    private final PlaceConfigService configService;
    private final PlaceDepartmentFresqueService departmentFresque;
    private final PlacePixelPlacementRepository placements;
    private final UserRepository users;
    private final DepartmentRepository departments;

    private final AtomicReference<Ranking> cached = new AtomicReference<>();

    public PlaceResultsService(PlaceConfigService configService,
                               PlaceDepartmentFresqueService departmentFresque,
                               PlacePixelPlacementRepository placements,
                               UserRepository users,
                               DepartmentRepository departments) {
        this.configService = configService;
        this.departmentFresque = departmentFresque;
        this.placements = placements;
        this.users = users;
        this.departments = departments;
    }

    /**
     * Le palmares, tel que le voit ce joueur.
     *
     * @throws NotFoundException tant que les resultats ne sont pas publies. Un 404
     *                           plutot qu'une page vide : le classement d'un evenement
     *                           en cours n'existe pas encore, et le laisser lire par
     *                           l'URL le publierait avant l'heure.
     */
    @Transactional(readOnly = true)
    public PlaceResultsDto results(String keycloakSubject) {
        PlaceConfig config = configService.currentConfig();
        Instant now = TimeTravelContext.instantNow();
        if (!config.showsResultsAt(now)) {
            throw new NotFoundException("Les résultats de Place ne sont pas publiés.");
        }

        Ranking ranking = ranking(config, now);
        UUID me = keycloakSubject == null ? null : users.findByKeycloakSub(keycloakSubject)
                .map(AppUser::getId)
                .orElse(null);

        Ranked mine = me == null ? null : ranking.byUser().get(me);
        List<PlacePlayerResultDto> players = ranking.playersTop().stream()
                .map(player -> player.toDto(Objects.equals(player.userId(), me)))
                .toList();

        return new PlaceResultsDto(
                config.getEndDate(),
                ranking.totalPixels(),
                ranking.departments(),
                players,
                mine == null ? null : mine.toDto(true));
    }

    private Ranking ranking(PlaceConfig config, Instant now) {
        Ranking current = cached.get();
        if (current != null && current.matches(config) && !current.isStale(now)) {
            return current;
        }
        Ranking computed = compute(config, now);
        cached.set(computed);
        return computed;
    }

    private Ranking compute(PlaceConfig config, Instant now) {
        List<PlacePixelCount> counts = placements.countPixelsByUserBetween(config.getStartDate(), config.getEndDate());
        long totalPixels = counts.stream().mapToLong(PlacePixelCount::pixels).sum();

        Map<UUID, AppUser> people = new HashMap<>();
        if (!counts.isEmpty()) {
            users.findAllById(counts.stream().map(PlacePixelCount::userId).toList())
                    .forEach(user -> people.put(user.getId(), user));
        }

        Map<UUID, Department> byId = new HashMap<>();
        departments.findAll().forEach(department -> byId.put(department.getId(), department));

        Map<UUID, Long> placed = new HashMap<>();
        for (PlacePixelCount count : counts) {
            AppUser user = people.get(count.userId());
            if (user == null || user.getDepartment() == null) continue;
            placed.merge(user.getDepartment(), count.pixels(), Long::sum);
        }

        // Le territoire se lit la ou il se dessine : la fresque des departements est
        // une vue de la fresque libre, et son calcul dit deja quelles cases chacun
        // tient. Le refaire ici donnerait deux chiffres pour la meme image.
        PlaceDepartmentContributionsDto contributions = departmentFresque.contributions();
        Map<UUID, Long> territory = new HashMap<>();
        contributions.departments().forEach(entry -> territory.put(entry.departmentId(), entry.pixels()));

        return new Ranking(
                config.getStartDate(), config.getEndDate(), now, totalPixels,
                departmentRows(byId, territory, placed, contributions.attributedPixels()),
                players(counts, people, byId));
    }

    private static List<PlaceDepartmentResultDto> departmentRows(Map<UUID, Department> byId,
                                                                 Map<UUID, Long> territory,
                                                                 Map<UUID, Long> placed,
                                                                 long attributedPixels) {
        // Les departements sans territoire ni pose ne descendent pas : un departement
        // virtuel — une audience dont personne n'est membre — n'aurait rien a faire
        // dans un classement de l'ecole, et il n'apparait ici par aucun des deux bouts.
        List<UUID> present = new ArrayList<>(byId.keySet());
        present.removeIf(id -> territory.getOrDefault(id, 0L) == 0L && placed.getOrDefault(id, 0L) == 0L);

        Comparator<UUID> byTerritory = Comparator.comparingLong((UUID id) -> territory.getOrDefault(id, 0L)).reversed();
        Comparator<UUID> byPlaced = Comparator.comparingLong((UUID id) -> placed.getOrDefault(id, 0L)).reversed();
        Comparator<UUID> byName = Comparator.comparing((UUID id) -> name(byId.get(id)).toLowerCase(Locale.ROOT));
        present.sort(byTerritory.thenComparing(byPlaced).thenComparing(byName));

        List<PlaceDepartmentResultDto> rows = new ArrayList<>(present.size());
        long previousTerritory = Long.MIN_VALUE;
        long previousPlaced = Long.MIN_VALUE;
        int rank = 0;
        for (int index = 0; index < present.size(); index++) {
            UUID id = present.get(index);
            long tiles = territory.getOrDefault(id, 0L);
            long poses = placed.getOrDefault(id, 0L);
            if (tiles != previousTerritory || poses != previousPlaced) {
                rank = index + 1;
                previousTerritory = tiles;
                previousPlaced = poses;
            }
            Department department = byId.get(id);
            rows.add(new PlaceDepartmentResultDto(rank, name(department), department.getColor(), tiles, poses,
                    attributedPixels == 0 ? 0d : (double) tiles / attributedPixels));
        }
        return rows;
    }

    private static String name(Department department) {
        if (department == null) return "";
        return department.getShortName() != null ? department.getShortName() : department.getName();
    }

    /**
     * Le classement des joueurs, et la ligne de chacun.
     *
     * <p>Le staff est masque des classements partout ailleurs ; il l'est ici aussi.
     * Sa ligne existe pourtant, sans rang : son propre chiffre lui reste montre.</p>
     */
    private static Ranking.Players players(List<PlacePixelCount> counts, Map<UUID, AppUser> people,
                                           Map<UUID, Department> byId) {
        List<Ranked> ranked = new ArrayList<>();
        Map<UUID, Ranked> byUser = new LinkedHashMap<>();

        for (PlacePixelCount count : counts) {
            AppUser user = people.get(count.userId());
            if (user == null) continue;
            String department = user.getDepartment() == null ? null : name(byId.get(user.getDepartment()));
            Ranked entry = new Ranked(count.userId(), null, displayName(user), department, count.pixels());
            if (Boolean.TRUE.equals(user.getHiddenFromLeaderboards())) {
                byUser.put(count.userId(), entry);
            } else {
                ranked.add(entry);
            }
        }

        ranked.sort(Comparator.comparingLong(Ranked::pixels).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));

        List<Ranked> withRanks = new ArrayList<>(ranked.size());
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            Ranked entry = ranked.get(index);
            if (entry.pixels() != previous) {
                rank = index + 1;
                previous = entry.pixels();
            }
            Ranked placedEntry = entry.at(rank);
            withRanks.add(placedEntry);
            byUser.put(placedEntry.userId(), placedEntry);
        }

        return new Ranking.Players(withRanks.stream().limit(TOP).toList(), byUser);
    }

    private static String displayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail() == null ? "" : user.getEmail();
    }

    /** Une ligne du classement joueur, avant qu'on sache qui la regarde. */
    private record Ranked(UUID userId, Integer rank, String name, String department, long pixels) {
        Ranked at(int rank) {
            return new Ranked(userId, rank, name, department, pixels);
        }

        PlacePlayerResultDto toDto(boolean you) {
            return new PlacePlayerResultDto(rank, name, department, pixels, you);
        }
    }

    /**
     * Le calcul, fait une fois.
     *
     * <p>Relire la fresque entiere et regrouper toute la table des poses a chaque
     * ouverture rendrait le meme chiffre : l'evenement est termine.</p>
     */
    private record Ranking(Instant from, Instant to, Instant computedAt, long totalPixels,
                           List<PlaceDepartmentResultDto> departments, Players players) {

        record Players(List<Ranked> top, Map<UUID, Ranked> byUser) {
        }

        boolean matches(PlaceConfig config) {
            return Objects.equals(from, config.getStartDate()) && Objects.equals(to, config.getEndDate());
        }

        boolean isStale(Instant now) {
            return now.isBefore(computedAt) || !now.isBefore(computedAt.plus(FRESHNESS));
        }

        List<Ranked> playersTop() {
            return players.top();
        }

        Map<UUID, Ranked> byUser() {
            return players.byUser();
        }
    }
}
