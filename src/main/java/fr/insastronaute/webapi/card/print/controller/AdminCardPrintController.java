package fr.insastronaute.webapi.card.print.controller;

import fr.insastronaute.webapi.card.print.dto.CardPrintBatchDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintBatchPageLookupDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintPreviewRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunCreateRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintRunSlotDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateCalibrationRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateDeleteRequest;
import fr.insastronaute.webapi.card.print.dto.CardPrintTemplateDto;
import fr.insastronaute.webapi.card.print.service.CardPrintRunService;
import fr.insastronaute.webapi.card.print.service.CardPrintPdfRegenerationService;
import fr.insastronaute.webapi.card.print.service.CardPrintTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Card Print")
public class AdminCardPrintController {

    private final CardPrintTemplateService templateService;
    private final CardPrintRunService printRunService;
    private final CardPrintPdfRegenerationService pdfRegenerationService;

    public AdminCardPrintController(
            CardPrintTemplateService templateService,
            CardPrintRunService printRunService,
            CardPrintPdfRegenerationService pdfRegenerationService
    ) {
        this.templateService = templateService;
        this.printRunService = printRunService;
        this.pdfRegenerationService = pdfRegenerationService;
    }

    @GetMapping("/card-print-templates")
    @Operation(summary = "List card print templates", description = "Returns configured PDF print templates and calibration settings.")
    public List<CardPrintTemplateDto> getTemplates() {
        return templateService.findAll();
    }

    @PostMapping(path = "/card-print-templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a card print template", description = "Uploads a PDF print template and reads its first page dimensions.")
    public CardPrintTemplateDto createTemplate(
            @RequestParam String name,
            @RequestParam MultipartFile pdf
    ) {
        return templateService.create(name, pdf);
    }

    @PutMapping("/card-print-templates/{id}/calibration")
    @Operation(summary = "Calibrate a card print template", description = "Stores the 9 card slots and code rendering settings for a PDF template.")
    public CardPrintTemplateDto calibrateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CardPrintTemplateCalibrationRequest request
    ) {
        return templateService.updateCalibration(id, request);
    }

    @DeleteMapping("/card-print-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a card print template", description = "Deletes one unused PDF print template and its public S3 object.")
    public void deleteTemplate(@PathVariable UUID id) {
        templateService.delete(id);
    }

    @DeleteMapping("/card-print-templates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete card print templates", description = "Deletes multiple unused PDF print templates and their public S3 objects.")
    public void deleteTemplates(@Valid @RequestBody CardPrintTemplateDeleteRequest request) {
        templateService.deleteAll(request.templateIds());
    }

    @PostMapping(
            path = "/card-print-templates/{id}/preview",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @Operation(summary = "Preview a card print template", description = "Generates a test PDF without creating physical card instances.")
    public ResponseEntity<byte[]> previewTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CardPrintPreviewRequest request
    ) {
        byte[] pdf = templateService.preview(id, request);
        return pdfResponse(pdf, "card-print-preview.pdf");
    }

    @PostMapping("/card-print-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a card print run", description = "Creates physical card instances, renders the selected sheets, and stores the generated PDF privately.")
    public CardPrintRunDto createPrintRun(
            @Valid @RequestBody CardPrintRunCreateRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return printRunService.create(request, oidcUser.getSubject());
    }

    @PostMapping("/batches/regenerate-pdfs")
    @Operation(summary = "Regenerate old print PDFs", description = "Re-renders completed print batch PDFs that were not generated with the compressed renderer.")
    public CardPrintPdfRegenerationDto regenerateBatchPdfs() {
        return pdfRegenerationService.regenerateUncompressedRunPdfs();
    }

    @PostMapping("/card-print-templates/{id}/regenerate-batches")
    @Operation(summary = "Regenerate template batches", description = "Force re-renders every completed batch PDF linked to this template using its current calibration.")
    public CardPrintPdfRegenerationDto regenerateTemplateBatches(@PathVariable UUID id) {
        return pdfRegenerationService.regenerateTemplateBatches(id);
    }

    @PostMapping("/batches/{id}/regenerate-pdf")
    @Operation(summary = "Regenerate one batch PDF", description = "Force re-renders one completed batch PDF while preserving its card instances and public ids.")
    public CardPrintPdfRegenerationDto regenerateBatchPdf(@PathVariable UUID id) {
        return pdfRegenerationService.regenerateBatch(id);
    }

    @GetMapping("/batches")
    @Operation(summary = "List print batches", description = "Returns every generated card print batch with pages, cards, and generated PDF metadata.")
    public List<CardPrintBatchDto> getBatches() {
        return printRunService.findAllBatches();
    }

    @GetMapping("/batches/{id}")
    @Operation(summary = "Get print batch", description = "Returns one generated card print batch with exact card and page traceability.")
    public CardPrintBatchDto getBatch(@PathVariable UUID id) {
        return printRunService.findBatch(id);
    }

    @GetMapping(path = "/batches/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download print batch PDF", description = "Streams the generated print PDF through the authenticated admin API.")
    public ResponseEntity<byte[]> getBatchPdf(@PathVariable UUID id) {
        byte[] pdf = printRunService.readBatchPdf(id);
        return pdfResponse(pdf, "card-print-batch-" + id + ".pdf");
    }

    @GetMapping("/batches/{id}/cards")
    @Operation(summary = "List batch cards", description = "Returns the exact card instances generated by a print batch.")
    public List<CardPrintRunSlotDto> getBatchCards(@PathVariable UUID id) {
        return printRunService.findBatchCards(id);
    }

    @DeleteMapping("/batches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete print batch", description = "Deletes one completed or failed card print batch and its unassigned card instances.")
    public void deleteBatch(@PathVariable UUID id) {
        printRunService.deleteBatch(id);
    }

    @GetMapping("/batches/pages/{printPageId}")
    @Operation(summary = "Find print page", description = "Finds the batch, PDF page, and associated cards from the page id stamped on paper.")
    public CardPrintBatchPageLookupDto findBatchPage(@PathVariable String printPageId) {
        return printRunService.findPage(printPageId);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename)
                        .build()
                        .toString())
                .body(pdf);
    }
}
