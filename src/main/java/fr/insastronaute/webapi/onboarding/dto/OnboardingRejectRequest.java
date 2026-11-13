package fr.insastronaute.webapi.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardingRejectRequest(@NotBlank @Size(max = 500) String reason) {}
