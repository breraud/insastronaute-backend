package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ChallengeVisibilityDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeVisibilityRequest;
import fr.insastronaute.webapi.challenge.service.ChallengeVisibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/challenges/visibility")
@Tag(name = "Admin Challenges")
public class AdminChallengeVisibilityController {

    private final ChallengeVisibilityService visibilityService;

    public AdminChallengeVisibilityController(ChallengeVisibilityService visibilityService) {
        this.visibilityService = visibilityService;
    }

    @GetMapping
    @Operation(summary = "Are challenges hidden from players")
    public ChallengeVisibilityDto getVisibility() {
        return new ChallengeVisibilityDto(visibilityService.hidden());
    }

    @PutMapping
    @Operation(
            summary = "Hide or show every challenge",
            description = "Draws a curtain over the whole challenge page. No challenge is edited: opening it again "
                    + "restores exactly what was there, without having to remember which ones were off."
    )
    public ChallengeVisibilityDto setVisibility(@Valid @RequestBody ChallengeVisibilityRequest request) {
        return new ChallengeVisibilityDto(visibilityService.setHidden(Boolean.TRUE.equals(request.hidden())));
    }
}
