package fr.insastronaute.webapi.cardteam.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.cardteam.dto.CardTeamDto;
import fr.insastronaute.webapi.cardteam.dto.CardTeamStateDto;
import fr.insastronaute.webapi.cardteam.dto.CardTeamUserDto;
import fr.insastronaute.webapi.cardteam.service.CardTeamService;
import fr.insastronaute.webapi.user.domain.AppUser;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardTeamControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean UserSynchronizationService synchronizationService;
    @MockitoBean CardTeamService cardTeamService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void getsTheAuthenticatedPlayersTeamState() throws Exception {
        AppUser user = user();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);
        when(cardTeamService.getState(user)).thenReturn(new CardTeamStateDto(null, List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/card-teams/state").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team").doesNotExist())
                .andExpect(jsonPath("$.discoverableTeams").isArray());
    }

    @Test
    void exposesRequestAndInvitationMutationsToAuthenticatedPlayers() throws Exception {
        AppUser user = user();
        UUID teamId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);

        mockMvc.perform(post("/api/card-teams/{teamId}/requests", teamId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/card-teams/invitations/{userId}", friendId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/card-teams/requests/{requestId}/accept", requestId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/card-teams/requests/{requestId}", requestId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/card-teams/invitations/{invitationId}/accept", invitationId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/card-teams/invitations/{invitationId}", invitationId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(cardTeamService).requestJoin(user, teamId);
        verify(cardTeamService).invite(user, friendId, null);
        verify(cardTeamService).acceptJoinRequest(user, requestId);
        verify(cardTeamService).declineJoinRequest(user, requestId);
        verify(cardTeamService).acceptInvitation(user, invitationId);
        verify(cardTeamService).declineInvitation(user, invitationId);
    }

    /** Fonder une equipe, c'est inviter quelqu'un en proposant un nom. */
    @Test
    void carriesTheProposedTeamNameOnTheInvitation() throws Exception {
        AppUser user = user();
        UUID friendId = UUID.randomUUID();
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);

        mockMvc.perform(post("/api/card-teams/invitations/{userId}", friendId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Les Cometes\"}"))
                .andExpect(status().isNoContent());

        verify(cardTeamService).invite(user, friendId, "Les Cometes");
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setDisplayName("Alice");
        user.setEmail("alice@example.test");
        return user;
    }
}
