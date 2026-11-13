package fr.insastronaute.webapi.treasurehunt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VaultUnlockRequest(@Size(min = 6, max = 6) List<@NotBlank String> codes) { }
