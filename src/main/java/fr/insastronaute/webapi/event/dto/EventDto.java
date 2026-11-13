package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;
import fr.insastronaute.webapi.event.domain.EventSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDto(
        UUID id,
        String title,
        String shortDescription,
        String location,
        String coverImageUrl,
        EventSource source,
        Instant startsAt,
        Instant endsAt,
        Instant publishedAt,
        Boolean registrationEnabled,
        Integer capacity,
        Boolean waitlistEnabled,
        EventArticleDto article,
        List<EventCategoryDto> categories,
        List<EventDepartmentDto> targetDepartments,
        long registeredCount,
        long waitlistedCount,
        EventRegistrationStatus currentUserRegistrationStatus,
        List<EventFriendDto> registeredFriends,
        /** L'événement pose-t-il des questions à l'inscription ? */
        boolean hasQuestions,
        Instant createdAt,
        Instant updatedAt
) {
}
