package fr.insastronaute.webapi.event.controller;

import fr.insastronaute.webapi.event.service.EventQuestionnaireService;
import fr.insastronaute.webapi.event.service.EventService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EventService eventService;
    @MockitoBean EventQuestionnaireService questionnaireService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void studentLimitedCanListEvents() throws Exception {
        when(eventService.listPlayerEvents("limited-sub")).thenReturn(List.of());

        mockMvc.perform(get("/api/events").with(limitedOidc()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(eventService).listPlayerEvents("limited-sub");
    }

    @Test
    void studentLimitedCannotMutateEventRegistrationOrAnswers() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{id}/registrations", eventId)
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
        mockMvc.perform(put("/api/events/{id}/registrations/answers", eventId)
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/events/{id}/registrations", eventId)
                        .with(limitedOidc()).with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verify(eventService, never()).register(any(), any(), any());
        verify(eventService, never()).updateAnswers(any(), any(), any());
        verify(eventService, never()).unregister(any(), any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor limitedOidc() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED"))
                .idToken(token -> token.subject("limited-sub"));
    }
}
