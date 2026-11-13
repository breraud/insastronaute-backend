package fr.insastronaute.webapi.qrlink.controller;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.qrlink.dto.QrScanResultDto;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigDto;
import fr.insastronaute.webapi.qrlink.service.QrLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QrLinkService qrLinkService;

    @MockitoBean
    private UserSynchronizationService userSynchronizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void inactivePermanentQrRouteRedirectsWithoutAuthentication() throws Exception {
        when(qrLinkService.getConfig()).thenReturn(new QrLinkConfigDto(
                "https://api.example.com/q", "https://example.org/programme", false));
        when(qrLinkService.resolveQrDestination()).thenReturn("https://example.org/programme");

        mockMvc.perform(get("/q"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org/programme"));
    }

    @Test
    void activePermanentQrRouteStartsLoginBeforeLoadingLandingForAnonymousVisitor() throws Exception {
        when(qrLinkService.getConfig()).thenReturn(new QrLinkConfigDto(
                "https://api.example.com/q", "https://example.org/programme", true));
        when(qrLinkService.resolveQrDestination()).thenReturn("https://app.example.com/scan");

        mockMvc.perform(get("/q"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "/oauth2/authorization/keycloak?redirect_uri=https%3A%2F%2Fapp.example.com%2Fscan"
                ));
    }

    @Test
    void activePermanentQrRouteLoadsLandingDirectlyForAuthenticatedVisitor() throws Exception {
        when(qrLinkService.getConfig()).thenReturn(new QrLinkConfigDto(
                "https://api.example.com/q", "https://example.org/programme", true));
        when(qrLinkService.resolveQrDestination()).thenReturn("https://app.example.com/scan");

        mockMvc.perform(get("/q").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://app.example.com/scan"));
    }

    @Test
    void authenticatedScanReturnsBusinessResult() throws Exception {
        when(qrLinkService.scan("player-sub"))
                .thenReturn(new QrScanResultDto("SCANNED", "Code scanné avec succès."));

        mockMvc.perform(post("/api/scan")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCANNED"))
                .andExpect(jsonPath("$.message").value("Code scanné avec succès."));

        verify(qrLinkService).scan("player-sub");
        verify(qrLinkService).ensureScanAvailable();
        verify(userSynchronizationService).syncWithKeycloak(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactiveScanReturnsNotFound() throws Exception {
        doThrow(new NotFoundException("Page de scan introuvable."))
                .when(qrLinkService).ensureScanAvailable();

        mockMvc.perform(post("/api/scan")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Page de scan introuvable."));
        verify(userSynchronizationService, never()).syncWithKeycloak(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anonymousScanRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/scan").with(csrf().asHeader()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousInactiveScanIsNotFoundWithoutSynchronizingUser() throws Exception {
        doThrow(new NotFoundException("Page de scan introuvable."))
                .when(qrLinkService).ensureScanAvailable();

        mockMvc.perform(post("/api/scan").with(csrf().asHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Page de scan introuvable."));

        verify(userSynchronizationService, never()).syncWithKeycloak(org.mockito.ArgumentMatchers.any());
    }
}
