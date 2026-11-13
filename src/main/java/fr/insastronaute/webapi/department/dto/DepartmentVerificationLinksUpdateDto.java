package fr.insastronaute.webapi.department.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DepartmentVerificationLinksUpdateDto(
        @NotNull(message = "La liste des liens de vérification est requise.")
        List<@Valid DepartmentVerificationLinkUpdateDto> links
) {
}
