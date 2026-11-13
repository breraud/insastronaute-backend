package fr.insastronaute.webapi.event.dto;

import fr.insastronaute.webapi.event.domain.EventQuestionType;

import java.util.List;
import java.util.UUID;

public record EventQuestionResultDto(
        UUID questionId,
        String label,
        EventQuestionType type,
        boolean required,
        long answeredCount,
        /** Registrants who never answered. Non-zero after a question is added late. */
        long unansweredCount,
        List<EventQuestionOptionResultDto> options,
        List<EventTextAnswerDto> textAnswers
) {
    public record EventQuestionOptionResultDto(UUID optionId, String label, long count, int percentage) {
    }

    public record EventTextAnswerDto(String displayName, String value) {
    }
}
