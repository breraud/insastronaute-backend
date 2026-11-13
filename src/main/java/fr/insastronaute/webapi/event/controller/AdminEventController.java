package fr.insastronaute.webapi.event.controller;

import fr.insastronaute.webapi.event.dto.EventDto;
import fr.insastronaute.webapi.event.dto.EventRegistrantDto;
import fr.insastronaute.webapi.event.dto.EventRequest;
import fr.insastronaute.webapi.event.dto.EventQuestionDto;
import fr.insastronaute.webapi.event.dto.EventQuestionnaireRequest;
import fr.insastronaute.webapi.event.dto.EventQuestionnaireResultsDto;
import fr.insastronaute.webapi.event.service.EventQuestionnaireService;
import fr.insastronaute.webapi.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import fr.insastronaute.webapi.event.dto.EventStaffRegistrationDto;
import fr.insastronaute.webapi.event.dto.EventStaffRegistrationRequest;
import fr.insastronaute.webapi.event.service.EventSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/events")
@Tag(name = "Admin Events")
public class AdminEventController {

    private final EventService eventService;
    private final EventSettingsService eventSettings;
    private final EventQuestionnaireService questionnaireService;

    public AdminEventController(EventService eventService,
            EventQuestionnaireService questionnaireService,
            EventSettingsService eventSettings) {
        this.eventService = eventService;
        this.questionnaireService = questionnaireService;
        this.eventSettings = eventSettings;
    }

    @GetMapping
    @Operation(summary = "List events", description = "Returns every event for admin management.")
    public List<EventDto> listEvents() {
        return eventService.listAdminEvents();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an event", description = "Creates an event with publication, quota, category, department, and article settings.")
    public EventDto createEvent(@Valid @RequestBody EventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get an event", description = "Returns an event for admin editing.")
    public EventDto getEvent(@PathVariable UUID eventId) {
        return eventService.getAdminEvent(eventId);
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Update an event", description = "Replaces editable event settings.")
    public EventDto updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request
    ) {
        return eventService.updateEvent(eventId, request);
    }

    @PostMapping(path = "/{eventId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload event cover", description = "Uploads or replaces the public cover image for the event.")
    public EventDto uploadCover(
            @PathVariable UUID eventId,
            @RequestParam MultipartFile file
    ) {
        return eventService.uploadCover(eventId, file);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an event", description = "Deletes the event and its registrations.")
    public void deleteEvent(@PathVariable UUID eventId) {
        eventService.deleteEvent(eventId);
    }

    @GetMapping("/{eventId}/registrants")
    @Operation(summary = "List event registrants", description = "Returns registered and waitlisted users for an event.")
    public List<EventRegistrantDto> getRegistrants(@PathVariable UUID eventId) {
        return eventService.getRegistrants(eventId);
    }

    @GetMapping("/{eventId}/registrations/out-of-scope")
    @Operation(
            summary = "List registrants outside the target departments",
            description = "Returns the registrants whose department is no longer targeted by the event. "
                    + "Narrowing the target departments never unregisters anyone: these seats still count "
                    + "against the quota while their holders can no longer see the event."
    )
    public List<EventRegistrantDto> getOutOfScopeRegistrants(@PathVariable UUID eventId) {
        return eventService.getOutOfScopeRegistrants(eventId);
    }

    @DeleteMapping("/{eventId}/registrations/out-of-scope")
    @Operation(
            summary = "Unregister everyone outside the target departments",
            description = "Deletes those registrations, notifies each player, and promotes the waitlist for every "
                    + "seat freed. Deliberately a separate call: it destroys registrations and reshuffles the "
                    + "waitlist, which no checkbox should trigger in passing."
    )
    public List<EventRegistrantDto> removeOutOfScopeRegistrants(@PathVariable UUID eventId) {
        return eventService.removeOutOfScopeRegistrants(eventId);
    }

    /**
     * Reserve aux administrateurs, plus etroit que le reste de cet ecran : retirer une
     * inscription prend une place a quelqu'un et en donne une a un autre. Ce n'est pas le
     * meme geste que modifier un evenement.
     */
    @DeleteMapping("/{eventId}/registrations/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Remove one registration by hand",
            description = "Deletes that person's registration, notifies them, and promotes the first waitlisted "
                    + "player when the freed seat was a confirmed one. Removing someone from the waitlist frees "
                    + "nothing and promotes nobody."
    )
    public EventRegistrantDto removeRegistration(@PathVariable UUID eventId, @PathVariable UUID userId) {
        return eventService.removeRegistration(eventId, userId);
    }

    @GetMapping("/settings/staff-registration")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Is staff allowed to register for events")
    public EventStaffRegistrationDto getStaffRegistration() {
        return new EventStaffRegistrationDto(eventSettings.staffRegistrationBlocked());
    }

    @PutMapping("/settings/staff-registration")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Allow or forbid staff from registering for events",
            description = "Blocks staff accounts from taking a seat on any event. Nothing already registered is "
                    + "touched: existing registrations stay, and are removed one by one if that is what is wanted."
    )
    public EventStaffRegistrationDto setStaffRegistration(
            @Valid @RequestBody EventStaffRegistrationRequest request
    ) {
        return new EventStaffRegistrationDto(
                eventSettings.setStaffRegistrationBlocked(Boolean.TRUE.equals(request.blocked())));
    }

    @GetMapping("/{eventId}/waitlist")
    @Operation(summary = "List event waitlist", description = "Returns waitlisted users for an event.")
    public List<EventRegistrantDto> getWaitlist(@PathVariable UUID eventId) {
        return eventService.getWaitlist(eventId);
    }

    @GetMapping("/{eventId}/questions")
    @Operation(summary = "Read an event's questionnaire")
    public List<EventQuestionDto> getQuestions(@PathVariable UUID eventId) {
        return questionnaireService.getQuestions(eventId);
    }

    @PutMapping("/{eventId}/questions")
    @Operation(
            summary = "Replace an event's questionnaire",
            description = "Questions and options sent with their id are updated in place, so renaming one keeps the answers "
                    + "already collected. Anything left out is removed along with its answers."
    )
    public List<EventQuestionDto> replaceQuestions(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventQuestionnaireRequest request
    ) {
        return questionnaireService.replaceQuestionnaire(eventId, request);
    }

    @GetMapping("/{eventId}/questionnaire")
    @Operation(
            summary = "Read questionnaire results",
            description = "Per question: the tally per option, the free-text answers, and how many registrants never answered."
    )
    public EventQuestionnaireResultsDto getQuestionnaireResults(@PathVariable UUID eventId) {
        return questionnaireService.results(eventId);
    }
}
