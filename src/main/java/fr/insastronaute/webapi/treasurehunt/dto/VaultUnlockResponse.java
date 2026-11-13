package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;

public record VaultUnlockResponse(List<TreasureHuntContentResponse> contents) { }
