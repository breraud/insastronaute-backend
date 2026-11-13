package fr.insastronaute.webapi.user.dto;

import jakarta.validation.constraints.NotNull;

public record TokenBalanceUpdateDto(
        @NotNull(message = "La variation de tokens est requise.") Long tokensDelta
) {
}
