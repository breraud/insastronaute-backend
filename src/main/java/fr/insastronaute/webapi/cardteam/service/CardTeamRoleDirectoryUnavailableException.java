package fr.insastronaute.webapi.cardteam.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CardTeamRoleDirectoryUnavailableException extends RuntimeException {
    public CardTeamRoleDirectoryUnavailableException() {
        super("L'annuaire des rôles est temporairement indisponible.");
    }
}
