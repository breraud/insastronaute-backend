package fr.insastronaute.webapi.proof.service;

import java.io.InputStream;

public record ProofMediaStream(String contentType, long objectSize, long start, long end, boolean partial, InputStream inputStream) {
    public static ProofMediaStream full(String contentType, long size, InputStream inputStream) {
        return new ProofMediaStream(contentType, size, 0, Math.max(0, size - 1), false, inputStream);
    }
    public long contentLength() { return end - start + 1; }
}
