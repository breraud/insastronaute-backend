package fr.insastronaute.webapi.announcement.controller;

import fr.insastronaute.webapi.announcement.dto.AnnouncementDto;
import fr.insastronaute.webapi.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
@Tag(name = "Announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @Operation(
            summary = "List the announcements to show",
            description = "Returns the announcements in their window that this player has not closed yet, newest first."
    )
    public List<AnnouncementDto> current(@AuthenticationPrincipal OidcUser oidcUser) {
        return announcementService.visibleFor(oidcUser.getSubject());
    }

    @PostMapping("/{announcementId}/dismiss")
    @Operation(
            summary = "Close an announcement",
            description = "Remembers that this player closed it, on every device."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal OidcUser oidcUser, @PathVariable UUID announcementId) {
        announcementService.dismiss(oidcUser.getSubject(), announcementId);
    }
}
