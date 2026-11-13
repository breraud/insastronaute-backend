package fr.insastronaute.webapi.medialibrary.repository;

/** Ce qu'une partie d'export contient : combien de fichiers, combien d'octets. */
public record MediaExportPartSummary(Integer partNumber, Long itemCount, Long bytes) {
}
