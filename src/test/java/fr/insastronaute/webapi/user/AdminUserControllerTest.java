package fr.insastronaute.webapi.user;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.dto.AdminUserDetailDto;
import fr.insastronaute.webapi.user.dto.AdminUserDto;
import fr.insastronaute.webapi.user.dto.AdminUserStatsDto;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import fr.insastronaute.webapi.user.dto.AdminManualChallengeSubmissionRequest;
import fr.insastronaute.webapi.user.dto.AdminRejectChallengeSubmissionRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardChangeRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardRemovalRequest;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private fr.insastronaute.webapi.user.service.RealmRoleDirectory roleDirectory;

    @MockitoBean
    private fr.insastronaute.webapi.user.service.DepartmentResyncService departmentResyncService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void resetMocks() {
        reset(userRepository, departmentRepository, adminUserService);
    }

    /**
     * Une page ou personne n'a de departement s'affiche quand meme.
     *
     * <p>La table des departements se construit a partir de ceux qu'on trouve sur la page.
     * Vide, elle etait rendue en {@code Map.of()} -- une carte immuable, qui leve une
     * NullPointerException sur {@code get(null)} au lieu de rendre {@code null}. Des qu'un
     * seul joueur portait un departement, la carte devenait une HashMap et le defaut
     * disparaissait : d'ou une page qui marche partout, sauf quand on cherche precisement
     * le joueur dont on vient de retirer le departement.</p>
     */
    @Test
    void aPageWhereNobodyHasADepartmentStillRenders() throws Exception {
        AppUser orphan = new AppUser();
        orphan.setId(UUID.randomUUID());
        orphan.setKeycloakSub("keycloak-sub");
        orphan.setEmail("sans@example.com");
        orphan.setDisplayName("Sans département");
        orphan.setDepartment((UUID) null);

        when(userRepository.searchAdminUsers(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(orphan)));

        mockMvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].department").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").value("sans@example.com"));
    }

    @Test
    void adminCanSearchUsersWithOptionalFilters() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AppUser appUser = new AppUser();
        appUser.setId(userId);
        appUser.setKeycloakSub("keycloak-sub");
        appUser.setEmail("alice@example.com");
        appUser.setDisplayName("Alice");
        appUser.setGender(Gender.FEMALE);
        appUser.setDepartment(departmentId);
        appUser.setHiddenFromLeaderboards(true);

        Department department = new Department();
        department.setId(departmentId);
        department.setShortName("INFO");

        when(userRepository.searchAdminUsers(eq("alice"), eq(departmentId), eq(Gender.FEMALE), eq(true), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appUser)));
        when(departmentRepository.findAllById(any())).thenReturn(List.of(department));

        mockMvc.perform(get("/api/admin/users")
                        .param("query", " alice ")
                        .param("departmentId", departmentId.toString())
                        .param("gender", "FEMALE")
                        .param("hiddenFromLeaderboards", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].department").value("INFO"))
                .andExpect(jsonPath("$.content[0].hiddenFromLeaderboards").value(true));

        verify(userRepository).searchAdminUsers(eq("alice"), eq(departmentId), eq(Gender.FEMALE), eq(true), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    /** La liste dit qui est apprenti : sans cela, la coche ne se verifie qu'une fiche a la fois. */
    @Test
    void theUserListSaysWhoIsAnApprentice() throws Exception {
        AppUser apprentice = new AppUser();
        apprentice.setId(UUID.randomUUID());
        apprentice.setKeycloakSub("keycloak-sub");
        apprentice.setEmail("apprentie@example.com");
        apprentice.setDisplayName("Apprentie");
        apprentice.setPerf(true);

        when(userRepository.searchAdminUsers(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(apprentice)));

        mockMvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].perf").value(true));
    }

    @Test
    void adminCanListUsersWithoutFilters() throws Exception {
        when(userRepository.searchAdminUsers(eq(null), eq(null), eq(null), eq(null), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userRepository).searchAdminUsers(eq(null), eq(null), eq(null), eq(null), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void adminCanGetUserDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AdminUserDto adminUser = new AdminUserDto(
                userId,
                "keycloak-sub",
                "alice@example.com",
                "Alice",
                Gender.FEMALE,
                departmentId,
                "INFO",
                12L,
                3L,
                2L,
                42L,
                7L,
                false,
                null,
                null,
                null,
                null,
                null
        );
        AdminUserDetailDto detail = new AdminUserDetailDto(
                adminUser,
                new AdminUserStatsDto(4, 1, 0, 2, 1, 3, 2, 1, 5, 2, 2),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        when(adminUserService.getUserDetail(userId)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/users/{userId}", userId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.tokens").value(12))
                .andExpect(jsonPath("$.user.challengePoints").value(42))
                .andExpect(jsonPath("$.stats.submissionCount").value(4))
                .andExpect(jsonPath("$.stats.virtualCardCount").value(5))
                .andExpect(jsonPath("$.submissions.length()").value(0))
                .andExpect(jsonPath("$.friendships.length()").value(0))
                .andExpect(jsonPath("$.cardCollections.length()").value(0))
                .andExpect(jsonPath("$.physicalCardInstances.length()").value(0))
                .andExpect(jsonPath("$.cardTeam").value(org.hamcrest.Matchers.nullValue()));

        verify(adminUserService).getUserDetail(userId);
    }

    @Test
    void userSearchAndDetailIncludeStudentLimitedField() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDto adminUser = new AdminUserDto(
                userId,
                "keycloak-sub",
                "alice@example.com",
                "Alice",
                Gender.FEMALE,
                UUID.randomUUID(),
                "INFO",
                12L,
                3L,
                2L,
                42L,
                7L,
                false,
                false,
                true,
                false,
                OnboardingStatus.VALIDATED,
                null,
                null,
                null
        );
        AdminUserDetailDto detail = new AdminUserDetailDto(
                adminUser,
                new AdminUserStatsDto(4, 1, 0, 2, 1, 3, 2, 1, 5, 2, 2),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
        when(adminUserService.getUserDetail(userId)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/users/{userId}", userId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.studentLimited").value(true));
    }

    @Test
    void adminCanGetChallengesAvailableToAUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(adminUserService.getAvailableChallenges(userId)).thenReturn(List.of(new PlayerChallengeDto(
                challengeId,
                "Photo d'équipe",
                "Prenez une photo",
                10,
                "Photos",
                java.util.List.of("Photos"),
                "ADMIN",
                "MEDIA",
                false, false,
                1,
                1,
                0,
                false,
                true,
                false,
                false,
                null,
                null,
                null
        )));

        mockMvc.perform(get("/api/admin/users/{userId}/available-challenges", userId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(challengeId.toString()))
                .andExpect(jsonPath("$[0].canStillComplete").value(true));

        verify(adminUserService).getAvailableChallenges(userId);
    }

    @Test
    void adminCanHideUserFromLeaderboards() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser appUser = new AppUser();

        appUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(appUser));

        mockMvc.perform(patch("/api/admin/users/{userId}/leaderboard-visibility", userId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hiddenFromLeaderboards": true
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(appUser.getHiddenFromLeaderboards()).isTrue();
        verify(userRepository).save(appUser);
    }

    /**
     * La coche PERF est administrative : rien dans l'annuaire ne la pose.
     *
     * <p>Elle ne deplace pas le joueur -- son departement reste sa filiere -- mais elle
     * change son audience d'evenements et le sort des effectifs de classement.</p>
     */
    @Test
    void adminCanTagAPlayerAsApprentice() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser appUser = new AppUser();
        UUID department = UUID.randomUUID();

        appUser.setId(userId);
        appUser.setDepartment(department);
        when(userRepository.findById(userId)).thenReturn(Optional.of(appUser));

        mockMvc.perform(patch("/api/admin/users/{userId}/perf", userId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "perf": true
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(appUser.isPerf()).isTrue();
        assertThat(appUser.getDepartment()).isEqualTo(department);
        verify(userRepository).save(appUser);
    }

    @Test
    void adminCanToggleUserToStudentLimited() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDto updated = new AdminUserDto(
                userId, "keycloak-sub", "alice@example.com", "Alice", Gender.FEMALE,
                null, null, 17L, 0L, 0L, 0L, 0L, true, false, true, false, OnboardingStatus.VALIDATED, null, null, null
        );
        when(adminUserService.updateStudentLimited(eq(userId), eq(true), eq("admin-sub"))).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/users/{userId}/student-limited", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentLimited": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentLimited").value(true));

        verify(adminUserService).updateStudentLimited(userId, true, "admin-sub");
    }

    @Test
    void nonAdminCannotToggleStudentLimited() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(patch("/api/admin/users/{userId}/student-limited", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("respo-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_DEFIS")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentLimited": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAddTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDto updated = new AdminUserDto(
                userId, "keycloak-sub", "alice@example.com", "Alice", Gender.FEMALE,
                null, null, 17L, 0L, 0L, 0L, 0L, false, null, null, null, null, null
        );
        when(adminUserService.updateTokenBalance(userId, 5L, "admin-sub")).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens").value(17));

        verify(adminUserService).updateTokenBalance(userId, 5L, "admin-sub");
    }

    /**
     * Les responsables cartes et evenements creditent aussi.
     *
     * <p>Ce sont eux qui tiennent les stands ou les jetons se distribuent. Passer par un
     * administrateur pour chaque geste faisait du compte a privileges un goulet, un soir
     * d'affluence.</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_RESPO_CARTES", "ROLE_SOUS_RESPO_CARTES",
                            "ROLE_RESPOS_EVENT", "ROLE_SOUS_RESPO_EVENT"})
    void cardAndEventLeadsCanCreditTokens(String authority) throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDto updated = new AdminUserDto(
                userId, "keycloak-sub", "alice@example.com", "Alice", Gender.FEMALE,
                null, null, 17L, 0L, 0L, 0L, 0L, false, null, null, null, null, null
        );
        when(adminUserService.updateTokenBalance(userId, 5L, "respo-sub")).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("respo-sub"))
                                .authorities(new SimpleGrantedAuthority(authority)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": 5
                                }
                                """))
                .andExpect(status().isOk());
    }

    /**
     * Le reste du staff, non. Un responsable tournois ou depart atteint le reste de
     * /api/admin/users, mais creer de la monnaie n'est pas son geste -- et la liste des
     * quatre roles doit se lire ici, pas se deviner.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_RESPO_TOURNOIS", "ROLE_RESPO_DEPART", "ROLE_MODERATOR"})
    void theRestOfTheStaffCannotCreditTokens(String authority) throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", UUID.randomUUID())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority(authority)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": 5
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * Relire un departement : les respos defis et depart, et eux seuls.
     *
     * <p>La page est ouverte a tout le staff ; ce geste ne l'est pas. Il change ce qu'un
     * joueur est aux yeux du classement et des defis cibles, et ceux qui en repondent sont
     * ces quatre-la. La liste doit se lire ici, pas se deviner.</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_RESPOS_DEFIS", "ROLE_SOUS_RESPO_DEFIS", "ROLE_RESPO_DEPART", "ROLE_SOUS_RESPO_DEPART"})
    void thoseWhoAnswerForDepartmentsCanResyncOne(String authority) throws Exception {
        UUID userId = UUID.randomUUID();
        when(departmentResyncService.resync(userId))
                .thenReturn(new fr.insastronaute.webapi.user.service.DepartmentResyncService.Outcome(userId, "GM", "ITI", true));

        mockMvc.perform(post("/api/admin/users/{userId}/department-resync", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_RESPO_GOODIES", "ROLE_RESPO_TOURNOIS", "ROLE_MODERATOR"})
    void theRestOfTheStaffCannotResyncADepartment(String authority) throws Exception {
        mockMvc.perform(post("/api/admin/users/{userId}/department-resync", UUID.randomUUID())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** Le balayage complet reste aux administrateurs : un appel Keycloak par compte. */
    @ParameterizedTest
    @ValueSource(strings = {"ROLE_RESPOS_DEFIS", "ROLE_RESPO_DEPART"})
    void onlyAdministratorsSweepEveryDepartment(String authority) throws Exception {
        mockMvc.perform(post("/api/admin/users/department-resync")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRemoveTokensWithoutMakingTheBalanceNegative() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDto updated = new AdminUserDto(
                userId, "keycloak-sub", "alice@example.com", "Alice", Gender.FEMALE,
                null, null, 7L, 0L, 0L, 0L, 0L, false, null, null, null, null, null
        );
        when(adminUserService.updateTokenBalance(userId, -5L, "admin-sub")).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": -5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens").value(7));

        verify(adminUserService).updateTokenBalance(userId, -5L, "admin-sub");
    }

    @Test
    void adminCannotRemoveMoreTokensThanTheAvailableBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminUserService.updateTokenBalance(userId, -5L, "admin-sub"))
                .thenThrow(new fr.insastronaute.webapi.common.web.BadRequestException(
                        "Le solde de tokens ne peut pas devenir négatif.",
                        "tokensDelta", "Le retrait dépasse le solde disponible."));

        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": -5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le solde de tokens ne peut pas devenir négatif."));
    }

    @Test
    void adminCannotSubmitANullTokenVariation() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", userId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La variation de tokens est requise."));
    }

    @Test
    void adminCannotSubmitAZeroTokenVariation() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminUserService.updateTokenBalance(userId, 0L, "admin-sub"))
                .thenThrow(new fr.insastronaute.webapi.common.web.BadRequestException(
                        "La variation de tokens ne peut pas être nulle.",
                        "tokensDelta", "La variation doit être différente de zéro."));

        mockMvc.perform(patch("/api/admin/users/{userId}/tokens", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tokensDelta": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanCreateAnApprovedManualChallengeSubmission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/{userId}/challenge-submissions", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeId": "%s",
                                  "comment": "Validation sur stand"
                                }
                                """.formatted(challengeId)))
                .andExpect(status().isNoContent());

        verify(adminUserService).createManualChallengeSubmission(
                eq(userId),
                eq(new AdminManualChallengeSubmissionRequest(challengeId, "Validation sur stand", null)),
                eq("admin-sub")
        );
    }

    @Test
    void adminCanAddVirtualCardsToAUserCatalogue() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/{userId}/cards", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": "%s",
                                  "collectionType": "VIRTUAL",
                                  "quantity": 2
                                }
                                """.formatted(cardId)))
                .andExpect(status().isNoContent());

        verify(adminUserService).addCards(
                eq(userId),
                eq(new AdminUserCardChangeRequest(cardId, fr.insastronaute.webapi.card.domain.CardCollectionType.VIRTUAL, 2, null)),
                eq("admin-sub")
        );
    }

    @Test
    void adminCanRemovePhysicalCardInstancesFromAUserCatalogue() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/users/{userId}/cards", userId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collectionType": "PHYSICAL",
                                  "cardInstanceIds": ["%s"]
                                }
                                """.formatted(instanceId)))
                .andExpect(status().isNoContent());

        verify(adminUserService).removeCards(
                eq(userId),
                eq(new AdminUserCardRemovalRequest(
                        fr.insastronaute.webapi.card.domain.CardCollectionType.PHYSICAL,
                        null,
                        null,
                        List.of(instanceId)
                )),
                eq("admin-sub")
        );
    }

    /**
     * La voie de rattrapage : le joueur ne peut plus corriger son genre lui-meme, il
     * faut donc que quelqu'un le puisse, sinon une erreur de saisie enferme le compte
     * sur un champ qui decide qui verra ses preuves sensibles.
     */
    @Test
    void anAdminCanCorrectAGenderThePlayerCanNoLongerChange() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setGender(Gender.MALE);
        user.setOnboardingGenderChosenAt(java.time.Instant.parse("2026-08-01T10:00:00Z"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/admin/users/{userId}/gender", userId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gender\": \"FEMALE\"}"))
                .andExpect(status().isNoContent());

        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        verify(userRepository).save(user);
    }

    /**
     * Modifier ce champ, c'est choisir qui peut consulter les preuves sensibles d'un
     * compte : les roles de staff ne suffisent pas, meme s'ils atteignent le reste de
     * /api/admin/users.
     */
    @Test
    void aStaffAccountThatIsNotAdminCannotCorrectAGender() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/gender", UUID.randomUUID())
                        .with(user("sous-respo").roles("SOUS_RESPO_DEFIS"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gender\": \"FEMALE\"}"))
                .andExpect(status().isForbidden());

        verify(userRepository, org.mockito.Mockito.never()).save(any(AppUser.class));
    }

    @Test
    void adminCanRejectAValidatedChallengeSubmission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/{userId}/challenge-submissions/{submissionId}/reject", userId, submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "Preuve non conforme au défi"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(adminUserService).rejectChallengeSubmission(
                eq(userId),
                eq(submissionId),
                eq(new AdminRejectChallengeSubmissionRequest("Preuve non conforme au défi")),
                eq("admin-sub")
        );
    }

    @Test
    void challengeLeadCanRejectAValidatedChallengeSubmission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/{userId}/challenge-submissions/{submissionId}/reject", userId, submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("respo-defis-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_DEFIS")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());

        verify(adminUserService).rejectChallengeSubmission(
                eq(userId),
                eq(submissionId),
                eq(new AdminRejectChallengeSubmissionRequest(null)),
                eq("respo-defis-sub")
        );
    }

    @Test
    void studentWithoutRoleCannotRejectChallengeSubmission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/{userId}/challenge-submissions/{submissionId}/reject", userId, submissionId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("student-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
