package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.dto.RetainedMediaCountDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibrarySelectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Combien de medias retenus une purge de defis detruirait. Sous /api/admin/challenges,
 * donc ouverte aux memes roles que la modification groupee qui purge.
 */
@RestController
@RequestMapping("/api/admin/challenges")
@Tag(name = "Admin Challenges")
public class AdminChallengeRetainedMediaController {

    private final MediaLibrarySelectionService selectionService;

    public AdminChallengeRetainedMediaController(MediaLibrarySelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @GetMapping("/retained-media-count")
    @Operation(summary = "Retained media among challenges", description = "Shown before a proof purge, which would destroy them.")
    public RetainedMediaCountDto retainedMediaCount(@RequestParam List<UUID> ids) {
        return new RetainedMediaCountDto(selectionService.retainedInChallenges(ids));
    }
}
