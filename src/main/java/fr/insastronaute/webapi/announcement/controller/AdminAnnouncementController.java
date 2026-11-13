package fr.insastronaute.webapi.announcement.controller;

import fr.insastronaute.webapi.announcement.dto.AnnouncementDto;
import fr.insastronaute.webapi.announcement.dto.AnnouncementRequest;
import fr.insastronaute.webapi.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/announcements")
@Tag(name = "Admin Announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @Operation(summary = "List every announcement", description = "Newest first, closed or still running.")
    public List<AnnouncementDto> list() {
        return announcementService.findAll();
    }

    @PostMapping
    @Operation(summary = "Write an announcement")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementDto create(@Valid @RequestBody AnnouncementRequest request) {
        return announcementService.create(request);
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "Rewrite an announcement")
    public AnnouncementDto update(
            @PathVariable UUID announcementId,
            @Valid @RequestBody AnnouncementRequest request
    ) {
        return announcementService.update(announcementId, request);
    }

    @DeleteMapping("/{announcementId}")
    @Operation(summary = "Delete an announcement", description = "Its closings go with it.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID announcementId) {
        announcementService.delete(announcementId);
    }
}
