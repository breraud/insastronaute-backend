package fr.insastronaute.webapi.treasurehunt.dto;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import jakarta.validation.constraints.NotBlank;

public record AdminCheckpointRequest(@NotBlank String code, TreasureHuntInputTool inputTool) { }
