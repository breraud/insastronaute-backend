package fr.insastronaute.webapi.tournament.controller;

import fr.insastronaute.webapi.tournament.service.TournamentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inscrire quelqu'un a la main est plus etroit que le reste de l'ecran des tournois :
 * les respos evenements et departements pointent les presences, mais seuls les respos
 * tournois et les administrateurs ajoutent des joueurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTournamentControllerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-00000000e001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentService tournamentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RESPO_TOURNOIS", "SOUS_RESPO_TOURNOIS"})
    void tournamentManagersCanRegisterAPlayer(String role) throws Exception {
        mockMvc.perform(register().with(user("staff").roles(role)))
                .andExpect(status().isOk());

        verify(tournamentService).registerParticipant(EVENT_ID, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESPOS_EVENT", "SOUS_RESPO_EVENT", "RESPO_DEPART", "SOUS_RESPO_DEPART", "STUDENT"})
    void otherRolesCannotRegisterAPlayer(String role) throws Exception {
        mockMvc.perform(register().with(user("staff").roles(role)))
                .andExpect(status().isForbidden());

        verify(tournamentService, never()).registerParticipant(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESPO_TOURNOIS"})
    void aMissingPlayerIsRejectedBeforeTheService(String role) throws Exception {
        mockMvc.perform(post("/api/admin/tournaments/{eventId}/registrations", EVENT_ID)
                        .with(user("staff").roles(role))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(tournamentService, never()).registerParticipant(any(), any());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register() {
        return post("/api/admin/tournaments/{eventId}/registrations", EVENT_ID)
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\"}");
    }
}
