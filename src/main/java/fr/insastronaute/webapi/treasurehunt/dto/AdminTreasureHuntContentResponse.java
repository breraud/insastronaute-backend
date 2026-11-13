package fr.insastronaute.webapi.treasurehunt.dto;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import java.util.UUID;

public record AdminTreasureHuntContentResponse(UUID id, Integer position, TreasureHuntContentType type,
                                                String text, UUID articleId, boolean hasMedia) { }
