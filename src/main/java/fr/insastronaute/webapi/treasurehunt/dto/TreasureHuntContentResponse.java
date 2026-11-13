package fr.insastronaute.webapi.treasurehunt.dto;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import java.util.UUID;

public record TreasureHuntContentResponse(TreasureHuntContentType type, String text, String mediaUrl, UUID articleId) { }
