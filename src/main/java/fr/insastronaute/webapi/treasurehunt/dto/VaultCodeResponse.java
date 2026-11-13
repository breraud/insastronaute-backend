package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.UUID;

/** Un code gagne, sans la position de sa voie -- voir PlayerPathProgressResponse. */
public record VaultCodeResponse(UUID pathId, String code) { }
