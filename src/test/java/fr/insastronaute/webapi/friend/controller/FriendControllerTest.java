package fr.insastronaute.webapi.friend.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.dto.FriendRelationStatus;
import fr.insastronaute.webapi.friend.dto.FriendRequestDto;
import fr.insastronaute.webapi.friend.dto.FriendProfileDto;
import fr.insastronaute.webapi.friend.dto.FriendSearchResultDto;
import fr.insastronaute.webapi.friend.dto.FriendUserDto;
import fr.insastronaute.webapi.friend.service.FriendService;
import fr.insastronaute.webapi.user.domain.AppUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSynchronizationService synchronizationService;

    @MockitoBean
    private FriendService friendService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void sendRequestUsesAuthenticatedUser() throws Exception {
        AppUser currentUser = user();
        UUID targetUserId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(currentUser);
        when(friendService.sendRequest(currentUser, targetUserId)).thenReturn(new FriendRequestDto(
                requestId,
                FriendUserDto.from(currentUser),
                new FriendUserDto(targetUserId, "Bob", "bob@example.com", null),
                FriendshipStatus.PENDING,
                null,
                null
        ));

        mockMvc.perform(post("/api/friends/requests/{userId}", targetUserId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void searchContactsUsesAuthenticatedUser() throws Exception {
        AppUser currentUser = user();
        UUID contactUserId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(currentUser);
        when(friendService.searchDepartmentPeopleAndFriends(eq(currentUser), eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new FriendSearchResultDto(
                        new FriendUserDto(contactUserId, "Alice", "alice@example.com", null),
                        FriendRelationStatus.NONE
                ))));

        mockMvc.perform(get("/api/friends/contacts/search")
                        .param("query", "ali")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].user.id").value(contactUserId.toString()))
                .andExpect(jsonPath("$.content[0].relationStatus").value("NONE"));
    }

    @Test
    void acceptRequestUsesAuthenticatedUser() throws Exception {
        AppUser currentUser = user();
        UUID requestId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(currentUser);
        when(friendService.acceptRequest(currentUser, requestId)).thenReturn(new FriendRequestDto(
                requestId,
                new FriendUserDto(UUID.randomUUID(), "Alice", "alice@example.com", null),
                FriendUserDto.from(currentUser),
                FriendshipStatus.ACCEPTED,
                null,
                null
        ));

        mockMvc.perform(post("/api/friends/requests/{requestId}/accept", requestId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void removeFriendUsesAuthenticatedUser() throws Exception {
        AppUser currentUser = user();
        UUID friendUserId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(currentUser);

        mockMvc.perform(delete("/api/friends/{userId}", friendUserId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(friendService).removeFriend(currentUser, friendUserId);
    }

    @Test
    void getFriendProfileUsesAuthenticatedUser() throws Exception {
        AppUser currentUser = user();
        UUID friendUserId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(currentUser);
        when(friendService.getFriendProfile(currentUser, friendUserId)).thenReturn(new FriendProfileDto(
                "Alice",
                "ITI",
                3L,
                25.0,
                50.0,
                75.0,
                4,
                false
        ));

        mockMvc.perform(get("/api/friends/{userId}/profile", friendUserId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice"))
                .andExpect(jsonPath("$.department").value("ITI"))
                .andExpect(jsonPath("$.commonFriendsCount").value(3))
                .andExpect(jsonPath("$.virtualCardsCompletionPercent").value(25.0))
                .andExpect(jsonPath("$.physicalCardsCompletionPercent").value(50.0))
                .andExpect(jsonPath("$.challengesCompletionPercent").value(75.0))
                .andExpect(jsonPath("$.challengePointsRank").value(4));

        verify(friendService).getFriendProfile(currentUser, friendUserId);
    }

    @Test
    void declineRequestUsesAuthenticatedUser() throws Exception {
        AppUser currentUser = user();
        UUID requestId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(currentUser);

        mockMvc.perform(delete("/api/friends/requests/{requestId}/decline", requestId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(friendService).declineRequest(eq(currentUser), eq(requestId));
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");
        user.setDisplayName("Player");
        return user;
    }
}
