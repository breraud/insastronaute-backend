package fr.insastronaute.webapi.cardteam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CardTeamMergeRequest(@NotNull UUID sourceTeamId) {}
