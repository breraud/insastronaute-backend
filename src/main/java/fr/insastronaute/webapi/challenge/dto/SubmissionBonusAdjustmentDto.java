package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Les points bonus d'une preuve deja validee, revus.
 *
 * @param bonusPoints le nouveau total bonus, pas un delta : deux ajustements
 *                    concurrents sur la meme preuve doivent donner le nombre
 *                    affiche, pas leur somme
 * @param reason      ce qui explique le changement. Obligatoire, et lu par le
 *                    joueur autant que par le registre
 */
public record SubmissionBonusAdjustmentDto(
        @NotNull Integer bonusPoints,
        @NotBlank @Size(max = 1000) String reason
) {
}
