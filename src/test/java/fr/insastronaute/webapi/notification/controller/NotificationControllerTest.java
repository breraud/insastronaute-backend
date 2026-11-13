package fr.insastronaute.webapi.notification.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.dto.NotificationDto;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.user.domain.AppUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSynchronizationService synchronizationService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void getMyNotificationsReturnsPagedNotifications() throws Exception {
        AppUser user = user();
        UUID notificationId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);
        when(notificationService.listMine(eq(user), eq(true), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new NotificationDto(
                        notificationId,
                        NotificationType.GROUP_INVITE_RECEIVED,
                        "Invitation",
                        "Tu as recu une invitation.",
                        Map.of("groupId", groupId.toString()),
                        null,
                        "GROUP_INVITE",
                        groupId,
                        null,
                        Instant.parse("2026-06-18T10:00:00Z")
                )
        )));

        mockMvc.perform(get("/api/notifications")
                        .param("unreadOnly", "true")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.content[0].type").value("GROUP_INVITE_RECEIVED"))
                .andExpect(jsonPath("$.content[0].payload.groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.content[0].readAt").doesNotExist());
    }

    @Test
    void getMyNotificationsReturnsBadRequestForInvalidSort() throws Exception {
        AppUser user = user();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);
        when(notificationService.listMine(eq(user), eq(false), any(Pageable.class)))
                .thenThrow(new InvalidDataAccessApiUsageException("No property '[\"string\"]' found for type 'Notification'"));

        mockMvc.perform(get("/api/notifications")
                        .param("unreadOnly", "false")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "[\"string\"]")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Paramètre de tri invalide."))
                .andExpect(jsonPath("$.path").value("/api/notifications"));
    }

    @Test
    void getUnreadCountReturnsCount() throws Exception {
        AppUser user = user();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);
        when(notificationService.countUnread(user)).thenReturn(2L);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void markAsReadUsesAuthenticatedUser() throws Exception {
        AppUser user = user();
        UUID notificationId = UUID.randomUUID();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);
        when(notificationService.markAsRead(user, notificationId)).thenReturn(new NotificationDto(
                notificationId,
                NotificationType.FRIEND_REQUEST_ACCEPTED,
                "Demande acceptee",
                "Ta demande a ete acceptee.",
                Map.of(),
                null,
                null,
                null,
                Instant.parse("2026-06-18T10:01:00Z"),
                Instant.parse("2026-06-18T10:00:00Z")
        ));

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.readAt").value("2026-06-18T10:01:00Z"));
    }

    @Test
    void markAllAsReadUsesAuthenticatedUser() throws Exception {
        AppUser user = user();

        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(notificationService).markAllAsRead(user);
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");
        return user;
    }
}
