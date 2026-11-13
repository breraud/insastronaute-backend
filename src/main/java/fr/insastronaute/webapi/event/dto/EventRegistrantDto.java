package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;

import java.time.Instant;
import java.util.UUID;

public record EventRegistrantDto(
        UUID registrationId,
        EventRegistrationStatus status,
        Instant registeredAt,
        UUID userId,
        String email,
        String displayName,
        UUID departmentId,
        /**
         * Cet inscrit suit une filiere par apprentissage.
         *
         * <p>Son departement reste celui de sa filiere : sans cette marque, un tirage de
         * la liste d'un evenement PERF montre une salle de GC et de MECA, et ne dit plus
         * a qui il s'adresse.</p>
         */
        boolean perf
) {
}
