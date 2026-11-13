package fr.insastronaute.webapi.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DepartmentColorUpdateDto(
        @NotBlank(message = "La couleur est obligatoire.")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit etre au format #RRGGBB.")
        String color
) {
}
