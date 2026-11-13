package fr.insastronaute.webapi.place.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Avec qui partager un plan. Seuls des amis : un plan n'est pas un lien public. */
public record PlaceBlueprintShareDto(@NotEmpty List<UUID> userIds) {}
