package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.medialibrary.dto.MediaChallengeDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaPersonDto;
import fr.insastronaute.webapi.medialibrary.dto.MediaSubmissionDto;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryBrowseService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/media-library")
@Tag(name = "Media Library")
public class MediaLibraryBrowseController {

    private final MediaLibraryBrowseService browseService;

    public MediaLibraryBrowseController(MediaLibraryBrowseService browseService) {
        this.browseService = browseService;
    }

    @GetMapping("/challenges")
    @Operation(summary = "Challenges with eligible media", description = "Eligible proofs and retained media per challenge.")
    public List<MediaChallengeDto> challenges(@RequestParam(required = false) List<String> status,
                                              @RequestParam(required = false) String type,
                                              @AuthenticationPrincipal OidcUser oidcUser) {
        return browseService.challenges(oidcUser.getSubject(), Authorities.of(oidcUser), MediaLibraryFilter.of(status, type));
    }

    @GetMapping("/people")
    @Operation(summary = "People with eligible media", description = "Authors and participants, by number of eligible proofs.")
    public PageResponse<MediaPersonDto> people(@RequestParam(required = false) List<String> status,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String query,
                                               @RequestParam(defaultValue = "0") int page,
                                               @AuthenticationPrincipal OidcUser oidcUser) {
        return browseService.people(oidcUser.getSubject(), Authorities.of(oidcUser), MediaLibraryFilter.of(status, type), query, page);
    }

    @GetMapping("/submissions")
    @Operation(summary = "Eligible submissions and their media", description = "24 per page. No storage key is ever returned.")
    public PageResponse<MediaSubmissionDto> submissions(@RequestParam(required = false) List<String> status,
                                                        @RequestParam(required = false) String type,
                                                        @RequestParam(required = false) UUID challengeId,
                                                        @RequestParam(required = false) UUID userId,
                                                        @RequestParam(defaultValue = "false") boolean selected,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @AuthenticationPrincipal OidcUser oidcUser) {
        return browseService.submissions(oidcUser.getSubject(), Authorities.of(oidcUser), MediaLibraryFilter.of(status, type),
                challengeId, userId, selected, page);
    }
}
