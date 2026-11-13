package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionOnboardingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SiteAvailabilityService siteAvailabilityService;

    @MockitoBean
    private UserSynchronizationService userSynchronizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void anAnonymousVisitorGetsAnExplicitNullOnboarding() throws Exception {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(true, null));

        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.onboarding").value(nullValue()));
    }

    @Test
    void anAuthenticatedPlayerLearnsTheirOnboardingState() throws Exception {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(true, null));

        AppUser appUser = new AppUser();
        appUser.setKeycloakSub("player-sub");
        appUser.setEmail("player@insastronaute.fr");
        appUser.setDisplayName("Player");
        appUser.setOnboardingStatus(OnboardingStatus.PENDING);
        when(userSynchronizationService.syncWithKeycloak(any())).thenReturn(appUser);

        mockMvc.perform(get("/api/session").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.onboarding.status").value("PENDING"));
    }
}
