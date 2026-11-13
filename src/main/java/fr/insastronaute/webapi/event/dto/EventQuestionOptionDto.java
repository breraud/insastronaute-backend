package fr.insastronaute.webapi.event.dto;

import java.util.UUID;

public record EventQuestionOptionDto(UUID id, String label, int position) {
}
