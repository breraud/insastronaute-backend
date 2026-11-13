package fr.insastronaute.webapi.auth.dto;

public record SessionResponse(
        boolean authenticated,
        SessionUserResponse user,
        SiteStatusDto siteStatus,
        OnboardingSessionDto onboarding
) {}