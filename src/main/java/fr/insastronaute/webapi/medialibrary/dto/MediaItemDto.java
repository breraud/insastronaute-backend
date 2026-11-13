package fr.insastronaute.webapi.medialibrary.dto;

/** Un fichier d'une preuve. {@code selected} est nul tant que personne ne l'a retenu. */
public record MediaItemDto(int index, String contentType, long sizeBytes, MediaSelectionMarkDto selected) { }
