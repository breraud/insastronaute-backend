package fr.insastronaute.webapi.place.dto;

import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.domain.PlaceVisibilityMode;

import java.time.Instant;

public record PlaceConfigDto(
        boolean isActive,
        Instant startDate,
        Instant endDate,
        int canvasWidth,
        int canvasHeight,
        long baseCooldownMs,
        long challengeCooldownMs,
        int challengeRatio,
        long bonusPixelPrice,
        long bonusPixelPriceIncrement,
        boolean showPixelAuthors,
        PlaceVisibilityMode visibilityMode,
        boolean departmentCanvasEnabled,
        boolean resultsVisible
) {
    public static PlaceConfigDto from(PlaceConfig config) {
        return new PlaceConfigDto(
                config.isActive(), config.getStartDate(), config.getEndDate(),
                config.getCanvasWidth(), config.getCanvasHeight(), config.getBaseCooldownMs(),
                config.getChallengeCooldownMs(), config.getChallengeRatio(),
                config.getBonusPixelPrice(), config.getBonusPixelPriceIncrement(),
                config.isShowPixelAuthors(),
                config.getVisibilityMode(),
                config.isDepartmentCanvasEnabled(),
                config.isResultsVisible()
        );
    }
}
