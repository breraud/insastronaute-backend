package fr.insastronaute.webapi.user.dto;

import jakarta.validation.constraints.NotNull;

public record AdminPerfUpdateDto(
        @NotNull(message = "La coche PERF est requise.")
        Boolean perf
) {
}
