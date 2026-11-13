package fr.insastronaute.webapi.leaderboard.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentLeaderboardEntryDto;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardEntryDto;
import fr.insastronaute.webapi.leaderboard.dto.UserTotalLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaderboardService leaderboardService;

    @MockitoBean
    private UserSynchronizationService synchronizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void userLeaderboardsReturnRequestedMetrics() throws Exception {
        Instant calculatedAt = Instant.parse("2026-06-14T18:00:00Z");
        AppUser requester = new AppUser();

        when(synchronizationService.syncWithKeycloak(org.mockito.ArgumentMatchers.any())).thenReturn(requester);
        when(leaderboardService.getLatestUserLeaderboards(List.of(LeaderboardMetric.TOURNAMENT_POINTS), requester))
                .thenReturn(List.of(new UserLeaderboardDto(
                        LeaderboardMetric.TOURNAMENT_POINTS,
                        calculatedAt,
                        List.of(new UserLeaderboardEntryDto(1, "Test User", "CFI", 42L, false, false, java.util.List.of()))
                )));

        mockMvc.perform(get("/api/leaderboards/users")
                        .param("metrics", "TOURNAMENT_POINTS")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].metric").value("TOURNAMENT_POINTS"))
                .andExpect(jsonPath("$[0].calculatedAt").value("2026-06-14T18:00:00Z"))
                .andExpect(jsonPath("$[0].entries[0].rank").value(1))
                .andExpect(jsonPath("$[0].entries[0].name").value("Test User"))
                .andExpect(jsonPath("$[0].entries[0].department").value("CFI"))
                .andExpect(jsonPath("$[0].entries[0].score").value(42));

        verify(synchronizationService).syncWithKeycloak(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void departmentLeaderboardsDoNotExposeDepartmentFieldOnEntries() throws Exception {
        Instant calculatedAt = Instant.parse("2026-06-14T18:00:00Z");

        when(leaderboardService.getLatestDepartmentLeaderboards(List.of(LeaderboardMetric.CHALLENGE_POINTS)))
                .thenReturn(List.of(new DepartmentLeaderboardDto(
                        LeaderboardMetric.CHALLENGE_POINTS,
                        calculatedAt,
                        List.of(new DepartmentLeaderboardEntryDto(1, "ITI", 100L))
                )));

        mockMvc.perform(get("/api/leaderboards/departments")
                        .param("metrics", "CHALLENGE_POINTS")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].entries[0].name").value("ITI"))
                .andExpect(jsonPath("$[0].entries[0].department").doesNotExist());
    }

    /** Le détail du score part avec le classement : c'est lui que l'explication affiche. */
    @Test
    void departmentLeaderboardsCarryTheDetailOfTheirScore() throws Exception {
        when(leaderboardService.getLatestDepartmentLeaderboards(List.of(LeaderboardMetric.CHALLENGE_POINTS)))
                .thenReturn(List.of(new DepartmentLeaderboardDto(
                        LeaderboardMetric.CHALLENGE_POINTS,
                        Instant.parse("2026-09-15T12:00:00Z"),
                        "BALANCED_SQRT",
                        120,
                        List.of(new DepartmentLeaderboardEntryDto(1, "GM", 900L, 400L, 40, 60, 2.25, 0L, 0))
                )));

        mockMvc.perform(get("/api/leaderboards/departments")
                        .param("metrics", "CHALLENGE_POINTS")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formula").value("BALANCED_SQRT"))
                .andExpect(jsonPath("$[0].referenceHeadcount").value(120))
                .andExpect(jsonPath("$[0].entries[0].rawTotal").value(400))
                .andExpect(jsonPath("$[0].entries[0].coefficient").value(2.25))
                .andExpect(jsonPath("$[0].entries[0].memberCount").value(40))
                .andExpect(jsonPath("$[0].entries[0].theoreticalHeadcount").value(60))
                .andExpect(jsonPath("$[0].entries[0].department").doesNotExist());
    }

    @Test
    void totalUserLeaderboardUsesAuthenticatedRequester() throws Exception {
        Instant calculatedAt = Instant.parse("2026-07-27T12:00:00Z");
        AppUser requester = new AppUser();

        when(synchronizationService.syncWithKeycloak(org.mockito.ArgumentMatchers.any())).thenReturn(requester);
        when(leaderboardService.getLatestUserTotalLeaderboard(requester))
                .thenReturn(new UserTotalLeaderboardDto(
                        calculatedAt,
                        List.of(new UserLeaderboardEntryDto(37, "Current User", "ITI", 1234L, true, false, java.util.List.of()))
                ));

        mockMvc.perform(get("/api/leaderboards/users/total").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAt").value("2026-07-27T12:00:00Z"))
                .andExpect(jsonPath("$.entries[0].rank").value(37))
                .andExpect(jsonPath("$.entries[0].score").value(1234));
    }
}
