package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Le defi pour lequel on veut afficher un code d'invitation. */
public record GroupInviteCreateDto(@NotNull UUID challengeId) {
}
