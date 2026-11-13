package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.service.MediaLibraryGrantService;
import fr.insastronaute.webapi.medialibrary.service.RoleDirectoryUnavailableException;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMediaLibraryGrantControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MediaLibraryGrantService grantService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void anAdministratorDesignatesAndRevokes() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/admin/media-library/grants/{userId}", userId)
                        .with(csrf().asHeader())
                        .with(oidcLogin().idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/media-library/grants/{userId}", userId)
                        .with(csrf().asHeader())
                        .with(oidcLogin().idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        verify(grantService).grant(userId, "admin-sub");
        verify(grantService).revoke(userId, "admin-sub");
    }

    @Test
    void aChallengeLeadCannotDesignateAnyone() throws Exception {
        mockMvc.perform(put("/api/admin/media-library/grants/{userId}", UUID.randomUUID())
                        .with(csrf().asHeader())
                        .with(oidcLogin().idToken(token -> token.subject("lead-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_DEFIS"))))
                .andExpect(status().isForbidden());

        verify(grantService, never()).grant(any(), any());
    }

    @Test
    void anUnreachableRoleDirectoryAnswersServiceUnavailable() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new RoleDirectoryUnavailableException()).when(grantService).grant(userId, "admin-sub");

        mockMvc.perform(put("/api/admin/media-library/grants/{userId}", userId)
                        .with(csrf().asHeader())
                        .with(oidcLogin().idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("L'annuaire des rôles est temporairement indisponible."));
    }
}
