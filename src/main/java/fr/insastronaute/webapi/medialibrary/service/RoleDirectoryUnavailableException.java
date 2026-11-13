package fr.insastronaute.webapi.medialibrary.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Keycloak n'a jamais repondu : on refuse de designer plutot que de deviner. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class RoleDirectoryUnavailableException extends RuntimeException {
    public RoleDirectoryUnavailableException() {
        super("L'annuaire des rôles est temporairement indisponible.");
    }
}
