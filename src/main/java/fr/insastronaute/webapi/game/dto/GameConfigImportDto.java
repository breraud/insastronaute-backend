package fr.insastronaute.webapi.game.dto;

/**
 * Ce qu'un import d'archive a reellement fait.
 *
 * <p>{@code deletedCount} vaut 0 hors mode remplacement : un import qui recouvre sans
 * effacer laisse en place des fichiers absents de l'archive, et l'admin doit pouvoir
 * lire lequel des deux comportements il vient de declencher.</p>
 */
public record GameConfigImportDto(int fileCount, int folderCount, boolean replaced, int deletedCount) {
}
