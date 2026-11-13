package fr.insastronaute.webapi.event.dto;

import java.util.UUID;

/** One answer as its author sees it: the option chosen, or the text written. */
public record EventAnswerDto(UUID questionId, UUID optionId, String textValue) {
}
