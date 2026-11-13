package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ChallengeProgressDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeSubmissionDto;
import fr.insastronaute.webapi.challenge.dto.PlayerSubmissionDetailDto;
import fr.insastronaute.webapi.challenge.dto.ParticipantEligibilityDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeRecommendationDto;
import fr.insastronaute.webapi.challenge.dto.GroupInviteCodeDto;
import fr.insastronaute.webapi.challenge.dto.GroupInviteCreateDto;
import fr.insastronaute.webapi.challenge.dto.GroupInvitePreviewDto;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageCreateDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantsUpdateDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionResultDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeDepartmentShareDto;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/challenges")
@Tag(name = "Challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @GetMapping
    @Operation(summary = "List available challenges", description = "Returns challenges visible to the authenticated player, including player-specific progress state.")
    public List<PlayerChallengeDto> getMyChallenges(@AuthenticationPrincipal OidcUser oidcUser) {
        return challengeService.getPlayerChallenges(oidcUser.getSubject());
    }

    @GetMapping("/{challengeId}/department-shares")
    @Operation(summary = "Show the coefficient-adjusted department share for a challenge")
    public List<ChallengeDepartmentShareDto> departmentShares(@PathVariable UUID challengeId) {
        return challengeService.getDepartmentShares(challengeId);
    }

    @GetMapping("/department-shares")
    @Operation(summary = "Show coefficient-adjusted department shares for all challenges")
    public Map<UUID, List<ChallengeDepartmentShareDto>> allDepartmentShares() {
        return challengeService.getAllDepartmentShares();
    }

    @GetMapping("/{challengeId}/participants/eligibility")
    @Operation(
            summary = "Check who can be invited on a challenge",
            description = "Returns, for each requested player, whether they can join this challenge and the reason "
                    + "if they cannot — department, prerequisites, or a submission already in flight."
    )
    public List<ParticipantEligibilityDto> participantEligibility(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID challengeId,
            @RequestParam List<UUID> userIds
    ) {
        return challengeService.participantEligibility(oidcUser.getSubject(), challengeId, userIds);
    }

    @GetMapping("/{challengeId}/progress")
    @Operation(
            summary = "Read progress on an automatic challenge",
            description = "Returns how far the player is on a self-validating challenge, or nothing when the "
                    + "challenge is validated by a moderator or its type cannot answer for one player."
    )
    public ResponseEntity<ChallengeProgressDto> progress(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID challengeId
    ) {
        ChallengeProgressDto progress = challengeService.autoChallengeProgress(oidcUser.getSubject(), challengeId);
        return progress == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(progress);
    }

    @GetMapping("/recommendations")
    @Operation(
            summary = "Suggest challenges worth doing next",
            description = "Returns a short list with the reason each one is put forward: closing soon, done by "
                    + "friends, or in a category the player already succeeds in."
    )
    public List<ChallengeRecommendationDto> getRecommendations(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return challengeService.getRecommendations(oidcUser.getSubject(), limit);
    }

    @GetMapping("/favorites")
    @Operation(summary = "List favorite challenges", description = "Returns the authenticated player's favorite challenges.")
    public List<PlayerChallengeDto> getMyFavoriteChallenges(@AuthenticationPrincipal OidcUser oidcUser) {
        return challengeService.getFavoriteChallenges(oidcUser.getSubject());
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Add a challenge favorite", description = "Marks the selected challenge as a favorite for the authenticated player.")
    public void addFavorite(@PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.addFavoriteChallenge(oidcUser.getSubject(), id);
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "Remove a challenge favorite", description = "Removes the selected challenge from the authenticated player's favorites.")
    public void removeFavorite(@PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.removeFavoriteChallenge(oidcUser.getSubject(), id);
    }

    @GetMapping("/submissions/results")
    @Operation(summary = "List submission results", description = "Returns accepted, rejected, and pending submission results for the authenticated player.")
    public List<SubmissionResultDto> getMySubmissionResults(@AuthenticationPrincipal OidcUser oidcUser) {
        return challengeService.getSubmissionResults(oidcUser.getSubject());
    }

    @GetMapping("/submissions/waiting-confirmation")
    @Operation(summary = "List submissions waiting for confirmation", description = "Returns group submissions that are still waiting for every invited participant to accept.")
    public List<SubmissionResultDto> getMySubmissionsWaitingConfirmation(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.getSubmissionsWaitingConfirmation(oidcUser.getSubject());
    }

    @GetMapping("/submissions/pending-validation")
    @Operation(summary = "List submissions pending validation", description = "Returns submissions whose participant confirmations are complete and which are waiting for validation.")
    public List<SubmissionResultDto> getMySubmissionsPendingValidation(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.getSubmissionsPendingValidation(oidcUser.getSubject());
    }

    @GetMapping("/submissions/{id}")
    @Operation(summary = "Get submission detail", description = "Returns a player-visible submission detail with group participant confirmation statuses.")
    public PlayerSubmissionDetailDto getSubmissionDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.getPlayerSubmissionDetail(id, oidcUser.getSubject());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("!hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "Submit a challenge", description = "Creates a challenge submission for the authenticated player, with proof references and optional group participants.")
    public void submit(@PathVariable UUID id,
                       @Valid @RequestBody ChallengeSubmissionDto dto,
                       @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.submitChallenge(oidcUser.getSubject(), id, dto);
    }

    @PostMapping("/group-invites")
    @Operation(
            summary = "Create a QR invite code for a group challenge",
            description = "Returns the code the player shows so that someone next to them joins the team by scanning it. Regenerating extends the same code."
    )
    public GroupInviteCodeDto createGroupInvite(
            @RequestBody GroupInviteCreateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.createGroupInviteCode(oidcUser.getSubject(), dto.challengeId());
    }

    @GetMapping("/group-invites/{token}")
    @Operation(summary = "Preview a scanned invite", description = "Describes the team behind a scanned code, and says whether the player can join it.")
    public GroupInvitePreviewDto previewGroupInvite(
            @PathVariable UUID token,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.previewGroupInvite(oidcUser.getSubject(), token);
    }

    @PostMapping("/group-invites/{token}/join")
    @Operation(
            summary = "Join a team by scanning its code",
            description = "Adds the player to the team, already agreed: scanning stands for acceptance, so no invitation is sent back to them."
    )
    public GroupInvitePreviewDto joinGroupInvite(
            @PathVariable UUID token,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.joinGroupInvite(oidcUser.getSubject(), token);
    }

    @PostMapping("/submissions/{id}/participants/accept")
    @Operation(summary = "Accept a group submission", description = "Confirms the authenticated player's participation in a pending group submission.")
    public void acceptGroupSubmission(@PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.respondToGroupSubmission(id, oidcUser.getSubject(), true);
    }

    @PostMapping("/submissions/{id}/participants/decline")
    @Operation(summary = "Decline a group submission", description = "Declines the authenticated player's participation in a pending group submission.")
    public void declineGroupSubmission(@PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.respondToGroupSubmission(id, oidcUser.getSubject(), false);
    }

    @PutMapping("/submissions/{id}/participants")
    @Operation(
            summary = "Replace the group of a waiting submission",
            description = "Lets the submitter recompose the team of a submission still waiting for confirmations: newcomers are invited, removed members are told, and the proof leaves as soon as everyone left has accepted."
    )
    public PlayerSubmissionDetailDto replaceGroupParticipants(
            @PathVariable UUID id,
            @RequestBody SubmissionParticipantsUpdateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.replaceGroupParticipants(
                id, oidcUser.getSubject(), dto == null ? null : dto.participantUserIds());
    }

    @DeleteMapping("/submissions/{id}")
    @Operation(summary = "Cancel a waiting group submission", description = "Allows the submitter to cancel a group submission that is still waiting for participant confirmations.")
    public void cancelGroupSubmission(@PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.cancelGroupSubmission(id, oidcUser.getSubject());
    }

    @PostMapping("/submissions/{id}/participants/resend")
    @Operation(summary = "Resend pending group invitations", description = "Allows the submitter to resend notifications to participants who have not answered yet.")
    public void resendGroupSubmissionInvites(@PathVariable UUID id, @AuthenticationPrincipal OidcUser oidcUser) {
        challengeService.resendGroupSubmissionInvites(id, oidcUser.getSubject());
    }

    @GetMapping("/submissions/{id}/messages")
    @Operation(summary = "List submission messages", description = "Returns the discussion for a submission the authenticated player participates in. Moderator identities are anonymized.")
    public List<SubmissionMessageDto> getSubmissionMessages(
            @PathVariable UUID id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.getPlayerSubmissionMessages(id, oidcUser.getSubject());
    }

    @PostMapping("/submissions/{id}/messages")
    @Operation(summary = "Send a submission message", description = "Adds a player message to a submission discussion.")
    public SubmissionMessageDto createSubmissionMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionMessageCreateDto dto,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return challengeService.createPlayerSubmissionMessage(id, oidcUser.getSubject(), dto);
    }
}
