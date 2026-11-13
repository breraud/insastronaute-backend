package fr.insastronaute.webapi.game.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public final class GameAssetChunker {

    public static final int CHUNK_SIZE_BYTES = 2 * 1024 * 1024;

    public boolean shouldChunk(long sizeBytes) {
        return sizeBytes > CHUNK_SIZE_BYTES;
    }

    public ChunkedAsset chunk(String relativePath, byte[] bytes) {
        List<Chunk> chunks = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += CHUNK_SIZE_BYTES) {
            byte[] chunkBytes = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK_SIZE_BYTES));
            chunks.add(new Chunk(chunks.size(), chunkBytes, sha256(chunkBytes)));
        }
        return new ChunkedAsset(relativePath, bytes.length, sha256(bytes), List.copyOf(chunks));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record ChunkedAsset(String relativePath, long sizeBytes, String sha256, List<Chunk> chunks) {
    }

    public record Chunk(int index, byte[] bytes, String sha256) {
    }
}
