package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventQuestionType;

import java.util.List;
import java.util.UUID;

public record EventQuestionDto(
        UUID id,
        String label,
        EventQuestionType type,
        boolean required,
        int position,
        List<EventQuestionOptionDto> options
) {
}
