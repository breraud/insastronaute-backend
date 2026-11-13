package fr.insastronaute.webapi.onboarding.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.onboarding.dto.OnboardingGenderRequest;
import fr.insastronaute.webapi.onboarding.dto.OnboardingStateDto;
import fr.insastronaute.webapi.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@Tag(name = "Onboarding")
public class OnboardingController {

    private final UserSynchronizationService synchronizationService;
    private final OnboardingService onboardingService;

    public OnboardingController(UserSynchronizationService synchronizationService, OnboardingService onboardingService) {
        this.synchronizationService = synchronizationService;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/state")
    public OnboardingStateDto getState(@AuthenticationPrincipal OidcUser oidcUser) {
        return onboardingService.state(synchronizationService.syncWithKeycloak(oidcUser));
    }

    @PutMapping("/gender")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void chooseGender(@AuthenticationPrincipal OidcUser oidcUser,
                             @Valid @RequestBody OnboardingGenderRequest request) {
        onboardingService.chooseGender(synchronizationService.syncWithKeycloak(oidcUser), request.gender());
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal OidcUser oidcUser) {
        onboardingService.submit(synchronizationService.syncWithKeycloak(oidcUser));
    }
}
