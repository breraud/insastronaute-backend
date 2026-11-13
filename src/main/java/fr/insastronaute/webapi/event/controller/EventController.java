package fr.insastronaute.webapi.event.controller;

import fr.insastronaute.webapi.event.dto.EventDto;
import fr.insastronaute.webapi.event.dto.EventRegistrationDto;
import fr.insastronaute.webapi.event.dto.EventAnswerDto;
import fr.insastronaute.webapi.event.dto.EventQuestionDto;
import fr.insastronaute.webapi.event.dto.EventRegistrationRequest;
import fr.insastronaute.webapi.event.service.EventQuestionnaireService;
import fr.insastronaute.webapi.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;
    private final EventQuestionnaireService questionnaireService;

    public EventController(EventService eventService,
            EventQuestionnaireService questionnaireService) {
        this.eventService = eventService;
        this.questionnaireService = questionnaireService;
    }

    @GetMapping
    @Operation(summary = "List visible events", description = "Returns published events visible to the authenticated player.")
    public List<EventDto> listEvents(@AuthenticationPrincipal OidcUser oidcUser) {
        return eventService.listPlayerEvents(oidcUser.getSubject());
    }

    @GetMapping("/registrations")
    @Operation(summary = "List my event registrations", description = "Returns the authenticated player's event registrations and waitlist entries.")
    public List<EventRegistrationDto> getMyRegistrations(@AuthenticationPrincipal OidcUser oidcUser) {
        return eventService.getMyRegistrations(oidcUser.getSubject());
    }

    @GetMapping("/waitlist")
    @Operation(summary = "List my waitlist entries", description = "Returns the authenticated player's waitlisted event registrations.")
    public List<EventRegistrationDto> getMyWaitlist(@AuthenticationPrincipal OidcUser oidcUser) {
        return eventService.getMyWaitlist(oidcUser.getSubject());
    }

    @PostMapping("/{eventId}/registrations")
    @Operation(summary = "Register for an event", description = "Registers the authenticated player or places them on the waitlist when the quota is full.")
    public EventRegistrationDto register(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody(required = false) EventRegistrationRequest request
    ) {
        return eventService.register(
                eventId,
                oidcUser.getSubject(),
                request == null ? null : request.answers()
        );
    }

    @GetMapping("/{eventId}/questions")
    @Operation(
            summary = "List an event's questions",
            description = "Returns the questions to answer when registering. Empty when the event asks nothing."
    )
    public List<EventQuestionDto> getQuestions(@PathVariable UUID eventId) {
        return questionnaireService.getQuestions(eventId);
    }

    @GetMapping("/{eventId}/registrations/answers")
    @Operation(summary = "Read my answers", description = "Returns the answers the authenticated player gave for this event.")
    public List<EventAnswerDto> getMyAnswers(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return eventService.answersFor(eventId, oidcUser.getSubject());
    }

    @PutMapping("/{eventId}/registrations/answers")
    @Operation(
            summary = "Revise my answers",
            description = "Updates the answers without giving up the seat, while registration is still open."
    )
    public List<EventAnswerDto> updateMyAnswers(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody EventRegistrationRequest request
    ) {
        return eventService.updateAnswers(eventId, oidcUser.getSubject(), request.answers());
    }

    @DeleteMapping("/{eventId}/registrations")
    @Operation(summary = "Unregister from an event", description = "Deletes the authenticated player's registration and promotes the first waitlisted player when possible.")
    public void unregister(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        eventService.unregister(eventId, oidcUser.getSubject());
    }
}
