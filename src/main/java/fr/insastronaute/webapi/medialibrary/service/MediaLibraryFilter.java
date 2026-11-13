package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.web.BadRequestException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Les filtres communs a toutes les vues : statut et type de media. */
public record MediaLibraryFilter(List<String> statuses, String type) {

    public static final List<String> ALL_STATUSES = List.of("WAITING_CONFIRMATION", "PENDING", "VALIDATED", "REJECTED");
    private static final Set<String> TYPES = Set.of("photo", "video");

    public static MediaLibraryFilter of(List<String> status, String type) {
        List<String> statuses = status == null || status.isEmpty()
                ? ALL_STATUSES
                : status.stream().map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        if (!ALL_STATUSES.containsAll(statuses)) {
            throw new BadRequestException("Statut de soumission inconnu.");
        }
        String normalizedType = type == null || type.isBlank() ? null : type.toLowerCase(Locale.ROOT);
        if (normalizedType != null && !TYPES.contains(normalizedType)) {
            throw new BadRequestException("Type de média inconnu : photo ou video.");
        }
        return new MediaLibraryFilter(statuses, normalizedType);
    }
}
