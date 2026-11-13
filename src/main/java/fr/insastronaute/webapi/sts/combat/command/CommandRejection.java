package fr.insastronaute.webapi.sts.combat.command;

import java.util.Objects;

public record CommandRejection(RejectionCode code, String message) {

    public CommandRejection {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
