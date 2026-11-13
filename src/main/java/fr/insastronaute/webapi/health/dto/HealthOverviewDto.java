package fr.insastronaute.webapi.health.dto;

import java.util.List;

/**
 * @param now     l'état à l'instant de l'appel, lu directement dans le registre
 * @param history les relevés de la fenêtre demandée, du plus ancien au plus récent
 */
public record HealthOverviewDto(
        HealthPointDto now,
        List<HealthPointDto> history
) {
}
