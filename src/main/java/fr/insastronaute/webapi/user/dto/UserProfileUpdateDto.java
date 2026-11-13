package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.user.domain.Gender;
import jakarta.validation.constraints.NotNull;

public record UserProfileUpdateDto(
        @NotNull Gender gender
) {}
