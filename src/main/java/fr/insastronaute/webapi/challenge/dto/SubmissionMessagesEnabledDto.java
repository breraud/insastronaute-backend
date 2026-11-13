package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotNull;

public record SubmissionMessagesEnabledDto(
        @NotNull Boolean enabled
) {
}
