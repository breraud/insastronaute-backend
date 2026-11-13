package fr.insastronaute.webapi.notification.controller;

import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.notification.dto.NotificationDto;
import fr.insastronaute.webapi.notification.dto.UnreadNotificationCountDto;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final UserSynchronizationService synchronizationService;
    private final NotificationService notificationService;

    public NotificationController(
            UserSynchronizationService synchronizationService,
            NotificationService notificationService
    ) {
        this.synchronizationService = synchronizationService;
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List notifications", description = "Returns notifications for the authenticated player, optionally filtered to unread items.")
    public PageResponse<NotificationDto> getMyNotifications(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return PageResponse.from(notificationService.listMine(user, unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count", description = "Returns the number of unread notifications for the authenticated player.")
    public UnreadNotificationCountDto getUnreadCount(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return new UnreadNotificationCountDto(notificationService.countUnread(user));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read", description = "Marks one notification as read for the authenticated player.")
    public NotificationDto markAsRead(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable UUID id
    ) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        return notificationService.markAsRead(user, id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks every notification as read for the authenticated player.")
    public void markAllAsRead(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = synchronizationService.syncWithKeycloak(oidcUser);
        notificationService.markAllAsRead(user);
    }
}
