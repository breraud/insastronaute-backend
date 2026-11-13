package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.dto.MediaRefDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSelectionPatchDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibrarySelectionService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaLibrarySelectionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MediaLibrarySelectionService selectionService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void patchingTheSelectionAnswersNoContent() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(patch("/api/media-library/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"add\":[{\"submissionId\":\"" + submissionId + "\",\"index\":0}]}")
                        .with(csrf().asHeader())
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_EVENT"))))
                .andExpect(status().isNoContent());

        verify(selectionService).patch(eq("staff-sub"), any(),
                eq(new MediaSelectionPatchDto(List.of(new MediaRefDto(submissionId, 0)), null)));
    }

    @Test
    void theRetainedCountIsOpenToChallengeLeads() throws Exception {
        UUID challengeId = UUID.randomUUID();
        when(selectionService.retainedInChallenges(List.of(challengeId))).thenReturn(4L);

        mockMvc.perform(get("/api/admin/challenges/retained-media-count").param("ids", challengeId.toString())
                        .with(oidcLogin().idToken(token -> token.subject("lead-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RESPOS_DEFIS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }
}
