package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardEnrollmentService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSynchronizationServiceTest {

    /**
     * Un joueur supprime ne rentre plus, meme avec un jeton encore valable.
     *
     * <p>Son compte Keycloak a ete efface, mais un jeton deja emis reste bon jusqu'a son
     * expiration. Sans cette garde il continuerait de jouer pendant ce temps. La poser ici
     * ferme tous les endpoints d'un coup, plutot qu'un predicat a ajouter dans chacun.</p>
     */
    @Test
    void aDeletedPlayerIsTurnedAwayEvenWithAStillValidToken() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        AppUser deleted = new AppUser();
        deleted.setId(UUID.randomUUID());
        deleted.setKeycloakSub("keycloak-sub");
        deleted.setDeletedAt(java.time.Instant.parse("2026-09-01T10:00:00Z"));

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.of(deleted));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository, departmentRepository, leaderboardEnrollmentService
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.syncWithKeycloak(oidcUser))
                .isInstanceOf(fr.insastronaute.webapi.common.web.ForbiddenException.class);

        // Et surtout : sa ligne n'est pas rafraichie au passage. Sans le refus, la
        // synchronisation ecraserait son nom avec celui du jeton.
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void syncWithKeycloakPrefersMultivaluedDepartmentClaimOverStaleGroups() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);
        Department department = new Department();
        UUID departmentId = UUID.randomUUID();
        department.setId(departmentId);
        department.setKeycloakGroupName("MECA");

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getPreferredUsername()).thenReturn("user");
        when(oidcUser.hasClaim("department")).thenReturn(true);
        when(oidcUser.getClaimAsStringList("department")).thenReturn(List.of("unknown", " meca "));
        when(oidcUser.getClaimAsStringList("groups")).thenReturn(List.of("/CFI"));
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.empty());
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("meca")).thenReturn(Optional.of(department));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository, departmentRepository, leaderboardEnrollmentService
        );

        AppUser user = service.syncWithKeycloak(oidcUser);

        assertThat(user.getDepartment()).isEqualTo(departmentId);
        verify(departmentRepository, never()).findByKeycloakGroupNameIgnoreCase("CFI");
    }

    @Test
    void syncWithKeycloakDoesNotFallbackToGroupsForPresentEmptyDepartmentClaim() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getPreferredUsername()).thenReturn("user");
        when(oidcUser.hasClaim("department")).thenReturn(true);
        when(oidcUser.getClaimAsStringList("department")).thenReturn(List.of());
        when(oidcUser.getClaimAsStringList("groups")).thenReturn(List.of("/ITI"));
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository, departmentRepository, leaderboardEnrollmentService
        );

        AppUser user = service.syncWithKeycloak(oidcUser);

        assertThat(user.getDepartment()).isNull();
        verify(departmentRepository, never()).findByKeycloakGroupNameIgnoreCase("ITI");
    }

    /**
     * Un nom arrive de l'inscription tel qu'il a ete tape, espaces comprises.
     *
     * <p>Une espace au bout ne se voit sur aucun ecran : ni sur le leaderboard, ni sur la
     * fiche joueur. Elle se compare pourtant, et elle avait rendu un joueur impossible a
     * supprimer depuis le panel, ou l'admin doit retaper le nom exact pour confirmer.
     * On la retire ici, la ou le nom entre, plutot que partout ou il ressort.</p>
     */
    @Test
    void aNameKeepsNoSurroundingSpaceFromKeycloak() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("arthur@example.com");
        when(oidcUser.getFullName()).thenReturn("Arthur PLasse ");
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository, departmentRepository, leaderboardEnrollmentService
        );

        AppUser user = service.syncWithKeycloak(oidcUser);

        assertThat(user.getDisplayName()).isEqualTo("Arthur PLasse");
    }

    @Test
    void syncWithKeycloakAssociatesDepartmentIgnoringCase() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        UUID departmentId = UUID.randomUUID();
        Department department = new Department();
        department.setId(departmentId);
        department.setKeycloakGroupName("ITI");

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getPreferredUsername()).thenReturn("user");
        when(oidcUser.getClaimAsStringList("groups")).thenReturn(List.of("/iti"));
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.empty());
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("iti")).thenReturn(Optional.of(department));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository,
                departmentRepository,
                leaderboardEnrollmentService
        );

        AppUser user = service.syncWithKeycloak(oidcUser);

        assertThat(user.getDepartment()).isEqualTo(departmentId);
        verify(leaderboardEnrollmentService).addNewUserToLatestUserSnapshots(user);
    }

    @Test
    void syncWithKeycloakHidesUsersWithApplicationRoleOtherThanStudent() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("admin@example.com");
        when(oidcUser.getPreferredUsername()).thenReturn("admin");
        doReturn(List.of(
                new SimpleGrantedAuthority("ROLE_STUDENT"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        )).when(oidcUser).getAuthorities();
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository,
                departmentRepository,
                leaderboardEnrollmentService
        );

        AppUser user = service.syncWithKeycloak(oidcUser);

        assertThat(user.getHiddenFromLeaderboards()).isTrue();
    }

    /** STUDENT_LIMITED est volontairement un role autre que STUDENT pour ce masquage. */
    @Test
    void syncWithKeycloakHidesANewLimitedStudent() {
        AppUser user = synchronizeNewUserWithRoles("ROLE_STUDENT_LIMITED");

        assertThat(user.getHiddenFromLeaderboards()).isTrue();
    }

    @Test
    void syncWithKeycloakKeepsLimitedPrecedenceWhenStudentRoleIsAlsoPresent() {
        AppUser user = synchronizeNewUserWithRoles("ROLE_STUDENT", "ROLE_STUDENT_LIMITED");

        assertThat(user.getHiddenFromLeaderboards()).isTrue();
    }

    @Test
    void syncWithKeycloakClearsDepartmentWhenNoGroupMatches() {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);
        AppUser existingUser = new AppUser();
        existingUser.setKeycloakSub("keycloak-sub");
        existingUser.setDepartment(UUID.randomUUID());

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getPreferredUsername()).thenReturn("user");
        when(oidcUser.getClaimAsStringList("groups")).thenReturn(List.of("/unknown"));
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.of(existingUser));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("unknown")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository,
                departmentRepository,
                leaderboardEnrollmentService
        );

        AppUser user = service.syncWithKeycloak(oidcUser);

        assertThat(user.getDepartment()).isNull();
    }

    /**
     * Le voyage dans le temps ne doit rien perdre de l'utilisateur.
     *
     * <p>Il rendait auparavant un objet reconstruit champ par champ, et la liste de copie
     * oubliait {@code passkeyPromptAnsweredAt} et {@code passkeyPromptSnoozedUntil} : la
     * suggestion de passkey revenait a chaque rafraichissement malgre un refus enregistre,
     * puisqu'elle etait relue sur un objet ou le refus n'avait pas ete recopie. Le 204 du
     * refus etait pourtant sincere -- l'ecriture avait bien eu lieu.</p>
     *
     * <p>La comparaison se fait contre une empreinte prise avant l'appel, et non contre
     * l'objet d'origine : depuis la correction, l'utilisateur rendu <em>est</em> celui
     * qu'on a passe, et se comparer a soi-meme ne mesurerait rien. L'empreinte, elle,
     * detecte aussi bien un retour a la copie champ par champ.</p>
     *
     * <p>Les champs sont parcourus par reflexion plutot que nommes : nommer les deux
     * d'aujourd'hui laisserait passer le troisieme qu'on ajoutera.</p>
     */
    @Test
    void syncWithKeycloakKeepsEveryFieldWhenADepartmentIsForced() throws Exception {
        UUID forced = UUID.randomUUID();
        Instant refused = Instant.parse("2026-09-01T10:00:00Z");
        AppUser stored = new AppUser();
        stored.setId(UUID.randomUUID());
        stored.setKeycloakSub("sub");
        stored.setEmail("joueur@insa-rouen.fr");
        stored.setDisplayName("Joueur");
        stored.setDepartment(UUID.randomUUID());
        stored.setPasskeyPromptAnsweredAt(refused);
        stored.setPasskeyPromptSnoozedUntil(refused.plusSeconds(86_400));
        stored.setOnboardingStatus(OnboardingStatus.VALIDATED);
        stored.setChallengePoints(42L);
        stored.setTokens(7L);

        Map<String, Object> before = snapshot(stored);
        AppUser seen = syncWithForcedDepartment(stored, forced);

        assertThat(seen.getDepartment()).isEqualTo(forced);
        // Le refus enregistre doit se relire : c'est le bug rapporte, en une ligne.
        assertThat(seen.isPasskeySuggestionDue(refused.plusSeconds(200_000))).isFalse();

        Map<String, Object> after = snapshot(seen);
        for (Map.Entry<String, Object> entry : before.entrySet()) {
            // `departmentId` est reecrit par la synchronisation depuis les revendications,
            // et `overriddenDepartmentId` est la superposition elle-meme.
            if (entry.getKey().equals("departmentId") || entry.getKey().equals("overriddenDepartmentId")) {
                continue;
            }
            assertThat(after.get(entry.getKey()))
                    .as("le champ %s doit survivre au voyage dans le temps", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /**
     * Le departement force ne doit jamais atteindre la base.
     *
     * <p>Une quarantaine d'appelants recoivent cet utilisateur et plusieurs le
     * reenregistrent -- l'accueil, la mise a jour du profil. Si la superposition
     * s'ecrivait, une session de voyage deplacerait durablement quelqu'un de
     * departement.</p>
     */
    @Test
    void aForcedDepartmentNeverReachesThePersistedColumn() {
        UUID forced = UUID.randomUUID();
        AppUser stored = new AppUser();
        stored.setKeycloakSub("sub");

        AppUser seen = syncWithForcedDepartment(stored, forced);

        assertThat(seen.getDepartment()).isEqualTo(forced);
        assertThat(seen.getPersistedDepartment()).isNotEqualTo(forced);
    }

    /** Les champs d'instance et leurs valeurs, pour comparer un avant et un apres. */
    private static Map<String, Object> snapshot(AppUser user) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : AppUser.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            values.put(field.getName(), field.get(user));
        }
        return values;
    }

    /** Le meme appel que les autres tests, avec un departement force dans le contexte. */
    private AppUser syncWithForcedDepartment(AppUser stored, UUID forced) {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(oidcUser.getSubject()).thenReturn("sub");
        when(oidcUser.getEmail()).thenReturn(stored.getEmail());
        when(oidcUser.getFullName()).thenReturn(stored.getDisplayName());
        doReturn(List.of()).when(oidcUser).getAuthorities();
        when(oidcUser.hasClaim("department")).thenReturn(true);
        when(oidcUser.getClaimAsStringList("department")).thenReturn(List.of());
        when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(stored));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository, departmentRepository, leaderboardEnrollmentService);
        TimeTravelContext.setForcedDepartmentId(forced);
        try {
            return service.syncWithKeycloak(oidcUser);
        } finally {
            TimeTravelContext.clear();
        }
    }

    private AppUser synchronizeNewUserWithRoles(String... roles) {
        UserRepository userRepository = mock(UserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LeaderboardEnrollmentService leaderboardEnrollmentService = mock(LeaderboardEnrollmentService.class);
        OidcUser oidcUser = mock(OidcUser.class);

        when(oidcUser.getSubject()).thenReturn("keycloak-sub");
        when(oidcUser.getEmail()).thenReturn("limited@example.com");
        when(oidcUser.getPreferredUsername()).thenReturn("limited");
        doReturn(java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList()).when(oidcUser).getAuthorities();
        when(userRepository.findByKeycloakSub("keycloak-sub")).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSynchronizationService service = new UserSynchronizationService(
                userRepository,
                departmentRepository,
                leaderboardEnrollmentService
        );
        return service.syncWithKeycloak(oidcUser);
    }
}
