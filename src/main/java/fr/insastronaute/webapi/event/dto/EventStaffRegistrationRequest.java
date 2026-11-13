package fr.insastronaute.webapi.event.dto;

import jakarta.validation.constraints.NotNull;

public record EventStaffRegistrationRequest(
        @NotNull(message = "Le blocage du staff est requis.")
        Boolean blocked
) {
}
