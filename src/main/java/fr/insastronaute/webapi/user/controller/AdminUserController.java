package fr.insastronaute.webapi.user.controller;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.dto.AdminGenderUpdateDto;
import fr.insastronaute.webapi.user.dto.AdminUserDetailDto;
import fr.insastronaute.webapi.user.dto.AdminPlayerDeletionDto;
import fr.insastronaute.webapi.user.dto.AdminPerfUpdateDto;
import fr.insastronaute.webapi.user.dto.AdminStudentLimitedUpdateDto;
import fr.insastronaute.webapi.user.dto.AdminUserDto;
import fr.insastronaute.webapi.user.dto.AdminManualChallengeSubmissionRequest;
import fr.insastronaute.webapi.user.dto.AdminRejectChallengeSubmissionRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardChangeRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardRemovalRequest;
import fr.insastronaute.webapi.user.dto.LeaderboardVisibilityUpdateDto;
import fr.insastronaute.webapi.user.dto.TokenBalanceUpdateDto;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.AdminUserService;
import fr.insastronaute.webapi.user.service.DepartmentResyncService;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin Users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AdminUserService adminUserService;
    private final RealmRoleDirectory roleDirectory;
    private final DepartmentResyncService departmentResyncService;

    public AdminUserController(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            AdminUserService adminUserService,
            RealmRoleDirectory roleDirectory,
            DepartmentResyncService departmentResyncService
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.adminUserService = adminUserService;
        this.roleDirectory = roleDirectory;
        this.departmentResyncService = departmentResyncService;
    }

    @GetMapping
    @Operation(
            summary = "Search users",
            description = "Returns a paginated admin user search. All query parameters are optional. "
                    + "Deleted players whose record the registry retains are excluded unless deleted=true: "
                    + "they are not active players, and mixing them in made them easy to mistake for one."
    )
    public PageResponse<AdminUserDto> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Boolean hiddenFromLeaderboards,
            @RequestParam(required = false) Boolean staff,
            /** Les filieres par apprentissage, que rien d'autre ne distingue dans la liste. */
            @RequestParam(required = false) Boolean perf,
            @RequestParam(required = false) OnboardingStatus onboardingStatus,
            /*
             * Par defaut on ne montre pas les supprimes. Leur fiche est conservee parce que
             * le registre les nomme, pas parce qu'ils jouent encore : les melanger aux
             * vivants faisait chercher longtemps pourquoi un compte « existe » sans repondre.
             */
            @RequestParam(required = false, defaultValue = "false") Boolean deleted,
            @PageableDefault(size = 20)
            @SortDefault(sort = "displayName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        // Le tri staff/joueur se fait sur les identifiants Keycloak, que seul Keycloak
        // connait. On les passe au depot plutot que de filtrer apres coup : filtrer une page
        // deja constituee rendrait des pages de tailles inegales, et un compteur faux.
        Set<String> staffSubs = staff == null ? null : roleDirectory.staff();
        Page<AppUser> users = userRepository.searchAdminUsers(
                normalizeQuery(query),
                departmentId,
                gender,
                hiddenFromLeaderboards,
                staff,
                staffSubs,
                onboardingStatus,
                deleted,
                perf,
                pageable
        );
        Map<UUID, Department> departments = loadDepartments(users);
        Set<String> allStaff = roleDirectory.staff();
        Set<String> allLimited = roleDirectory.studentLimited();
        return PageResponse.from(users.map(user -> AdminUserDto.from(
                user,
                shortNameOf(departments, user.getDepartment()),
                allStaff.contains(user.getKeycloakSub()),
                allLimited.contains(user.getKeycloakSub())
        )));
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Get user moderation detail",
            description = "Returns one player with currencies, challenge submissions, friendships, card collections, and physical card instances."
    )
    public AdminUserDetailDto getUserDetail(@PathVariable UUID userId) {
        return adminUserService.getUserDetail(userId);
    }

    @GetMapping("/{userId}/available-challenges")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPOS_DEFIS', 'SOUS_RESPO_DEFIS')")
    @Operation(summary = "Get challenges available to a user", description = "Returns challenges currently visible and eligible for the selected player.")
    public java.util.List<PlayerChallengeDto> getAvailableChallenges(@PathVariable UUID userId) {
        return adminUserService.getAvailableChallenges(userId);
    }

    @PatchMapping("/{userId}/leaderboard-visibility")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Update leaderboard visibility", description = "Shows or hides a user from public leaderboard snapshots.")
    public void updateLeaderboardVisibility(
            @PathVariable UUID userId,
            @Valid @RequestBody LeaderboardVisibilityUpdateDto dto
    ) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        user.setHiddenFromLeaderboards(dto.hiddenFromLeaderboards());
        userRepository.save(user);
    }

    /**
     * Poser ou retirer la coche des filieres par apprentissage.
     *
     * <p>Elle ne deplace personne : le departement du joueur reste sa filiere parente,
     * et c'est ce qui le fait compter dans Place, dans les defis et dans le classement
     * de son departement. Elle change son audience d'evenements, qui devient PERF et
     * rien d'autre, et le sort des effectifs qui servent au calcul des coefficients.</p>
     *
     * <p>Administrative de bout en bout : l'annuaire ne distingue pas les apprentis, donc
     * rien ne repose cette coche a la connexion ni au balayage des departements.</p>
     */
    @PatchMapping("/{userId}/perf")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Tag a player as PERF", description = "Marks a player as following an apprenticeship track.")
    public void updatePerf(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminPerfUpdateDto dto
    ) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        user.setPerf(dto.perf());
        userRepository.save(user);
    }

    @PatchMapping("/{userId}/student-limited")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Toggle STUDENT_LIMITED role", description = "Switches a user between standard STUDENT and STUDENT_LIMITED.")
    public AdminUserDto updateStudentLimited(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminStudentLimitedUpdateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return adminUserService.updateStudentLimited(userId, dto.studentLimited(), oidcUser.getSubject());
    }

    /**
     * La seule voie de correction du genre une fois qu'un joueur l'a choisi.
     *
     * <p>Reservee aux administrateurs et non aux roles de staff, parce que ce champ
     * decide quel moderateur peut consulter les preuves sensibles d'un compte :
     * pouvoir le modifier, c'est pouvoir se designer soi-meme comme moderateur
     * legitime d'une cible.</p>
     *
     * <p>La correction pose l'horodatage si le compte n'en avait pas, pour qu'un
     * compte corrige ne reparte pas avec son choix gratuit intact.</p>
     */
    @PatchMapping("/{userId}/gender")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Correct a player's gender", description = "Sets the gender of a player whose own choice is already locked.")
    public void updateGender(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminGenderUpdateDto dto
    ) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        user.setGender(dto.gender());
        if (user.getOnboardingGenderChosenAt() == null) {
            user.setOnboardingGenderChosenAt(TimeTravelContext.instantNow());
        }
        userRepository.save(user);
    }

    /**
     * Crediter ou debiter des jetons.
     *
     * <p>Ouvert aux responsables cartes et evenements, en plus des administrateurs : ce sont
     * eux qui tiennent les stands ou les jetons se distribuent, et passer par un
     * administrateur pour chaque geste faisait du compte a privileges un goulet.</p>
     *
     * <p>Les quatre roles sont nommes un par un plutot que repris de {@code
     * EVENT_ADMIN_ROLES}, qui porte aussi les responsables depart et tournois. Il s'agit de
     * creer de la monnaie : la liste doit dire exactement qui, et non heriter d'un
     * regroupement fait pour autre chose.</p>
     */
    @PatchMapping("/{userId}/tokens")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPO_CARTES', 'SOUS_RESPO_CARTES', 'RESPOS_EVENT', 'SOUS_RESPO_EVENT')")
    @Operation(summary = "Update token balance", description = "Adds or removes tokens without allowing a negative balance. Open to card and event leads, who run the stands where tokens are handed out.")
    public AdminUserDto updateTokenBalance(
            @PathVariable UUID userId,
            @Valid @RequestBody TokenBalanceUpdateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return adminUserService.updateTokenBalance(userId, dto.tokensDelta(), oidcUser.getSubject());
    }

    @PostMapping("/{userId}/challenge-submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPOS_DEFIS', 'SOUS_RESPO_DEFIS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Create a validated manual challenge submission for a user")
    public void createManualChallengeSubmission(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminManualChallengeSubmissionRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        adminUserService.createManualChallengeSubmission(userId, request, oidcUser.getSubject());
    }

    @PostMapping("/{userId}/challenge-submissions/{submissionId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPOS_DEFIS', 'SOUS_RESPO_DEFIS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a previously validated challenge submission for a user")
    public void rejectChallengeSubmission(
            @PathVariable UUID userId,
            @PathVariable UUID submissionId,
            @Valid @RequestBody(required = false) AdminRejectChallengeSubmissionRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        adminUserService.rejectChallengeSubmission(userId, submissionId, request, oidcUser.getSubject());
    }

    /*
     * Plus etroit que le reste de la page : `/api/admin/users/**` est ouvert a tout le
     * staff, et relire un departement change ce qu'un joueur est aux yeux du classement et
     * des defis cibles. Ceux qui en repondent sont les respos defis et depart.
     */
    @PostMapping("/{userId}/department-resync")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPOS_DEFIS', 'SOUS_RESPO_DEFIS', 'RESPO_DEPART', 'SOUS_RESPO_DEPART')")
    @Operation(
            summary = "Re-read one player's department from Keycloak",
            description = "The department is normally written at login, from the token. This reads the "
                    + "player's Keycloak groups directly, without waiting for them to sign in again."
    )
    public DepartmentResyncService.Outcome resyncDepartment(@PathVariable UUID userId) {
        return departmentResyncService.resync(userId);
    }

    @PostMapping("/department-resync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Re-read every player's department from Keycloak",
            description = "One Keycloak call per account: slow, and reserved to administrators. "
                    + "A nightly sweep does the same on its own."
    )
    public DepartmentResyncService.SweepReport resyncAllDepartments() {
        return departmentResyncService.resyncAll();
    }

    @PostMapping("/{userId}/cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPO_CARTES', 'SOUS_RESPO_CARTES')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add cards to a user's catalogue")
    public void addCards(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserCardChangeRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        adminUserService.addCards(userId, request, oidcUser.getSubject());
    }

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "Delete a player",
            description = "Deletes the Keycloak account, detaches the player from other players' game state, and "
                    + "either removes the row outright or, when the admin registry still names them, keeps it "
                    + "marked as deleted. A missing Keycloak account never blocks the deletion."
    )
    public AdminPlayerDeletionDto deletePlayer(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID userId
    ) {
        return adminUserService.deletePlayer(oidcUser.getSubject(), userId);
    }

    @DeleteMapping("/{userId}/cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPO_CARTES', 'SOUS_RESPO_CARTES')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove cards from a user's catalogue")
    public void removeCards(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserCardRemovalRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        adminUserService.removeCards(userId, request, oidcUser.getSubject());
    }

    private String normalizeQuery(String query) {
        return StringUtils.hasText(query) ? query.trim() : null;
    }

    /**
     * Le nom court d'un departement, ou null quand le joueur n'en a pas.
     *
     * <p>Le null est ecarte avant la recherche, et non apres : la table renvoyee par
     * {@link #loadDepartments} est {@code Map.of()} quand personne sur la page n'a de
     * departement, et une carte immuable leve une NullPointerException sur
     * {@code get(null)} au lieu de rendre null. Le defaut ne se voyait donc que sur une
     * page ou aucun joueur n'en avait -- en cherchant, par exemple, celui dont on venait
     * justement de le retirer.</p>
     */
    private static String shortNameOf(Map<UUID, Department> departments, UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        Department department = departments.get(departmentId);
        return department == null ? null : department.getShortName();
    }

    private Map<UUID, Department> loadDepartments(Page<AppUser> users) {
        Set<UUID> departmentIds = users.stream()
                .map(AppUser::getDepartment)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllById(departmentIds)
                .stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
    }
}
