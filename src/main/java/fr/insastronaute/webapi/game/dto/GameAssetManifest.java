package fr.insastronaute.webapi.game.dto;

import java.util.List;
import java.util.Map;

public record GameAssetManifest(
        int formatVersion,
        long chunkSizeBytes,
        Map<String, Asset> assets
) {
    public record Asset(
            String encoding,
            long sizeBytes,
            String sha256,
            String fallbackUrl,
            List<Chunk> chunks
    ) {
    }

    public record Chunk(
            String url,
            long sizeBytes,
            String sha256
    ) {
    }
}
