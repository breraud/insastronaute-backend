package fr.insastronaute.webapi.proof.dto;

import fr.insastronaute.webapi.friend.dto.FriendUserDto;

import java.util.List;
import java.util.UUID;

public record ProofDraftResponse(
        long remainingSizeBytes,
        List<ProofDraftDto> files,
        /** Les coequipiers choisis, pour les retrouver apres un rechargement. */
        List<FriendUserDto> participants,
        /**
         * Ceux d'entre eux qui sont entres en scannant le code, et non sur invitation.
         *
         * <p>Les deux voies menent a la meme equipe, mais pas au meme geste : l'une a
         * ete choisie par le chef, l'autre s'est ajoutee elle-meme. Le chef doit
         * pouvoir les distinguer d'un coup d'oeil.</p>
         */
        List<UUID> qrJoinedUserIds
) {
}
