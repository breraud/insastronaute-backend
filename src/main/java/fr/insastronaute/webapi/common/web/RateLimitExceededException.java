package fr.insastronaute.webapi.common.web;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterMs;

    public RateLimitExceededException(long retryAfterMs) {
        super("Trop de requêtes. Veuillez réessayer plus tard.");
        this.retryAfterMs = retryAfterMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
