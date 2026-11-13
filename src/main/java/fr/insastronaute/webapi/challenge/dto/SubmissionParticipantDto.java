package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.UUID;

public record SubmissionParticipantDto(
        UUID userId,
        String displayName,
        String email,
        String confirmationStatus,
        Instant invitedAt,
        Instant respondedAt,
        boolean submitter,
        /**
         * Cette personne est entree en scannant le code, pas sur invitation.
         *
         * <p>Les deux voies menent au meme groupe, mais pas au meme geste : l'une a
         * ete invitee puis a repondu, l'autre s'est ajoutee elle-meme. Le chef doit
         * pouvoir les distinguer d'un coup d'oeil.</p>
         */
        boolean joinedByQrCode
) {
}
