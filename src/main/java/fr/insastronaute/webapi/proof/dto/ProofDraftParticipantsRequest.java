package fr.insastronaute.webapi.proof.dto;

import java.util.List;
import java.util.UUID;

/** Les coequipiers choisis pour un defi de groupe, tels que la page les affiche. */
public record ProofDraftParticipantsRequest(List<UUID> participantUserIds) {
}
