package fr.insastronaute.webapi.event.dto;

import java.util.List;
import java.util.UUID;

public record EventQuestionnaireResultsDto(
        UUID eventId,
        String title,
        long registrationCount,
        List<EventQuestionResultDto> questions
) {
}
