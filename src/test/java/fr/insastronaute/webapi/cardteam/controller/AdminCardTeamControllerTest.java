package fr.insastronaute.webapi.cardteam.controller;

import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.dto.CardDeckTeamDto;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCardTeamControllerTest {

    private static final String PATH = "/api/admin/card-teams";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminCardTeamService adminCardTeamService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void everyAuthorisedRoleCanListTheTeams() throws Exception {
        // La boucle ci-dessous ne verifie que le mecanisme : elle iterait tout aussi bien
        // sur un tableau ampute. Sans cette assertion sur la composition exacte, un role
        // retire par erreur du tableau perdrait silencieusement son acces sans faire
        // echouer aucun test de ce fichier.
        assertThat(SecurityConfig.CARD_TEAM_ADMIN_ROLES).containsExactlyInAnyOrder(
                "ADMIN",
                "RESPO_CARTES",
                "SOUS_RESPO_CARTES",
                "RESPO_DEPART",
                "SOUS_RESPO_DEPART"
        );

        for (String role : SecurityConfig.CARD_TEAM_ADMIN_ROLES) {
            mockMvc.perform(get(PATH).with(user("staff").roles(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anUnrelatedStaffRoleIsRefused() throws Exception {
        mockMvc.perform(get(PATH).with(user("goodies").roles("SOUS_RESPO_GOODIES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPlayerIsRefused() throws Exception {
        mockMvc.perform(get(PATH).with(user("player").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theSearchQueryReachesTheService() throws Exception {
        mockMvc.perform(get(PATH).param("query", "Commeres").with(user("staff").roles("SOUS_RESPO_CARTES")))
                .andExpect(status().isOk());

        verify(adminCardTeamService).search(org.mockito.ArgumentMatchers.eq("Commeres"), any());
    }

    @Test
    void anAuthorisedRoleCanLoadATeamsPhysicalCollection() throws Exception {
        UUID teamId = UUID.randomUUID();
        CardDeckDto deck = new CardDeckDto(
                3,
                12,
                List.of(),
                CardCollectionScope.TEAM,
                new CardDeckTeamDto(teamId, "Nova", 2, 6),
                150L
        );
        when(adminCardTeamService.getCollection(teamId)).thenReturn(deck);

        mockMvc.perform(get(PATH + "/" + teamId + "/collection")
                        .with(user("staff").roles("SOUS_RESPO_CARTES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCount").value(3))
                .andExpect(jsonPath("$.scope").value("TEAM"))
                .andExpect(jsonPath("$.team.id").value(teamId.toString()))
                .andExpect(jsonPath("$.teamScore").value(150));

        verify(adminCardTeamService).getCollection(teamId);
    }

    @Test
    void anUnrelatedRoleCannotLoadATeamsPhysicalCollection() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(get(PATH + "/" + teamId + "/collection")
                        .with(user("goodies").roles("SOUS_RESPO_GOODIES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void movingAPlayerReachesTheService() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post(PATH + "/" + teamId + "/members/" + userId)
                        .with(user("staff").roles("SOUS_RESPO_DEPART")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminCardTeamService).moveMember(teamId, userId);
    }

    @Test
    void mergingReachesTheServiceWithBothTeams() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        mockMvc.perform(post(PATH + "/" + targetId + "/merge")
                        .with(user("staff").roles("RESPO_CARTES")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceTeamId\":\"" + sourceId + "\"}"))
                .andExpect(status().isNoContent());

        verify(adminCardTeamService).merge(targetId, sourceId);
    }

    @Test
    void deletingATeamReachesTheService() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(delete(PATH + "/" + teamId)
                        .with(user("staff").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminCardTeamService).delete(teamId);
    }

    @Test
    void removingAPlayerReachesTheService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete(PATH + "/members/" + userId)
                        .with(user("staff").roles("SOUS_RESPO_CARTES")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminCardTeamService).removeMember(userId);
    }
}
