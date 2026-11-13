package fr.insastronaute.webapi.treasurehunt.dto;

import java.time.Instant;

public record TreasureHuntValidatedStepResponse(int stepNumber, Instant validatedAt) { }
