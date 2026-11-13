package fr.insastronaute.webapi.treasurehunt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminTreasureHuntPathUpdateRequest(@NotBlank @Size(max = 120) String name) { }
