package fr.insastronaute.webapi.site.web;

import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SiteAvailabilityFilterTest {

    private static final String PROBE = "/api/probe-site-availability";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SiteAvailabilityService siteAvailabilityService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private void siteOpen() {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(true, null));
    }

    private void siteClosed(String message) {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(false, message));
    }

    @Test
    void anOpenSiteLetsPlayersThrough() throws Exception {
        siteOpen();

        mockMvc.perform(get(PROBE).with(user("player").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aClosedSiteRejectsPlayersWithAStableCode() throws Exception {
        siteClosed("Retour a 18h.");

        mockMvc.perform(get(PROBE).with(user("player").roles("STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("SITE_DISABLED"))
                .andExpect(jsonPath("$.message").value("Retour a 18h."))
                .andExpect(jsonPath("$.path").value(PROBE));
    }

    @Test
    void aClosedSiteWithoutMessageFallsBackToTheDefaultText() throws Exception {
        siteClosed(null);

        mockMvc.perform(get(PROBE).with(user("player").roles("STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Le site est momentanement ferme."));
    }

    @Test
    void aClosedSiteStillLetsEveryStaffRoleThrough() throws Exception {
        siteClosed(null);

        assertThat(fr.insastronaute.webapi.common.security.config.SecurityConfig.SITE_BYPASS_ROLES)
                .containsExactlyInAnyOrder(
                        "ADMIN",
                        "MODERATOR",
                        "RESPOS_DEFIS",
                        "SOUS_RESPO_DEFIS",
                        "RESPOS_EVENT",
                        "SOUS_RESPO_EVENT",
                        "RESPO_CARTES",
                        "SOUS_RESPO_CARTES",
                        "RESPO_GOODIES",
                        "SOUS_RESPO_GOODIES",
                        "RESPO_DEPART",
                        "SOUS_RESPO_DEPART",
                        "RESPO_TOURNOIS",
                        "SOUS_RESPO_TOURNOIS",
                        "ACCES_SITE_ADMIN"
                );

        for (String role : fr.insastronaute.webapi.common.security.config.SecurityConfig.SITE_BYPASS_ROLES) {
            mockMvc.perform(get(PROBE).with(user("staff").roles(role)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void aClosedSiteStillServesTheBootstrapEndpoints() throws Exception {
        siteClosed(null);

        mockMvc.perform(get("/api/csrf")).andExpect(status().isOk());
        mockMvc.perform(get("/api/session")).andExpect(status().isOk());
    }
}
