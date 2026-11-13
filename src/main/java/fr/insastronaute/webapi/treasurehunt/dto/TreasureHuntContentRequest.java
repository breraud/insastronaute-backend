package fr.insastronaute.webapi.treasurehunt.dto;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TreasureHuntContentRequest(@NotNull TreasureHuntContentType type, String text, UUID articleId) { }
