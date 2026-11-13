package fr.insastronaute.webapi.proof.web;

import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Les en-tetes de toute reponse qui porte un fichier de preuve.
 *
 * <p>Ecrits une fois pour la moderation et la Mediatheque : rien ne se met en cache, ni
 * dans le navigateur ni chez un intermediaire, rien ne s'interprete, et le fichier ne
 * peut rien executer.</p>
 */
public final class ProofMediaHeaders {

    private ProofMediaHeaders() {
    }

    public static HttpHeaders forMedia(ProofMediaStream media) {
        HttpHeaders headers = security();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(MediaType.parseMediaType(media.contentType()));
        headers.setContentLength(media.contentLength());
        if (media.partial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(media.start(), media.end(), media.objectSize()));
        }
        return headers;
    }

    public static HttpHeaders forBytes(String contentType, long length) {
        HttpHeaders headers = security();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(length);
        return headers;
    }

    /** Pour les reponses ecrites a la main dans la servlet, sans ResponseEntity. */
    public static void writeTo(HttpHeaders headers, HttpServletResponse response) {
        headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        if (headers.getContentType() != null) {
            response.setContentType(headers.getContentType().toString());
        }
    }

    private static HttpHeaders security() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, private, max-age=0");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        return headers;
    }
}
