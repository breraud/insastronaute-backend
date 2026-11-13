package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChallengeCreateDto(
        @NotBlank String title,
        String description,
        @NotNull
        @PositiveOrZero(message = "La récompense en points doit être supérieure ou égale à 0.")
        Integer pointsReward,
        @Size(max = 50, message = "Le nom de catégorie doit contenir au plus 50 caractères.")
        String category,
        // Toutes les catégories, la principale en tête. Absente, `category` fait
        // foi : un client qui ne connaît pas encore ce champ continue de marcher.
        List<String> categories,
        Instant startDate,
        Instant endDate,
        // Date de parution. Nulle = paru. Ne borne pas la soumission.
        Instant publishedAt,
        @PositiveOrZero(message = "Le quota global doit être supérieur ou égal à 0.")
        Integer globalQuota,
        @NotBlank
        @Pattern(regexp = "^(ADMIN|AUTO)$", message = "Le mode de validation doit être ADMIN ou AUTO.")
        String validationMode,
        // Ce que le défi attend comme preuve. Absent = MEDIA, ce qu'attendaient
        // tous les défis avant que le champ existe.
        @Pattern(regexp = "^(MEDIA|TEXT)$", message = "Le type de preuve doit être MEDIA ou TEXT.")
        String proofType,
        String autoChallengeType,
        // Une table et non un JsonNode : la couche HTTP est en Jackson 3, qui ne
        // sait pas construire l'arbre de Jackson 2. Voir JsonPayloads.
        Map<String, Object> autoChallengeParameters,
        Boolean sensitive,
        /** Un defi de promo : ses points vont au departement, une seule fois. */
        Boolean promo,
        @Positive(message = "La taille minimale du groupe doit être supérieure ou égale à 1.")
        Integer minGroupSize,
        @Positive(message = "La taille maximale du groupe doit être supérieure ou égale à 1.")
        Integer maxGroupSize,
        List<UUID> targetDepartmentIds, // Si vide concerne tout le monde
        List<UUID> prerequisiteChallengeIds,
        /** Ce defi accepte-t-il qu'un malus fasse descendre la recompense sous zero ? */
        Boolean allowNegativeReward
) {}
