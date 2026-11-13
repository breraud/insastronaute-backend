package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EventRequest(
        @NotBlank(message = "Le titre est requis.")
        @Size(max = 180, message = "Le titre ne peut pas dépasser 180 caractères.")
        String title,

        // Facultative : beaucoup d'evenements se decrivent par leur titre, leur lieu et
        // leur horaire. Exiger une phrase de plus faisait ecrire « voir affiche » ou
        // recopier le titre -- du texte qui occupe une place a l'ecran sans rien apprendre.
        @Size(max = 500, message = "La description courte ne peut pas dépasser 500 caractères.")
        String shortDescription,

        @Size(max = 255, message = "Le lieu ne peut pas dépasser 255 caractères.")
        String location,

        @Size(max = 2048, message = "L'URL de couverture ne peut pas dépasser 2048 caractères.")
        String coverImageUrl,

        @NotNull(message = "La source de l'événement est requise.")
        EventSource source,

        @NotNull(message = "La date de début est requise.")
        Instant startsAt,

        Instant endsAt,
        Instant publishedAt,
        Boolean registrationEnabled,
        Integer capacity,
        Boolean waitlistEnabled,
        UUID articleId,
        Set<UUID> categoryIds,
        Set<UUID> targetDepartmentIds
) {
}
