package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.dto.MediaChallengeDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryBrowseService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryFilter;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaLibraryBrowseControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MediaLibraryBrowseService browseService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void staffReachesTheService() throws Exception {
        UUID challengeId = UUID.randomUUID();
        when(browseService.challenges(eq("staff-sub"), any(), eq(new MediaLibraryFilter(List.of("VALIDATED"), "photo"))))
                .thenReturn(List.of(new MediaChallengeDto(challengeId, "Photo", "Soirée", 3, 1)));

        mockMvc.perform(get("/api/media-library/challenges").param("status", "VALIDATED").param("type", "photo")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_EVENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Photo"))
                .andExpect(jsonPath("$[0].retainedCount").value(1));
    }

    @Test
    void aStudentIsStoppedBeforeTheService() throws Exception {
        mockMvc.perform(get("/api/media-library/challenges")
                        .with(oidcLogin().idToken(token -> token.subject("student-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verify(browseService, never()).challenges(any(), any(), any());
    }
}
