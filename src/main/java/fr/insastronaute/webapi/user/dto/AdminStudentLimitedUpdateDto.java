package fr.insastronaute.webapi.user.dto;

import jakarta.validation.constraints.NotNull;

public record AdminStudentLimitedUpdateDto(
        @NotNull(message = "studentLimited est requis.")
        Boolean studentLimited
) {}
