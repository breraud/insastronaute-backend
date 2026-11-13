package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.medialibrary.service.MediaLibraryAccess;
import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import fr.insastronaute.webapi.user.domain.AppUser;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerMediaLibraryTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SiteAvailabilityService siteAvailabilityService;
    @MockitoBean private UserSynchronizationService userSynchronizationService;
    @MockitoBean private MediaLibraryAccess mediaLibraryAccess;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void theSessionSaysWhetherTheMediaLibraryIsOpen() throws Exception {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("staff-sub");
        user.setDisplayName("Camille");
        when(siteAvailabilityService.currentStatus()).thenReturn(new SiteAvailabilityService.SiteStatus(true, null));
        when(userSynchronizationService.syncWithKeycloak(any())).thenReturn(user);
        when(mediaLibraryAccess.canUse(eq("staff-sub"), any())).thenReturn(true);

        mockMvc.perform(get("/api/session")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_EVENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mediaLibrary").value(true));
    }
}
