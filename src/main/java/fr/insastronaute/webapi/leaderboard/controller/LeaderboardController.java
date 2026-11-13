package fr.insastronaute.webapi.leaderboard.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.dto.UserTotalLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final UserSynchronizationService synchronizationService;

    public LeaderboardController(
            LeaderboardService leaderboardService,
            UserSynchronizationService synchronizationService
    ) {
        this.leaderboardService = leaderboardService;
        this.synchronizationService = synchronizationService;
    }

    @GetMapping("/api/leaderboards/users")
    @Operation(summary = "Get user leaderboards", description = "Returns the latest released user leaderboard snapshots, optionally limited to selected metrics.")
    public List<UserLeaderboardDto> getUserLeaderboards(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(required = false) List<LeaderboardMetric> metrics
    ) {
        AppUser requester = synchronizationService.syncWithKeycloak(oidcUser);
        return leaderboardService.getLatestUserLeaderboards(metrics, requester);
    }

    @GetMapping("/api/leaderboards/users/total")
    @Operation(summary = "Get total user leaderboard", description = "Returns the total of the latest user leaderboard snapshots, limited to the top 10, the authenticated player, and their accepted friends.")
    public UserTotalLeaderboardDto getTotalUserLeaderboard(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        AppUser requester = synchronizationService.syncWithKeycloak(oidcUser);
        return leaderboardService.getLatestUserTotalLeaderboard(requester);
    }

    @GetMapping("/api/leaderboards/departments")
    @Operation(summary = "Get department leaderboards", description = "Returns the latest released department leaderboard snapshots, optionally limited to selected metrics.")
    public List<DepartmentLeaderboardDto> getDepartmentLeaderboards(
            @RequestParam(required = false) List<LeaderboardMetric> metrics
    ) {
        return leaderboardService.getLatestDepartmentLeaderboards(metrics);
    }
}
