package fr.insastronaute.webapi.event.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Optional body on registration. An event with no questions takes no body at
 * all, which is why every field is nullable.
 */
public record EventRegistrationRequest(@Valid List<EventAnswerDto> answers) {
}
