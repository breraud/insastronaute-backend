package fr.insastronaute.webapi.challenge.dto;

import java.time.Instant;
import java.util.List;

public record AdminSubmissionAuditDto(
        Decision decision,
        List<MediaConsultation> mediaConsultations
) {
    public record Decision(String status, String actorName, Instant occurredAt) {}

    public record MediaConsultation(String actorName, int mediaIndex, Instant occurredAt, String channel) {
        /** Avant la Mediatheque, toute lecture venait de la moderation. */
        public MediaConsultation(String actorName, int mediaIndex, Instant occurredAt) {
            this(actorName, mediaIndex, occurredAt, "MODERATION");
        }
    }
}
