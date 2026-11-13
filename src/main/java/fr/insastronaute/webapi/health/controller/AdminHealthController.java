package fr.insastronaute.webapi.health.controller;

import fr.insastronaute.webapi.health.dto.HealthOverviewDto;
import fr.insastronaute.webapi.health.service.HealthOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * L'état du service, pour le panel.
 *
 * <p>Rien n'est relayé tel quel : le panel demande une fenêtre, le serveur rend des
 * points déjà calculés. Laisser passer une requête libre vers une base de métriques
 * aurait donné au panel les moyens de la saturer, et à quiconque prendrait sa place
 * la carte complète de l'API.
 */
@RestController
@RequestMapping("/api/admin/health")
public class AdminHealthController {

    /** Deux heures affichent l'incident en cours ; au-delà, on remonte à la journée. */
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(2);
    /** Sept jours de relevés à trente secondes font vingt mille points : le navigateur suit, au-delà non. */
    private static final Duration MAX_WINDOW = Duration.ofDays(7);

    private final HealthOverviewService overviewService;

    public AdminHealthController(HealthOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    @Operation(summary = "Service health overview", description = "Current readings plus the recorded history over the requested window.")
    public HealthOverviewDto overview(@RequestParam(required = false) Integer windowMinutes) {
        return overviewService.overview(window(windowMinutes));
    }

    static Duration window(Integer windowMinutes) {
        if (windowMinutes == null || windowMinutes <= 0) {
            return DEFAULT_WINDOW;
        }
        Duration asked = Duration.ofMinutes(windowMinutes);
        return asked.compareTo(MAX_WINDOW) > 0 ? MAX_WINDOW : asked;
    }
}
