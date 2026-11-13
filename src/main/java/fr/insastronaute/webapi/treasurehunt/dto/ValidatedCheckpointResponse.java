package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;
import java.util.UUID;

/** Une etape deja validee par le joueur, avec ce qu'elle lui avait remis. */
public record ValidatedCheckpointResponse(UUID pathId, int stepNumber, List<TreasureHuntContentResponse> contents) { }
