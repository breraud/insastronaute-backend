package fr.insastronaute.webapi.treasurehunt.controller;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayerResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayersResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPathStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerPathStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntStepStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntValidatedStepResponse;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTreasureHuntStatsControllerTest {
    private static final Instant T0 = Instant.parse("2026-09-20T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private TreasureHuntStatsService service;

    @Test
    void servesTheStatsOfEveryStepToAdmins() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(service.checkpointStats()).thenReturn(new TreasureHuntCheckpointStatsResponse(42, 3, List.of(
                new TreasureHuntPathStatsResponse(pathId, 1, "Voie Informatique", "INFO", 30, 12, List.of(
                        new TreasureHuntStepStatsResponse(UUID.randomUUID(), 1, 30, T0, T0.plusSeconds(60), null),
                        new TreasureHuntStepStatsResponse(UUID.randomUUID(), 2, 21, T0, T0, 5400L))))));

        mockMvc.perform(get("/api/admin/treasure-hunt/stats/checkpoints")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(42))
                .andExpect(jsonPath("$.vaultUnlockCount").value(3))
                .andExpect(jsonPath("$.paths[0].departmentShortName").value("INFO"))
                .andExpect(jsonPath("$.paths[0].steps[0].firstValidatedAt").value("2026-09-20T10:00:00Z"))
                .andExpect(jsonPath("$.paths[0].steps[1].medianSecondsFromPreviousStep").value(5400));
    }

    @Test
    void servesAPlayersHuntToAdmins() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.playerStats(userId)).thenReturn(new TreasureHuntPlayerStatsResponse(userId, T0, null, List.of(
                new TreasureHuntPlayerPathStatsResponse(UUID.randomUUID(), 1, "Voie Informatique", "INFO", 3, 1, null,
                        List.of(new TreasureHuntValidatedStepResponse(1, T0))))));

        mockMvc.perform(get("/api/admin/treasure-hunt/stats/players/{userId}", userId)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.paths[0].validatedSteps").value(1))
                .andExpect(jsonPath("$.paths[0].steps[0].validatedAt").value("2026-09-20T10:00:00Z"));
    }

    @Test
    void answersNotFoundForAnUnknownPlayer() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.playerStats(userId)).thenThrow(new NotFoundException("Utilisateur introuvable."));

        mockMvc.perform(get("/api/admin/treasure-hunt/stats/players/{userId}", userId)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsTheStatsFromStaffWhoAreNotAdmins() throws Exception {
        // Un moderateur ouvre la fiche joueur, mais la chasse reste a l'administration :
        // tout /api/admin/treasure-hunt est reserve au role ADMIN.
        mockMvc.perform(get("/api/admin/treasure-hunt/stats/checkpoints")
                        .with(SecurityMockMvcRequestPostProcessors.user("moderator").roles("MODERATOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/treasure-hunt/stats/players/{userId}", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.user("moderator").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void servesTheArrivalsOfAStepToAdmins() throws Exception {
        UUID checkpointId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.checkpointPlayers(checkpointId)).thenReturn(new TreasureHuntCheckpointPlayersResponse(
                checkpointId, UUID.randomUUID(), 2, List.of(
                        new TreasureHuntCheckpointPlayerResponse(userId, "Alice", "INFO", T0, 3600L),
                        new TreasureHuntCheckpointPlayerResponse(UUID.randomUUID(), null, null, T0.plusSeconds(60), null))));

        mockMvc.perform(get("/api/admin/treasure-hunt/stats/checkpoints/{checkpointId}/players", checkpointId)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepNumber").value(2))
                .andExpect(jsonPath("$.players[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.players[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.players[0].validatedAt").value("2026-09-20T10:00:00Z"))
                .andExpect(jsonPath("$.players[0].secondsFromPreviousStep").value(3600))
                .andExpect(jsonPath("$.players[1].displayName").isEmpty())
                .andExpect(jsonPath("$.players[1].secondsFromPreviousStep").isEmpty());
    }

    @Test
    void answersNotFoundForAnUnknownStep() throws Exception {
        UUID checkpointId = UUID.randomUUID();
        when(service.checkpointPlayers(checkpointId)).thenThrow(new NotFoundException("Étape introuvable."));

        mockMvc.perform(get("/api/admin/treasure-hunt/stats/checkpoints/{checkpointId}/players", checkpointId)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsTheArrivalsOfAStepFromStaffWhoAreNotAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/treasure-hunt/stats/checkpoints/{checkpointId}/players", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.user("moderator").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }
}
