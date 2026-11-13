package fr.insastronaute.webapi.card.dto;

import java.util.UUID;

public record CardDeckTeamDto(UUID id, String name, int memberCount, int maxMembers) {
}
