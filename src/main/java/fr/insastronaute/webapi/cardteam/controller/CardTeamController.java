package fr.insastronaute.webapi.cardteam.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.cardteam.dto.CardTeamInviteRequest;
import fr.insastronaute.webapi.cardteam.dto.CardTeamStateDto;
import fr.insastronaute.webapi.cardteam.service.CardTeamService;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/card-teams")
public class CardTeamController {
    private final UserSynchronizationService synchronizationService;
    private final CardTeamService cardTeamService;

    public CardTeamController(UserSynchronizationService synchronizationService, CardTeamService cardTeamService) {
        this.synchronizationService = synchronizationService;
        this.cardTeamService = cardTeamService;
    }

    @GetMapping("/state")
    public CardTeamStateDto getState(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return cardTeamService.getState(user);
    }

    @PostMapping("/{teamId}/requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestJoin(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID teamId) {
        cardTeamService.requestJoin(synchronizationService.syncWithKeycloak(oidcUser), teamId);
    }

    /**
     * Le corps est optionnel : il ne porte un nom que pour fonder une equipe a deux,
     * lorsque l'invitant n'en a pas encore.
     */
    @PostMapping("/invitations/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) CardTeamInviteRequest request
    ) {
        cardTeamService.invite(
                synchronizationService.syncWithKeycloak(oidcUser),
                userId,
                request == null ? null : request.teamName()
        );
    }

    @PostMapping("/requests/{requestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptRequest(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID requestId) {
        cardTeamService.acceptJoinRequest(synchronizationService.syncWithKeycloak(oidcUser), requestId);
    }

    @DeleteMapping("/requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineRequest(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID requestId) {
        cardTeamService.declineJoinRequest(synchronizationService.syncWithKeycloak(oidcUser), requestId);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID invitationId) {
        cardTeamService.acceptInvitation(synchronizationService.syncWithKeycloak(oidcUser), invitationId);
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID invitationId) {
        cardTeamService.declineInvitation(synchronizationService.syncWithKeycloak(oidcUser), invitationId);
    }

}
