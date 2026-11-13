package fr.insastronaute.webapi.onboarding.dto;

import fr.insastronaute.webapi.user.domain.Gender;

import java.time.Instant;
import java.util.UUID;

public record PendingOnboardingDto(
        UUID userId,
        String fullName,
        String email,
        String department,
        Gender gender,
        Instant submittedAt,
        // Toujours null pour un compte encore en attente : seul le refus le
        // renseigne. Reutilise le meme DTO plutot que d'en creer un second,
        // /pending et /rejected ne divergeant que par ce champ.
        String rejectionReason
) {}
