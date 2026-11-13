package fr.insastronaute.webapi.leaderboard.controller;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.dto.AdminLeaderboardConfigDto;
import fr.insastronaute.webapi.leaderboard.dto.LeaderboardConfigUpdateDto;
import fr.insastronaute.webapi.leaderboard.dto.AdminDepartmentLeaderboardSettingsDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentHeadcountUpdateDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentFormulaPreviewDto;
import fr.insastronaute.webapi.leaderboard.dto.TotalLeaderboardWeightsUpdateDto;
import fr.insastronaute.webapi.leaderboard.dto.UserTotalLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.service.DepartmentLeaderboardSettingsService;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/leaderboards")
@Tag(name = "Admin Leaderboards")
public class AdminLeaderboardController {

    private final LeaderboardService leaderboardService;
    private final DepartmentLeaderboardSettingsService departmentSettings;

    public AdminLeaderboardController(LeaderboardService leaderboardService, DepartmentLeaderboardSettingsService departmentSettings) {
        this.leaderboardService = leaderboardService;
        this.departmentSettings = departmentSettings;
    }

    @GetMapping("/departments/settings") public List<AdminDepartmentLeaderboardSettingsDto> departmentSettings(){ return departmentSettings.list(); }
    @PutMapping("/departments/settings/{departmentId}") public AdminDepartmentLeaderboardSettingsDto updateDepartmentSettings(@PathVariable java.util.UUID departmentId,@Valid @RequestBody DepartmentHeadcountUpdateDto dto){ return departmentSettings.update(departmentId,dto.theoreticalHeadcount()); }

    @GetMapping("/departments/formula-preview")
    @Operation(summary = "Preview department scoring formulas", description = "Computes all department scoring formulas without publishing a snapshot.")
    public DepartmentFormulaPreviewDto formulaPreview(@RequestParam LeaderboardMetric metric) {
        return leaderboardService.getDepartmentFormulaPreview(metric);
    }

    @PutMapping("/users/total/weights")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Update total user leaderboard weights", description = "Stores metric weights that must total 100 percent, and optionally the factor each metric's points are multiplied by before its weight.")
    public void updateTotalLeaderboardWeights(@Valid @RequestBody TotalLeaderboardWeightsUpdateDto dto) {
        leaderboardService.updateTotalLeaderboardWeights(dto.weights(), dto.factors());
    }

    @GetMapping
    @Operation(summary = "List leaderboard configs", description = "Returns the administrative configuration for every leaderboard scope and metric.")
    public List<AdminLeaderboardConfigDto> getConfigs() {
        return leaderboardService.getConfigs();
    }

    @PatchMapping
    @Operation(summary = "Update leaderboard config", description = "Updates visibility or release configuration for one leaderboard scope and metric.")
    public AdminLeaderboardConfigDto updateConfig(
            @RequestParam LeaderboardScope scope,
            @RequestParam LeaderboardMetric metric,
            @Valid @RequestBody LeaderboardConfigUpdateDto dto
    ) {
        return leaderboardService.updateConfig(scope, metric, dto);
    }

    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Release a leaderboard", description = "Creates and publishes a fresh snapshot for one leaderboard scope and metric.")
    public void release(
            @RequestParam LeaderboardScope scope,
            @RequestParam LeaderboardMetric metric
    ) {
        leaderboardService.release(scope, metric);
    }

    @PostMapping("/release-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Release all leaderboards", description = "Creates and publishes fresh snapshots for all or scoped leaderboards.")
    public void releaseAll(@RequestParam(required = false) LeaderboardScope scope) {
        leaderboardService.releaseAll(scope);
    }

    @PatchMapping("/bulk")
    @Operation(summary = "Bulk update leaderboard configs", description = "Updates configuration for all or scoped leaderboards.")
    public List<AdminLeaderboardConfigDto> bulkUpdate(
            @RequestParam(required = false) LeaderboardScope scope,
            @Valid @RequestBody LeaderboardConfigUpdateDto dto
    ) {
        return leaderboardService.bulkUpdateConfigs(scope, dto);
    }

    @PostMapping("/sync-keycloak")
    @Operation(summary = "Sync deleted Keycloak users", description = "Excludes users deleted in Keycloak from leaderboards.")
    public java.util.Map<String, Object> syncKeycloak() {
        int hiddenCount = leaderboardService.syncDeletedKeycloakUsers();
        return java.util.Map.of("success", true, "hiddenCount", hiddenCount);
    }

    @GetMapping("/latest")
    @Operation(summary = "Preview latest leaderboards", description = "Returns the latest released or live leaderboard data for the selected administrative scope.")
    public Object getLatest(
            @RequestParam LeaderboardScope scope,
            @RequestParam(required = false) List<LeaderboardMetric> metrics,
            @RequestParam(required = false, defaultValue = "false") boolean live
    ) {
        if (live) {
            return switch (scope) {
                case USER -> leaderboardService.getLiveUserLeaderboards(metrics);
                case DEPARTMENT -> leaderboardService.getLiveDepartmentLeaderboards(metrics);
            };
        }
        return switch (scope) {
            case USER -> leaderboardService.getLatestUserLeaderboards(metrics);
            case DEPARTMENT -> leaderboardService.getLatestDepartmentLeaderboards(metrics);
        };
    }

    @GetMapping("/users/total")
    @Operation(summary = "Preview the complete total user leaderboard", description = "Aggregates either current scores or the latest released snapshots with the configured total weights.")
    public UserTotalLeaderboardDto getUserTotal(
            @RequestParam(required = false, defaultValue = "false") boolean live
    ) {
        return leaderboardService.getAdminUserTotalLeaderboard(live);
    }
}
