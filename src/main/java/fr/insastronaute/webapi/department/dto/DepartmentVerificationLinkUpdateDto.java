package fr.insastronaute.webapi.department.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DepartmentVerificationLinkUpdateDto(
        @NotNull(message = "Le département vérificateur est requis.")
        UUID verifyingDepartmentId,

        @NotNull(message = "Le département vérifié est requis.")
        UUID verifiedDepartmentId
) {
}
