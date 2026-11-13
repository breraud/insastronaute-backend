package fr.insastronaute.webapi.treasurehunt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AdminPathInitializationRequest(@NotNull @Size(min = 6, max = 6) List<@NotNull UUID> departmentIds) { }
