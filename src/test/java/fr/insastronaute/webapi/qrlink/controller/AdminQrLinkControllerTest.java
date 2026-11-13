package fr.insastronaute.webapi.qrlink.controller;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigDto;
import fr.insastronaute.webapi.qrlink.dto.QrLinkConfigUpdateDto;
import fr.insastronaute.webapi.qrlink.service.QrLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminQrLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QrLinkService qrLinkService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanReadConfiguration() throws Exception {
        when(qrLinkService.getConfig()).thenReturn(config());

        mockMvc.perform(get("/api/admin/qr-link")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicUrl").value("https://api.example.com/q"))
                .andExpect(jsonPath("$.redirectUrl").value("https://example.org"))
                .andExpect(jsonPath("$.challengeModeEnabled").value(true));
    }

    @Test
    void adminCanUpdateConfiguration() throws Exception {
        when(qrLinkService.updateConfig(any(QrLinkConfigUpdateDto.class))).thenReturn(config());

        mockMvc.perform(put("/api/admin/qr-link")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redirectUrl":"https://example.org","challengeModeEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeModeEnabled").value(true));
    }

    @Test
    void invalidUrlReturnsBadRequest() throws Exception {
        when(qrLinkService.updateConfig(any(QrLinkConfigUpdateDto.class)))
                .thenThrow(new BadRequestException("L'URL de redirection doit être une URL HTTPS absolue."));

        mockMvc.perform(put("/api/admin/qr-link")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redirectUrl":"http://example.org","challengeModeEnabled":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'URL de redirection doit être une URL HTTPS absolue."));
    }

    @Test
    void nonAdminCannotReadConfiguration() throws Exception {
        mockMvc.perform(get("/api/admin/qr-link").with(oidcLogin()))
                .andExpect(status().isForbidden());
    }

    private static QrLinkConfigDto config() {
        return new QrLinkConfigDto("https://api.example.com/q", "https://example.org", true);
    }
}
