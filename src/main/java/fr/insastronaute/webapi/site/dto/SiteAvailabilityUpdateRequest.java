package fr.insastronaute.webapi.site.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SiteAvailabilityUpdateRequest(
        @NotNull Boolean playerAccessEnabled,
        @Size(max = 500) String message
) {}
