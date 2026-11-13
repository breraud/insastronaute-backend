package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;

import java.util.UUID;

public record EventFriendDto(
        UUID userId,
        String displayName,
        UUID departmentId,
        EventRegistrationStatus status
) {
}
