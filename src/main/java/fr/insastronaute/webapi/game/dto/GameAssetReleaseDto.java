package fr.insastronaute.webapi.game.dto;

import java.time.Instant;

public record GameAssetReleaseDto(
        String version,
        String baseUrl,
        String indexUrl,
        String loaderUrl,
        String manifestUrl,
        int fileCount,
        long totalSizeBytes,
        Instant publishedAt
) {
}
