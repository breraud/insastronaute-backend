package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.UUID;

public record AdminTreasureHuntPathResponse(UUID id, UUID departmentId, Integer position, String name) { }
