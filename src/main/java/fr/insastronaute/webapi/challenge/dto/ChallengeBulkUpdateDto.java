package fr.insastronaute.webapi.challenge.dto;

import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChallengeBulkUpdateDto(
        @NotEmpty(message = "Au moins un défi doit être spécifié.")
        List<UUID> challengeIds,

        boolean updateStartDate,
        Instant startDate,

        boolean updateEndDate,
        Instant endDate,

        boolean updatePublishedAt,
        Instant publishedAt,

        boolean updateCategories,
        List<String> categories,

        /**
         * Effacer les preuves deja tranchees de chaque defi choisi.
         *
         * <p>Irreversible : les fichiers partent du stockage. Les preuves en attente
         * sont epargnees — sans leur photo, elles deviendraient injugeables et
         * bloqueraient la file de moderation.</p>
         *
         * <p>Boxe, et absent vaut non : un panel anterieur a ce reglage n'envoie pas le
         * champ, et un primitif manquant fait echouer la lecture du corps entier — la
         * mise a jour groupee repondrait 400 le temps que les deux cotes soient
         * deployes.</p>
         */
        Boolean purgeProofs
) {}
