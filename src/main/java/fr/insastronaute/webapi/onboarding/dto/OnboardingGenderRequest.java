package fr.insastronaute.webapi.onboarding.dto;

import fr.insastronaute.webapi.user.domain.Gender;
import jakarta.validation.constraints.NotNull;

public record OnboardingGenderRequest(@NotNull Gender gender) {}
