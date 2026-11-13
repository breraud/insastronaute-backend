package fr.insastronaute.webapi.onboarding.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import fr.insastronaute.webapi.onboarding.service.AdminOnboardingService;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOnboardingControllerTest {

    private static final String PATH = "/api/admin/onboarding";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOnboardingService service;

    @MockitoBean
    private UserSynchronizationService synchronizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private static AppUser decider() {
        AppUser respo = new AppUser();
        respo.setId(UUID.randomUUID());
        respo.setDisplayName("Respo Defis");
        respo.setEmail("respo@example.test");
        return respo;
    }

    @Test
    void everyChallengeAdminRoleCanSeeTheQueue() throws Exception {
        // La boucle ci-dessous ne verifie que le mecanisme : elle iterait tout aussi
        // bien sur un tableau ampute. Sans cette assertion sur la composition exacte,
        // un role retire par erreur du tableau perdrait silencieusement son acces sans
        // faire echouer aucun test de ce fichier.
        assertThat(SecurityConfig.CHALLENGE_ADMIN_ROLES).containsExactlyInAnyOrder(
                "ADMIN",
                "RESPOS_DEFIS",
                "SOUS_RESPO_DEFIS"
        );

        for (String role : SecurityConfig.CHALLENGE_ADMIN_ROLES) {
            mockMvc.perform(get(PATH + "/pending").with(user("staff").roles(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anUnrelatedStaffRoleIsRefused() throws Exception {
        mockMvc.perform(get(PATH + "/pending").with(user("cards").roles("SOUS_RESPO_CARTES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyChallengeAdminRoleCanSeeTheRejectedList() throws Exception {
        // Meme groupe de roles que la file en attente : le recours de la spec 2.4
        // n'a pas de sens si un sous-respo defis peut valider mais pas retrouver
        // le compte refuse qui vient de se presenter a lui.
        for (String role : SecurityConfig.CHALLENGE_ADMIN_ROLES) {
            mockMvc.perform(get(PATH + "/rejected").with(user("staff").roles(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anUnrelatedStaffRoleIsRefusedOnTheRejectedList() throws Exception {
        mockMvc.perform(get(PATH + "/rejected").with(user("cards").roles("SOUS_RESPO_CARTES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSousRespoDefisCannotReachTheBackfillButCanSeeThePendingQueue() throws Exception {
        mockMvc.perform(post(PATH + "/backfill").with(user("defis").roles("SOUS_RESPO_DEFIS")).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(PATH + "/pending").with(user("defis").roles("SOUS_RESPO_DEFIS")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminReachesTheBackfill() throws Exception {
        when(service.backfill()).thenReturn(0);

        mockMvc.perform(post(PATH + "/backfill").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        verify(service).backfill();
    }

    @Test
    void validatingReachesTheServiceWithTheResolvedDecider() throws Exception {
        AppUser decider = decider();
        UUID userId = UUID.randomUUID();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(decider);

        mockMvc.perform(post(PATH + "/" + userId + "/validate")
                        .with(oidcLogin().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SOUS_RESPO_DEFIS")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).validate(userId, decider);
    }

    @Test
    void rejectingReachesTheServiceWithTheReason() throws Exception {
        AppUser decider = decider();
        UUID userId = UUID.randomUUID();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(decider);

        mockMvc.perform(post(PATH + "/" + userId + "/reject")
                        .with(oidcLogin().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SOUS_RESPO_DEFIS")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Compte suspect\"}"))
                .andExpect(status().isNoContent());

        verify(service).reject(userId, decider, "Compte suspect");
    }

    @Test
    void onlyAdminAndResposDefisCanCancelAValidatedOnboarding() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser decider = decider();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(decider);

        for (String role : new String[]{"ADMIN", "RESPOS_DEFIS"}) {
            mockMvc.perform(post(PATH + "/" + userId + "/cancel")
                            .with(oidcLogin().authorities(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Validation accordee par erreur\"}"))
                    .andExpect(status().isNoContent());
        }

        verify(service, times(2)).cancel(userId, decider, "Validation accordee par erreur");

        mockMvc.perform(post(PATH + "/" + userId + "/cancel")
                        .with(user("sous-respo").roles("SOUS_RESPO_DEFIS"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Validation accordee par erreur\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanValidateLimited() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser decider = decider();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(decider);

        mockMvc.perform(post(PATH + "/" + userId + "/validate-limited")
                        .with(oidcLogin().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).validateLimited(userId, decider);
    }

    @Test
    void sousRespoDefisCannotValidateLimited() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post(PATH + "/" + userId + "/validate-limited")
                        .with(user("defis").roles("SOUS_RESPO_DEFIS"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
