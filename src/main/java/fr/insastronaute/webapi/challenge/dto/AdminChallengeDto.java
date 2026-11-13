package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminChallengeDto(
        UUID id,
        String title,
        String description,
        Integer pointsReward,
        boolean rewardShared,
        String category,
        /** Toutes les catégories du défi, la principale en tête. */
        List<String> categories,
        Instant startDate,
        Instant endDate,
        Instant publishedAt,
        Integer globalQuota,
        String validationMode,
        /** Ce que le défi attend comme preuve : MEDIA ou TEXT. */
        String proofType,
        String autoChallengeType,
        // Voir ChallengeCreateDto : le JSON libre traverse la frontière HTTP en
        // table, jamais en arbre Jackson 2.
        Map<String, Object> autoChallengeParameters,
        Boolean sensitive,
        /** Un defi de promo : ses points vont au departement, une seule fois. */
        boolean promo,
        Integer minGroupSize,
        Integer maxGroupSize,
        List<UUID> targetDepartmentIds,
        List<UUID> prerequisiteChallengeIds,
        Instant createdAt,
        Instant updatedAt,
        /** Ce defi accepte-t-il qu'un malus fasse descendre la recompense sous zero ? */
        Boolean allowNegativeReward
) {
}
