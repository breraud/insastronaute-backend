package fr.insastronaute.webapi.challenge.audit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ModerationAuditUnavailableException extends RuntimeException {
    public ModerationAuditUnavailableException(Throwable cause) {
        super("La journalisation de sécurité est temporairement indisponible.", cause);
    }
}
