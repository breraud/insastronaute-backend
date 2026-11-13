package fr.insastronaute.webapi.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

public record SessionUserResponse(
        UUID id,
        String keycloakSub,
        String email,
        String displayName,
        List<String> roles,
        boolean firstScan,
        // Seule /api/session le calcule ; ailleurs le champ est absent plutot que faux.
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean mediaLibrary
) {
    public SessionUserResponse(UUID id, String keycloakSub, String email, String displayName,
                               List<String> roles, boolean firstScan) {
        this(id, keycloakSub, email, displayName, roles, firstScan, null);
    }
}
