package fr.insastronaute.webapi.auth.dto;

import fr.insastronaute.webapi.site.service.SiteAvailabilityService;

public record SiteStatusDto(
        boolean playerAccessEnabled,
        String message
) {
    public static SiteStatusDto from(SiteAvailabilityService.SiteStatus status) {
        return new SiteStatusDto(status.playerAccessEnabled(), status.message());
    }
}
