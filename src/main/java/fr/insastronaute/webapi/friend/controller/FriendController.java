package fr.insastronaute.webapi.friend.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.friend.dto.FriendDto;
import fr.insastronaute.webapi.friend.dto.FriendRequestDto;
import fr.insastronaute.webapi.friend.dto.FriendProfileDto;
import fr.insastronaute.webapi.friend.dto.FriendSearchResultDto;
import fr.insastronaute.webapi.friend.service.FriendService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friends")
public class FriendController {

    private final UserSynchronizationService synchronizationService;
    private final FriendService friendService;

    public FriendController(UserSynchronizationService synchronizationService, FriendService friendService) {
        this.synchronizationService = synchronizationService;
        this.friendService = friendService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search friend candidates", description = "Searches users the authenticated player can send a friend request to.")
    public PageResponse<FriendSearchResultDto> searchFriends(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20)
            @SortDefault(sort = "displayName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return PageResponse.from(friendService.searchFriendCandidates(user, query, pageable));
    }

    @GetMapping("/contacts/search")
    @Operation(summary = "Search contacts", description = "Searches the authenticated player's friends and department contacts.")
    public PageResponse<FriendSearchResultDto> searchDepartmentPeopleAndFriends(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20)
            @SortDefault(sort = "displayName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return PageResponse.from(friendService.searchDepartmentPeopleAndFriends(user, query, pageable));
    }

    @GetMapping
    @Operation(summary = "List friends", description = "Returns the authenticated player's accepted friends.")
    public List<FriendDto> getFriends(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return friendService.listFriends(user);
    }

    @GetMapping("/{userId}/profile")
    @Operation(summary = "Get friend profile", description = "Returns the limited profile and progression of an accepted friend.")
    public FriendProfileDto getFriendProfile(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID userId
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return friendService.getFriendProfile(user, userId);
    }

    @GetMapping("/requests/incoming")
    @Operation(summary = "List incoming friend requests", description = "Returns pending friend requests received by the authenticated player.")
    public PageResponse<FriendRequestDto> getIncomingRequests(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return PageResponse.from(friendService.listIncomingRequests(user, pageable));
    }

    @GetMapping("/requests/outgoing")
    @Operation(summary = "List outgoing friend requests", description = "Returns pending friend requests sent by the authenticated player.")
    public PageResponse<FriendRequestDto> getOutgoingRequests(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return PageResponse.from(friendService.listOutgoingRequests(user, pageable));
    }

    @PostMapping("/requests/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a friend request", description = "Creates a friend request from the authenticated player to another user.")
    public FriendRequestDto sendRequest(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID userId
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return friendService.sendRequest(user, userId);
    }

    @PostMapping("/requests/{requestId}/accept")
    @Operation(summary = "Accept a friend request", description = "Accepts an incoming friend request and creates the friendship.")
    public FriendRequestDto acceptRequest(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID requestId
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return friendService.acceptRequest(user, requestId);
    }

    @DeleteMapping("/requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Decline a friend request", description = "Declines an incoming friend request.")
    public void declineRequest(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID requestId
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        friendService.declineRequest(user, requestId);
    }

    @DeleteMapping("/requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a friend request", description = "Cancels an outgoing friend request sent by the authenticated player.")
    public void cancelRequest(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID requestId
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        friendService.cancelRequest(user, requestId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a friend", description = "Removes an accepted friend relationship for the authenticated player.")
    public void removeFriend(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID userId
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        friendService.removeFriend(user, userId);
    }
}
