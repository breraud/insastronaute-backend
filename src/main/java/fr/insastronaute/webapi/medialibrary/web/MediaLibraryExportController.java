package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.dto.MediaExportDto;
import fr.insastronaute.webapi.medialibrary.export.MediaExportNaming;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Les exports de la selection. Une partie s'ecrit a la main dans la reponse : le delai
 * asynchrone de Tomcat (30 s) couperait un StreamingResponseBody de 2 Go.
 */
@RestController
@RequestMapping("/api/media-library/exports")
@Tag(name = "Media Library")
public class MediaLibraryExportController {

    private final MediaLibraryExportService exportService;

    public MediaLibraryExportController(MediaLibraryExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Freeze the selection for export", description = "Snapshot, split into parts of 2 GiB at most.")
    public MediaExportDto create(@AuthenticationPrincipal OidcUser oidcUser) {
        return exportService.create(oidcUser.getSubject(), Authorities.of(oidcUser));
    }

    @GetMapping
    @Operation(summary = "List exports", description = "Newest first; every part stays downloadable.")
    public List<MediaExportDto> list(@AuthenticationPrincipal OidcUser oidcUser) {
        return exportService.list(oidcUser.getSubject(), Authorities.of(oidcUser));
    }

    @GetMapping("/{exportId}")
    @Operation(summary = "Get an export", description = "Its parts, their file count and weight.")
    public MediaExportDto get(@PathVariable UUID exportId, @AuthenticationPrincipal OidcUser oidcUser) {
        return exportService.get(oidcUser.getSubject(), Authorities.of(oidcUser), exportId);
    }

    @GetMapping("/{exportId}/parts/{partNumber}")
    @Operation(summary = "Download an export part", description = "Streamed zip. 429 when two exports already run.")
    public void downloadPart(@PathVariable UUID exportId,
                             @PathVariable int partNumber,
                             @AuthenticationPrincipal OidcUser oidcUser,
                             HttpServletResponse response) throws IOException {
        // Avant la place : un refus ou une partie absente repondent 403 ou 404, meme quand
        // deux exports tournent deja.
        exportService.checkPart(oidcUser.getSubject(), Authorities.of(oidcUser), exportId, partNumber);
        try (MediaLibraryExportService.ExportSlot ignored = exportService.acquireSlot()) {
            MediaLibraryExportService.PartPlan plan = exportService.preparePart(
                    oidcUser.getSubject(), Authorities.of(oidcUser), exportId, partNumber);
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(MediaExportNaming.partFileName(plan.snapshotAt(), plan.partNumber(), plan.partCount()))
                    .build().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private, max-age=0");
            response.setHeader("X-Content-Type-Options", "nosniff");
            try {
                exportService.writePart(plan, response.getOutputStream());
            } catch (IOException | RuntimeException exception) {
                UnsentResponse.discard(response);
                throw exception;
            }
        }
    }
}
