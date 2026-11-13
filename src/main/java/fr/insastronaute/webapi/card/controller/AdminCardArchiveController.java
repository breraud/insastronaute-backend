package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.dto.CardArchiveImportResultDto;
import fr.insastronaute.webapi.card.service.CardArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/cards")
@Tag(name = "Admin Card Archive")
public class AdminCardArchiveController {

    private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");

    private final CardArchiveService archiveService;

    public AdminCardArchiveController(CardArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping(path = "/export", produces = "application/zip")
    @Operation(summary = "Export card system", description = "Exports cards, card images, print templates, print batches, generated PDFs, physical instances, and decks as one ZIP archive.")
    public ResponseEntity<byte[]> exportCards() {
        byte[] archive = archiveService.exportArchive();
        return ResponseEntity.ok()
                .contentType(APPLICATION_ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("insastronaute-card-archive-" + LocalDate.now() + ".zip")
                        .build()
                        .toString())
                .body(archive);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import card system", description = "Resets the complete card system and restores it from an export archive. Physical instances are restored without owners. Set importPhysical=false to restore the catalogue only, leaving physical instances and print runs out.")
    public CardArchiveImportResultDto importCards(
            @RequestParam MultipartFile archive,
            @RequestParam(defaultValue = "true") boolean importPhysical
    ) {
        return archiveService.importArchive(archive, importPhysical);
    }
}
