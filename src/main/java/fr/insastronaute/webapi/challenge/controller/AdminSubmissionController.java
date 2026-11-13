package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.PendingSubmissionDto;
import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionDetailDto;
import fr.insastronaute.webapi.challenge.dto.ModerationParticipantCandidateDto;
import fr.insastronaute.webapi.challenge.dto.ModerationParticipantCreateDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionBonusAdjustmentDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionDecisionDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionGraceDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageCreateDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessagesEnabledDto;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderation/submissions")
@Tag(name = "Moderation")
public class AdminSubmissionController {

    private final ChallengeService challengeService;

    public AdminSubmissionController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @ExceptionHandler(fr.insastronaute.webapi.proof.service.ProofRangeNotSatisfiableException.class)
    public ResponseEntity<Void> invalidProofRange(fr.insastronaute.webapi.proof.service.ProofRangeNotSatisfiableException exception) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + exception.objectSize());
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, private, max-age=0");
        return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    @GetMapping("/pending")
    @Operation(summary = "List pending submissions", description = "Returns submissions the authenticated moderator is allowed to review.")
    public List<PendingSubmissionDto> getPending(@AuthenticationPrincipal OidcUser oidcUser) {
        return challengeService.getPendingSubmissions(oidcUser.getSubject(), authorities(oidcUser));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a moderation submission detail", description = "Returns challenge, proof metadata, participants, review state, chat state, and messages for one submission.")
    public ModerationSubmissionDetailDto getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.getModerationSubmissionDetail(id, oidcUser.getSubject(), authorities(oidcUser));
    }

    @GetMapping("/{id}/participant-candidates")
    @Operation(summary = "Search participants for a submission")
    public List<ModerationParticipantCandidateDto> searchParticipantCandidates(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "") String query,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.searchModerationParticipantCandidates(
                id, query, oidcUser.getSubject(), authorities(oidcUser));
    }

    @PostMapping("/{id}/participants")
    @Operation(summary = "Add a participant to a submission under review")
    public SubmissionParticipantDto addParticipant(
            @PathVariable UUID id,
            @Valid @RequestBody ModerationParticipantCreateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.addModerationParticipant(
                id, dto.userId(), oidcUser.getSubject(), authorities(oidcUser));
    }

    @PostMapping("/{id}/decide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Decide a submission", description = "Accepts or rejects a pending submission and records an optional point adjustment; adjusted validations require a moderation comment.")
    public void decide(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionDecisionDto dto,
            @AuthenticationPrincipal OidcUser oidcUser) {

        challengeService.decideSubmission(
                id,
                oidcUser.getSubject(),
                authorities(oidcUser),
                dto.status(),
                dto.moderationComment(),
                dto.bonusPoints()
        );
    }

    @PostMapping("/{id}/bonus-points")
    @Operation(
            summary = "Adjust the bonus points of a validated submission",
            description = "Changes the discretionary points of an already validated proof, without touching its status. A reason is required: it reaches the player and the admin journal."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjustBonusPoints(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionBonusAdjustmentDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        challengeService.adjustValidatedSubmissionBonusPoints(
                id,
                oidcUser.getSubject(),
                authorities(oidcUser),
                dto.bonusPoints(),
                dto.reason()
        );
    }

    @PostMapping("/{id}/grace")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Grant a grace on a rejected submission",
            description = "Reopens a closed challenge for the members of a rejected submission, for a bounded delay, and tells them.")
    public void grantGrace(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SubmissionGraceDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        challengeService.grantSubmissionGrace(
                id,
                oidcUser.getSubject(),
                dto == null ? null : dto.hours(),
                dto == null ? null : dto.reason()
        );
    }

    @PostMapping("/{id}/review-lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lock a submission for review", description = "Claims a pending submission so other moderators can see that it is being reviewed.")
    public void lockForReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        challengeService.lockSubmissionForReview(id, oidcUser.getSubject(), authorities(oidcUser));
    }

    @DeleteMapping("/{id}/review-lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Release a review lock", description = "Releases the authenticated moderator's review lock for a submission.")
    public void releaseReviewLock(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        challengeService.releaseSubmissionReviewLock(id, oidcUser.getSubject(), authorities(oidcUser));
    }

    @PutMapping("/{id}/review-lock/heartbeat")
    @Operation(summary = "Renew a review lock", description = "Renews the authenticated moderator's bounded review lease.")
    public java.util.Map<String, java.time.Instant> heartbeatReviewLock(
            @PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return java.util.Map.of("expiresAt", challengeService.heartbeatSubmissionReviewLock(
                id, oidcUser.getSubject(), authorities(oidcUser)));
    }

    @GetMapping("/{id}/proofs/{index}/content")
    @Operation(summary = "Stream submission proof media", description = "Streams private proof media after revalidating moderation access and review-lock ownership.")
    public ResponseEntity<StreamingResponseBody> getProofContent(
            @PathVariable UUID id,
            @PathVariable int index,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        var media = challengeService.openModerationProofMedia(id, index, oidcUser.getSubject(), authorities(oidcUser), range);
        StreamingResponseBody body = output -> {
            try (var input = media.inputStream()) {
                input.transferTo(output);
            }
        };
        HttpHeaders headers = fr.insastronaute.webapi.proof.web.ProofMediaHeaders.forMedia(media);
        return new ResponseEntity<>(body, headers, media.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "List submission messages", description = "Returns the discussion for a submission with real author identities for moderation.")
    public List<SubmissionMessageDto> getMessages(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.getModerationSubmissionMessages(id, oidcUser.getSubject(), authorities(oidcUser));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a moderation message", description = "Adds a moderation-team message to a submission discussion.")
    public SubmissionMessageDto createMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionMessageCreateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.createModerationSubmissionMessage(id, oidcUser.getSubject(), authorities(oidcUser), dto);
    }

    @PatchMapping("/{id}/messages/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Enable or disable submission messages", description = "Controls whether players can write in a submission discussion.")
    public void updateMessagesEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionMessagesEnabledDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        challengeService.updateSubmissionMessagesEnabled(id, oidcUser.getSubject(), authorities(oidcUser), dto.enabled());
    }

    private Set<String> authorities(OidcUser oidcUser) {
        return oidcUser.getAuthorities().stream()
                .map(authority -> authority == null ? null : authority.getAuthority())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
