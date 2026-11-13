package fr.insastronaute.webapi.challenge.dto;

import java.util.List;
import java.util.UUID;

public record ChallengeBulkUpdateResultDto(
        int updatedCount,
        List<UUID> updatedIds,
        /** Les preuves dont les fichiers sont partis, zero quand on n'a rien demande. */
        int purgedProofCount
) {}
