package fr.insastronaute.webapi.sts.multiplayer.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatSnapshotDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpBattleActionRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpBattleDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeResponse;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpCollectionDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpDeckDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpDeckSaveRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpNotificationDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpProfileDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpProfileUpdateRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQueueStatusResponse;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQuickMatchRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQuickMatchResponse;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpMatchmakingStateResponse;
import fr.insastronaute.webapi.sts.multiplayer.service.StsPvpService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sts/pvp")
@ConditionalOnProperty(name = "app.sts.pvp.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "STS PVP")
public class StsPvpController {

    private final UserSynchronizationService synchronizationService;
    private final StsPvpService stsPvpService;

    public StsPvpController(UserSynchronizationService synchronizationService, StsPvpService stsPvpService) {
        this.synchronizationService = synchronizationService;
        this.stsPvpService = stsPvpService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get STS PVP profile", description = "Returns selected character, ELO and multiplayer progression for the current player.")
    public StsPvpProfileDto profile(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.getProfile(currentUser(oidcUser));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update STS PVP profile", description = "Updates selected character and PVP preferences for the current player.")
    public StsPvpProfileDto updateProfile(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestBody(required = false) StsPvpProfileUpdateRequest request
    ) {
        return stsPvpService.updateProfile(currentUser(oidcUser), request);
    }

    @GetMapping("/collection")
    @Operation(summary = "Get STS PVP collection", description = "Returns owned and multiplayer-unlocked cards, plus deck-size constraints.")
    public StsPvpCollectionDto collection(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.getCollection(currentUser(oidcUser));
    }

    @GetMapping("/decks")
    @Operation(summary = "List STS PVP decks", description = "Returns all saved PVP decks for the current player.")
    public List<StsPvpDeckDto> decks(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.listDecks(currentUser(oidcUser));
    }

    @PostMapping("/decks")
    @Operation(summary = "Save STS PVP deck", description = "Creates a new PVP deck configuration for the current player.")
    public StsPvpDeckDto saveDeck(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestBody StsPvpDeckSaveRequest request
    ) {
        return stsPvpService.saveDeck(currentUser(oidcUser), request);
    }

    @GetMapping("/decks/{deckId}")
    @Operation(summary = "Get STS PVP deck", description = "Returns one saved PVP deck of the current player.")
    public StsPvpDeckDto deck(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID deckId
    ) {
        return stsPvpService.getDeck(currentUser(oidcUser), deckId);
    }

    @DeleteMapping("/decks/{deckId}")
    @Operation(summary = "Delete STS PVP deck",
            description = "Removes one saved PVP deck of the current player. A deck of another "
                    + "player is not found rather than refused, so the endpoint cannot be used to "
                    + "probe which deck ids exist.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeck(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID deckId
    ) {
        stsPvpService.deleteDeck(currentUser(oidcUser), deckId);
    }

    @GetMapping("/matchmaking/queue")
    @Operation(summary = "How many players are queueing right now",
            description = "Counts live queue entries for one mode and queue kind, so the menu can say whether a match is likely.")
    public StsPvpQueueStatusResponse queueStatus(
            @RequestParam(name = "mode", required = false) String mode,
            @RequestParam(name = "friendly", required = false, defaultValue = "false") boolean friendly
    ) {
        return stsPvpService.queueStatus(mode, friendly);
    }

    @PostMapping("/matchmaking/quick")
    @Operation(summary = "Queue for quick PVP match", description = "Queues current player and tries immediate ELO-based pairing.")
    public StsPvpQuickMatchResponse quickMatch(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestBody(required = false) StsPvpQuickMatchRequest request
    ) {
        return stsPvpService.quickMatch(currentUser(oidcUser), request);
    }

    @DeleteMapping("/matchmaking/quick")
    @Operation(summary = "Cancel quick PVP matchmaking", description = "Removes the current player from the quick-match queue.")
    public StsPvpMatchmakingStateResponse cancelQuickMatch(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.cancelQuickMatch(currentUser(oidcUser));
    }

    @PostMapping("/matchmaking/quick/heartbeat")
    @Operation(summary = "Keep quick PVP matchmaking alive", description = "Refreshes an existing queue entry without starting another pairing attempt.")
    public StsPvpMatchmakingStateResponse heartbeatQuickMatch(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.heartbeatQuickMatch(currentUser(oidcUser));
    }

    @PostMapping("/challenges")
    @Operation(summary = "Send PVP challenge", description = "Sends a direct challenge invitation to another player.")
    public StsPvpChallengeResponse challenge(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestBody StsPvpChallengeRequest request
    ) {
        return stsPvpService.sendChallenge(currentUser(oidcUser), request);
    }

    @GetMapping("/challenges")
    @Operation(summary = "List pending PVP challenges",
            description = "Pending challenges the current player received (INCOMING) or sent (OUTGOING). "
                    + "Challenges past their deadline are marked expired and left out.")
    public List<StsPvpChallengeDto> challenges(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.listChallenges(currentUser(oidcUser));
    }

    @GetMapping("/challenges/{challengeId}")
    @Operation(summary = "Get a PVP challenge",
            description = "One challenge the current player takes part in, whatever its status. Once accepted "
                    + "it carries the battleId, which is how the waiting challenger learns to join.")
    public StsPvpChallengeDto challenge(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID challengeId
    ) {
        return stsPvpService.getChallenge(currentUser(oidcUser), challengeId);
    }

    @PostMapping("/challenges/{challengeId}/accept")
    @Operation(summary = "Accept a PVP challenge",
            description = "The challenged player accepts: a duel opens for both players and its battleId is returned.")
    public StsPvpChallengeDto acceptChallenge(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID challengeId
    ) {
        return stsPvpService.acceptChallenge(currentUser(oidcUser), challengeId);
    }

    @PostMapping("/challenges/{challengeId}/decline")
    @Operation(summary = "Decline a PVP challenge", description = "The challenged player declines; the challenger is notified.")
    public StsPvpChallengeDto declineChallenge(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID challengeId
    ) {
        return stsPvpService.declineChallenge(currentUser(oidcUser), challengeId);
    }

    @PostMapping("/challenges/{challengeId}/cancel")
    @Operation(summary = "Cancel a PVP challenge", description = "The challenger withdraws a challenge nobody answered yet.")
    public StsPvpChallengeDto cancelChallenge(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID challengeId
    ) {
        return stsPvpService.cancelChallenge(currentUser(oidcUser), challengeId);
    }

    @GetMapping("/notifications")
    @Operation(summary = "List PVP notifications", description = "Returns challenge and match-related PVP notifications for the current player.")
    public List<StsPvpNotificationDto> notifications(@AuthenticationPrincipal OidcUser oidcUser) {
        return stsPvpService.listNotifications(currentUser(oidcUser));
    }

    @PostMapping("/notifications/{notificationId}/ack")
    @Operation(summary = "Acknowledge PVP notification", description = "Marks one PVP notification as read.")
    public StsPvpNotificationDto ackNotification(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID notificationId
    ) {
        return stsPvpService.acknowledgeNotification(currentUser(oidcUser), notificationId);
    }

    @GetMapping("/battles/{battleId}")
    @Operation(summary = "Get PVP battle state", description = "Returns shared battle state, turn deadline and inactive participants.")
    public StsPvpBattleDto battle(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID battleId
    ) {
        return stsPvpService.getBattleState(currentUser(oidcUser), battleId);
    }

    /**
     * The combat of a battle, for the player asking, in the shape a client resynchronises from.
     *
     * <p>A PvP client used to ask the PvE endpoint, which answers a hard-coded {@code "PVE"} and a
     * payload it cannot parse, so it looped. What comes back here is the projection for the caller
     * and nothing else: the stored state holds both hands and the seed, so a stranger to the
     * battle is refused rather than served it.
     */
    @GetMapping("/battles/{battleId}/snapshot")
    @Operation(summary = "Get PVP combat snapshot",
            description = "Returns the authoritative PVP combat, projected for the calling player, "
                    + "in the same snapshot shape the PVE channel uses.")
    public CombatSnapshotDto combatSnapshot(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID battleId
    ) {
        return stsPvpService.combatSnapshot(currentUser(oidcUser), battleId);
    }

    /**
     * The battle given up on, in the caller's favour no longer.
     *
     * <p>A player who wants out had, until now, to close the tab and wait for the disconnect sweep
     * to notice — two minutes during which their opponent watched a frozen screen. The body is
     * empty on purpose: the only thing this says is "I give up", the battle is in the path and the
     * player is the authenticated one, so there is nothing a client could get wrong.
     *
     * <p>Answering the battle rather than nothing at all lets the client show the outcome from the
     * call it made, without waiting for the socket frame that carries the same news.
     */
    @PostMapping("/battles/{battleId}/surrender")
    @Operation(summary = "Surrender a PVP battle",
            description = "Ends the battle in the opponent's favour. The caller must play in it; "
                    + "a battle that is already over is left untouched.")
    public StsPvpBattleDto surrenderBattle(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID battleId
    ) {
        return stsPvpService.surrender(currentUser(oidcUser), battleId);
    }

    @PostMapping("/battles/{battleId}/actions")
    @Operation(summary = "Send PVP battle action", description = "Appends an action/heartbeat from the local player to the battle state.")
    public StsPvpBattleDto battleAction(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID battleId,
            @RequestBody(required = false) StsPvpBattleActionRequest request
    ) {
        return stsPvpService.submitBattleAction(currentUser(oidcUser), battleId, request);
    }

    private AppUser currentUser(OidcUser oidcUser) {
        return synchronizationService.syncWithKeycloak(oidcUser);
    }
}
