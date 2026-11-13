package fr.insastronaute.webapi.leaderboard.controller;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.dto.AdminLeaderboardConfigDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentFormulaPreviewDto;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardEntryDto;
import fr.insastronaute.webapi.leaderboard.dto.UserTotalLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaderboardService leaderboardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void getConfigsReturnsLeaderboardConfigsForAdmins() throws Exception {
        Instant lastReleasedAt = Instant.parse("2026-06-14T18:00:00Z");

        when(leaderboardService.getConfigs()).thenReturn(List.of(new AdminLeaderboardConfigDto(
                LeaderboardScope.USER,
                LeaderboardMetric.TOURNAMENT_POINTS,
                60,
                true,
                lastReleasedAt
        )));

        mockMvc.perform(get("/api/admin/leaderboards")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].scope").value("USER"))
                .andExpect(jsonPath("$[0].metric").value("TOURNAMENT_POINTS"))
                .andExpect(jsonPath("$[0].refreshDelayMinutes").value(60))
                .andExpect(jsonPath("$[0].autoRefreshEnabled").value(true))
                .andExpect(jsonPath("$[0].lastReleasedAt").value("2026-06-14T18:00:00Z"));
    }

    @Test
    void patchConfigUpdatesOneLeaderboardConfig() throws Exception {
        when(leaderboardService.updateConfig(
                org.mockito.ArgumentMatchers.eq(LeaderboardScope.USER),
                org.mockito.ArgumentMatchers.eq(LeaderboardMetric.TOURNAMENT_POINTS),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new AdminLeaderboardConfigDto(
                LeaderboardScope.USER,
                LeaderboardMetric.TOURNAMENT_POINTS,
                120,
                true,
                null
        ));

        mockMvc.perform(patch("/api/admin/leaderboards")
                        .param("scope", "USER")
                        .param("metric", "TOURNAMENT_POINTS")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshDelayMinutes": 120,
                                  "autoRefreshEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshDelayMinutes").value(120))
                .andExpect(jsonPath("$.autoRefreshEnabled").value(true));
    }

    @Test
    void releaseReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/admin/leaderboards/release")
                        .param("scope", "DEPARTMENT")
                        .param("metric", "CHALLENGE_POINTS")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(leaderboardService).release(LeaderboardScope.DEPARTMENT, LeaderboardMetric.CHALLENGE_POINTS);
    }

    @Test
    void formulaPreviewIsAdminOnlyAndReturnsDiagnostics() throws Exception {
        when(leaderboardService.getDepartmentFormulaPreview(LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(DepartmentFormulaPreviewDto.empty(LeaderboardMetric.CHALLENGE_POINTS));

        mockMvc.perform(get("/api/admin/leaderboards/departments/formula-preview")
                        .param("metric", "CHALLENGE_POINTS")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("CHALLENGE_POINTS"))
                .andExpect(jsonPath("$.recommendedFormula").value("BALANCED_SQRT"));
    }

    @Test
    void challengeLeadCanReadTheCompleteLiveTotalLeaderboard() throws Exception {
        when(leaderboardService.getAdminUserTotalLeaderboard(true)).thenReturn(new UserTotalLeaderboardDto(
                Instant.parse("2026-09-09T08:00:00Z"),
                List.of(new UserLeaderboardEntryDto(1, "Camille", "ITI", 245L, false, false, List.of()))
        ));

        mockMvc.perform(get("/api/admin/leaderboards/users/total")
                        .param("live", "true")
                        .with(user("lead").roles("RESPOS_DEFIS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAt").value("2026-09-09T08:00:00Z"))
                .andExpect(jsonPath("$.entries[0].name").value("Camille"))
                .andExpect(jsonPath("$.entries[0].score").value(245));

        verify(leaderboardService).getAdminUserTotalLeaderboard(true);
    }
}
