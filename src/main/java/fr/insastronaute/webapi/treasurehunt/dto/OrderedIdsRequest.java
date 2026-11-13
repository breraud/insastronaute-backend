package fr.insastronaute.webapi.treasurehunt.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record OrderedIdsRequest(@NotEmpty List<@NotNull UUID> ids) { }
