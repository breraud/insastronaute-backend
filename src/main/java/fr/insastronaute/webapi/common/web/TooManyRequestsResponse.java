package fr.insastronaute.webapi.common.web;

import java.time.OffsetDateTime;

public record TooManyRequestsResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        long remainingCooldownMs,
        String code
) {
}
