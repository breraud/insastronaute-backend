package fr.insastronaute.webapi.common.security.roles;

import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.event.service.EventQuestionnaireService;
import fr.insastronaute.webapi.event.service.EventService;
import fr.insastronaute.webapi.place.service.PlaceService;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentLimitedAccessBoundaryTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChallengeService challengeService;
    @MockitoBean TreasureHuntService treasureHuntService;
    @MockitoBean PlaceService placeService;
    @MockitoBean EventService eventService;
    @MockitoBean EventQuestionnaireService questionnaireService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void mixedStudentLimitedIsRejectedBeforeRequestBodyBindingAndValidation() throws Exception {
        UUID eventId = UUID.randomUUID();

        assertCodedForbidden(post("/api/challenges/{id}/submit", UUID.randomUUID())
                .with(mixedLimitedOidc()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proofType\":\"INVALID\"}"));
        // Un corps volontairement tronque : le 403 de role doit tomber avant la
        // deserialisation. Il porte la forme d'aujourd'hui -- { tool, secret } -- pour ne
        // pas donner l'ancienne en exemple.
        assertCodedForbidden(post("/api/treasure-hunt/checkpoints/validate")
                .with(mixedLimitedOidc()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tool\":"));
        assertCodedForbidden(post("/api/place/pixel")
                .with(mixedLimitedOidc()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        assertCodedForbidden(put("/api/events/{id}/registrations/answers", eventId)
                .with(mixedLimitedOidc()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"));
        assertCodedForbidden(post("/api/cards/instances/claim")
                .with(mixedLimitedOidc()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicId\":\"ABCDE\"}"));
        assertCodedForbidden(get("/api/cards/deck")
                .param("collectionType", "PHYSICAL")
                .with(mixedLimitedOidc()));
        assertCodedForbidden(get("/api/cards/deck")
                .param("scope", "TEAM")
                .with(mixedLimitedOidc()));

        verifyNoInteractions(challengeService, treasureHuntService, placeService, eventService);
    }

    @Test
    void ordinaryStudentStillReceivesRequestValidationErrors() throws Exception {
        mockMvc.perform(post("/api/place/pixel")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void assertCodedForbidden(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
    }

    private static RequestPostProcessor mixedLimitedOidc() {
        return oidcLogin().authorities(
                new SimpleGrantedAuthority("ROLE_STUDENT"),
                new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED")
        );
    }
}
