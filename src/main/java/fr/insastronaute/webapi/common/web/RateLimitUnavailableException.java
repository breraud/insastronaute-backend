package fr.insastronaute.webapi.common.web;

public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException() {
        super("Le contrôle de débit est temporairement indisponible.");
    }

    public RateLimitUnavailableException(Throwable cause) {
        super("Le contrôle de débit est temporairement indisponible.", cause);
    }
}
