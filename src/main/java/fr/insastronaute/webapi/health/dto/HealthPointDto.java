package fr.insastronaute.webapi.health.dto;

import java.time.Instant;

/**
 * Un point de courbe, tel que le panel le trace.
 *
 * @param requestsPerSecond taux calculé depuis le point précédent ; null pour le premier
 *                          point d'une série, et après un redémarrage
 * @param errorsPerSecond   idem, sur les seules réponses 5xx
 * @param storageFreeBytes  le disque des fichiers (SeaweedFS) ; null tant qu'aucun
 *                          chemin n'est configure
 */
public record HealthPointDto(
        Instant at,
        Double requestsPerSecond,
        Double errorsPerSecond,
        Double p95Millis,
        Long heapUsedBytes,
        Long heapMaxBytes,
        Integer dbPoolActive,
        Integer dbPoolMax,
        Long diskFreeBytes,
        Long diskTotalBytes,
        Long storageFreeBytes,
        Long storageTotalBytes,
        Integer gateQueueSize
) {
}
