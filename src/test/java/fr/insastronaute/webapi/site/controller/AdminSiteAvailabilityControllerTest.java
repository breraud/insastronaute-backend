package fr.insastronaute.webapi.site.controller;

import fr.insastronaute.webapi.site.dto.SiteAvailabilityDto;
import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
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

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSiteAvailabilityControllerTest {

    private static final String PATH = "/api/admin/site-availability";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SiteAvailabilityService siteAvailabilityService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void aSubRespoCannotReadTheSwitch() throws Exception {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(true, null));

        mockMvc.perform(get(PATH).with(user("staff").roles("SOUS_RESPO_CARTES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminReadsTheSwitch() throws Exception {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(true, null));
        when(siteAvailabilityService.get())
                .thenReturn(new SiteAvailabilityDto(false, "Retour a 18h.", Instant.parse("2026-08-27T10:00:00Z"), "Bob"));

        mockMvc.perform(get(PATH).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerAccessEnabled").value(false))
                .andExpect(jsonPath("$.message").value("Retour a 18h."))
                .andExpect(jsonPath("$.updatedBy").value("Bob"));
    }

    @Test
    void aSubRespoCannotFlipTheSwitch() throws Exception {
        when(siteAvailabilityService.currentStatus())
                .thenReturn(new SiteAvailabilityService.SiteStatus(true, null));

        mockMvc.perform(put(PATH).with(user("staff").roles("SOUS_RESPO_DEFIS")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerAccessEnabled\":false,\"message\":null}"))
                .andExpect(status().isForbidden());
    }
}
