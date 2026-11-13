package fr.insastronaute.webapi.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Un defi accorde a la main.
 *
 * @param bonusPoints l'ajustement, absent quand il n'y en a pas. Il se posait
 *                    jusqu'ici en deux temps — accorder, puis rouvrir la preuve —
 *                    et la seconde etape, souvent oubliee, laissait la recompense
 *                    nue. Un malus ne descend sous zero que si le defi l'autorise.
 */
public record AdminManualChallengeSubmissionRequest(
        @NotNull UUID challengeId,
        @Size(max = 4000) String comment,
        Integer bonusPoints
) {
}
