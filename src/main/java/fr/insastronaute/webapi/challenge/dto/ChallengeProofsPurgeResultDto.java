package fr.insastronaute.webapi.challenge.dto;

/**
 * Ce que l'effacement a emporte.
 *
 * @param purgedProofCount les preuves dont les fichiers sont partis. Zero se lit
 *                         aussi bien comme « rien a effacer » que comme « deja
 *                         efface » : dans les deux cas il ne reste plus de fichier.
 */
public record ChallengeProofsPurgeResultDto(int purgedProofCount) {
}
