package fr.insastronaute.webapi.place.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceBlueprintSaveDto(
        @NotBlank
        @Size(max = 80, message = "Le nom d'un plan ne doit pas dépasser 80 caractères.")
        String name,

        @NotNull @Positive @Max(value = 128, message = "Un plan ne peut pas dépasser 128 cases de côté.")
        Integer width,

        @NotNull @Positive @Max(value = 128, message = "Un plan ne peut pas dépasser 128 cases de côté.")
        Integer height,

        @NotNull List<String> palette,

        /** Une case par caractère, ligne par ligne. Voir BlueprintCells. */
        @NotNull String cells
) {}
