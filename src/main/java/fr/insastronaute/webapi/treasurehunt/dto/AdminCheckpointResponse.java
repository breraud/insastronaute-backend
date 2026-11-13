package fr.insastronaute.webapi.treasurehunt.dto;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import java.util.UUID;

public record AdminCheckpointResponse(UUID id, UUID pathId, Integer stepNumber, String code,
        TreasureHuntInputTool inputTool) { }
