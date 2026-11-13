package fr.insastronaute.webapi.site.dto;

import fr.insastronaute.webapi.site.domain.SiteAvailability;

import java.time.Instant;

public record SiteAvailabilityDto(
        boolean playerAccessEnabled,
        String message,
        Instant updatedAt,
        String updatedBy
) {
    public static SiteAvailabilityDto from(SiteAvailability entity) {
        return new SiteAvailabilityDto(
                entity.isPlayerAccessEnabled(),
                entity.getMessage(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy() == null ? null : entity.getUpdatedBy().getDisplayName()
        );
    }
}
