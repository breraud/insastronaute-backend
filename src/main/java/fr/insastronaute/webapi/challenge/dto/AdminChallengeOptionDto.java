package fr.insastronaute.webapi.challenge.dto;

import java.util.UUID;

public record AdminChallengeOptionDto(UUID id, String title, String description) {
}
