package fr.insastronaute.webapi.economy.controller;

import fr.insastronaute.webapi.economy.dto.EconomyStatsDto;
import fr.insastronaute.webapi.economy.service.EconomyStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@Tag(name = "Admin stats")
public class AdminEconomyStatsController {

    private final EconomyStatsService statsService;

    public AdminEconomyStatsController(EconomyStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/economy")
    @Operation(
            summary = "Token and event points economy",
            description = "Inflow by source, per-action and per-player distributions, balances, historical payouts "
                    + "per challenge and per game run, and detected anomalies. The window is capped at 90 days.")
    public EconomyStatsDto economy(@RequestParam(defaultValue = "30") int days) {
        return statsService.stats(days);
    }
}
