package fr.insastronaute.webapi.card.print.dto;

import jakarta.validation.constraints.NotBlank;

public record CardDatasetCropLabelRequest(
        @NotBlank String label
) {
}
