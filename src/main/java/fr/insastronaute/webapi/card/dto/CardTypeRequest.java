package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CardTypeRequest(
        @NotBlank @Size(max = 50) String name
) {
}
