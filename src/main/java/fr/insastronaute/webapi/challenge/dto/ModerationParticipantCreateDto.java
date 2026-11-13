package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ModerationParticipantCreateDto(@NotNull UUID userId) {
}
