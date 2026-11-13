package fr.insastronaute.webapi.game.dto;

/**
 * Ce qu'un envoi de build ne doit pas depasser.
 *
 * <p>Lu dans la configuration reellement appliquee, et non recopie cote client : ces
 * bornes se surchargent par variable d'environnement, et un environnement les a deja
 * eues bien plus basses que ce que le code declare. Un chiffre en dur dans le panel
 * aurait annonce une limite fausse -- soit en laissant partir un envoi voue a l'echec,
 * soit en refusant un envoi que le serveur aurait accepte.</p>
 */
public record GameAssetLimitsDto(
        long maxFileSizeBytes,
        long maxRequestSizeBytes,
        int maxPartCount
) {
}
