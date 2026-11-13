package fr.insastronaute.webapi.card.print.controller;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropLabelRequest;
import fr.insastronaute.webapi.card.print.dto.CardDatasetCropUploadRequest;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetDto;
import fr.insastronaute.webapi.card.print.dto.CardDatasetSheetSummaryDto;
import fr.insastronaute.webapi.card.print.dto.CardPrintPdfRegenerationDto;
import fr.insastronaute.webapi.card.print.service.CardDatasetCropService;
import fr.insastronaute.webapi.card.print.service.CardDatasetExportService;
import fr.insastronaute.webapi.card.print.service.CardDatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/batches")
@Tag(name = "Admin Card Dataset")
public class AdminCardDatasetController {

    /** L'identifiant de la planche qui vient d'etre generee, pour eviter de le relire sur le PDF. */
    public static final String SHEET_ID_HEADER = "X-Dataset-Sheet-Id";

    private final CardDatasetService datasetService;
    private final CardDatasetCropService cropService;
    private final CardDatasetExportService exportService;

    public AdminCardDatasetController(
            CardDatasetService datasetService,
            CardDatasetCropService cropService,
            CardDatasetExportService exportService
    ) {
        this.datasetService = datasetService;
        this.cropService = cropService;
        this.exportService = exportService;
    }

    @PostMapping(path = "/dataset/test", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate test ML dataset PDF", description = "Generates a PDF of card bottoms using random active cards and codes that exist nowhere in the database, and remembers those codes against a printed sheet identifier.")
    public ResponseEntity<byte[]> generateTestDataset() {
        CardDatasetService.GeneratedDatasetSheet generated = datasetService.generateTestDatasetPdf();
        return pdfResponse(generated, "dataset-" + generated.sheetId() + ".pdf");
    }

    @GetMapping("/dataset/sheets")
    @Operation(summary = "List generated ML dataset sheets", description = "Lists every generated sheet, including legacy sheets whose original PDF was not archived, with their number of stored scans.")
    public List<CardDatasetSheetSummaryDto> listDatasetSheets() {
        return datasetService.findAllSheets();
    }

    @PostMapping("/dataset/sheets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate new ML test sheets", description = "Generates count test sheets in one pass, stores each original PDF privately in object storage, and returns their metadata without embedding any PDF in JSON. All or nothing: a failure rolls the whole batch back.")
    public List<CardDatasetSheetSummaryDto> createDatasetSheets(@RequestParam(defaultValue = "1") int count) {
        return datasetService.createTestSheets(count);
    }

    @GetMapping(path = "/dataset/sheets/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download several ML sheets as one PDF", description = "Returns one PDF holding every requested sheet. Sheets that remember the cards they carry are re-rendered together, so each distinct card is embedded once instead of once per slot; older sheets fall back to concatenating their stored PDFs.")
    public ResponseEntity<byte[]> downloadDatasetSheets(@RequestParam List<String> sheetId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("planches-dataset-" + LocalDate.now() + ".pdf")
                        .build()
                        .toString())
                .body(datasetService.renderSheetsPdf(sheetId));
    }

    @GetMapping(path = "/dataset/{sheetId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download an existing ML sheet PDF", description = "Returns the exact original PDF archived when the sheet was generated. Legacy sheets without an archived PDF return 404.")
    public ResponseEntity<byte[]> downloadDatasetSheet(@PathVariable String sheetId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("dataset-" + sheetId + ".pdf")
                        .build()
                        .toString())
                .body(datasetService.readSheetPdf(sheetId));
    }

    @PostMapping("/dataset/sheets/regenerate")
    @Operation(summary = "Regenerate every ML sheet", description = "Re-renders every sheet that remembers the cards it carries against the current print template, replacing each archived PDF. Sheets that cannot be re-rendered are skipped, not failed.")
    public CardPrintPdfRegenerationDto regenerateAllDatasetSheets() {
        return datasetService.regenerateAllSheetPdfs();
    }

    @PostMapping(path = "/dataset/{sheetId}/regenerate", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Regenerate an ML sheet PDF", description = "Re-renders the sheet with the current print template and replaces its archived PDF. Explicit on purpose: changing a template never rewrites printed sheets on its own. Sheets generated before codes remembered their card cannot be regenerated.")
    public ResponseEntity<byte[]> regenerateDatasetSheet(@PathVariable String sheetId) {
        byte[] pdf = datasetService.regenerateSheetPdf(sheetId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("dataset-" + sheetId + ".pdf")
                        .build()
                        .toString())
                .body(pdf);
    }

    @DeleteMapping("/dataset/{sheetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an ML sheet", description = "Deletes the sheet, its codes, every crop scanned from it, and everything it stored. Cascading: a botched sheet takes the scans it produced with it.")
    public void deleteDatasetSheet(@PathVariable String sheetId) {
        datasetService.deleteSheet(sheetId);
    }

    @PostMapping(path = "/{id}/dataset", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate batch ML dataset PDF", description = "Generates a PDF of card bottoms matching exactly the physical prints of a batch, and remembers them against a printed sheet identifier.")
    public ResponseEntity<byte[]> generateBatchDataset(@PathVariable UUID id) {
        CardDatasetService.GeneratedDatasetSheet generated = datasetService.generateBatchDatasetPdf(id);
        return pdfResponse(generated, "dataset-" + generated.sheetId() + ".pdf");
    }

    @GetMapping("/dataset/{sheetId}/codes")
    @Operation(summary = "Read the codes a dataset sheet carries", description = "Returns the codes printed on one dataset sheet, in the order they are printed.")
    public CardDatasetSheetDto getSheetCodes(@PathVariable String sheetId) {
        return datasetService.findSheet(sheetId);
    }

    @PostMapping(path = "/dataset/{sheetId}/crops", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Store one raw dataset crop",
            description = "Stores the raw crop of one physical strip, never a preprocessed tensor, with its "
                    + "label when one is known. `framing` says how the crop was framed -- AUTOMATIC or "
                    + "MANUAL, AUTOMATIC when absent. A manually framed crop is by definition one the "
                    + "automatic pipeline could not handle, so it is the hard case of the set; an unknown "
                    + "value is refused rather than passed off as automatic, which would quietly dilute it. "
                    + "`frame` and the `region*` fields are optional and go together: the whole photograph the "
                    + "crop was taken from, and where the crop sat in it, in that photograph's own pixels. They "
                    + "are what a framing model would be trained on -- a manually framed crop is by definition a "
                    + "case the automatic pipeline failed, and keeping only its contents throws away the question "
                    + "while keeping the answer. A region is four numbers or none, and it must fit inside the "
                    + "photograph: a region expressed in the preview stream's pixels beside a full-resolution "
                    + "photograph would look perfectly valid and point at nothing."
    )
    public CardDatasetCropDto storeCrop(
            @PathVariable String sheetId,
            @RequestParam Integer position,
            @RequestParam(required = false) String label,
            @RequestParam(required = false, defaultValue = "AUTOMATIC") CardDatasetCropFraming framing,
            @RequestParam(required = false) String captureDevice,
            @RequestParam(required = false) Integer captureWidthPx,
            @RequestParam(required = false) Integer captureHeightPx,
            @RequestParam(required = false) Integer regionXPx,
            @RequestParam(required = false) Integer regionYPx,
            @RequestParam(required = false) Integer regionWidthPx,
            @RequestParam(required = false) Integer regionHeightPx,
            @RequestParam(required = false) Double regionAngleDeg,
            @RequestParam MultipartFile image,
            @RequestParam(required = false) MultipartFile frame
    ) {
        CardDatasetCropUploadRequest request = new CardDatasetCropUploadRequest(
                position, label, framing, captureDevice, captureWidthPx, captureHeightPx,
                regionXPx, regionYPx, regionWidthPx, regionHeightPx, regionAngleDeg);
        return cropService.store(sheetId, request, image, frame);
    }

    @GetMapping("/dataset/{sheetId}/crops")
    @Operation(
            summary = "List the crops taken from one dataset sheet",
            description = "Returns every stored crop of a sheet with its physical strip, its expected "
                    + "code and its label. Crops the operator discarded are still listed, with status "
                    + "DISCARDED, so the admin tool can grey them out and offer to restore them: hiding "
                    + "them would remove the only way back, and a discard with no way back is a delete."
    )
    public List<CardDatasetCropDto> getCrops(@PathVariable String sheetId) {
        return cropService.findBySheet(sheetId);
    }

    @PatchMapping("/dataset/crops/{cropId}/label")
    @Operation(summary = "Record the arbitration of one crop", description = "Sets the ground truth label a human decided for a crop no code of the sheet explained.")
    public CardDatasetCropDto labelCrop(
            @PathVariable UUID cropId,
            @Valid @RequestBody CardDatasetCropLabelRequest request
    ) {
        return cropService.label(cropId, request.label());
    }

    @DeleteMapping("/dataset/crops/{cropId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Discard one crop the operator judged unusable",
            description = "Takes a bad capture -- blurred, mis-framed, the wrong strip -- out of the "
                    + "dataset. It is a status change and nothing else: the row stays, the stored image "
                    + "is never touched, the crop keeps showing up in the sheet listing with status "
                    + "DISCARDED, and `POST .../restore` puts it back. It leaves the export, so it "
                    + "reaches no training. Idempotent: discarding an already discarded crop answers "
                    + "204 too, because a thumb on a touch screen sends the same request twice. A crop "
                    + "that never existed is 404."
    )
    public void discardCrop(@PathVariable UUID cropId) {
        cropService.discard(cropId);
    }

    @PostMapping("/dataset/crops/{cropId}/restore")
    @Operation(
            summary = "Put a discarded crop back into the dataset",
            description = "Undoes a discard. The crop comes back with the status its label implies -- "
                    + "LABELLED when the code was known, PENDING_ARBITRATION otherwise -- because the "
                    + "discard touched neither the label nor the image. Restoring a crop that was never "
                    + "discarded changes nothing and still answers 200."
    )
    public CardDatasetCropDto restoreCrop(@PathVariable UUID cropId) {
        return cropService.restore(cropId);
    }

    @GetMapping(path = "/dataset/export", produces = "application/zip")
    @Operation(
            summary = "Export the dataset, split by physical strip",
            description = "Returns a zip of every stored crop with its label, split train/val/test on the "
                    + "identity of the physical strip -- never on the image, because one strip is "
                    + "photographed many times and two photos of it either side of the split would make "
                    + "validation measure memorisation. The test set is held out by a fixed function of the "
                    + "strip identity, so it is not re-drawn on every export. Crops awaiting arbitration "
                    + "carry no trustworthy label and ship apart, under a manifest with no code column."
    )
    public ResponseEntity<byte[]> exportDataset(
            @RequestParam(name = "frames", defaultValue = "false") boolean frames
    ) {
        byte[] archive = exportService.exportArchive(frames);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(frames ? "card-dataset-frames.zip" : "card-dataset.zip")
                        .build()
                        .toString())
                .body(archive);
    }

    @GetMapping("/dataset/crops/{cropId}/image")
    @Operation(summary = "Read one raw dataset crop", description = "Returns the stored raw crop bytes, unchanged.")
    public ResponseEntity<byte[]> getCropImage(@PathVariable UUID cropId) {
        CardDatasetCropService.StoredCropImage image = cropService.readImage(cropId);
        MediaType contentType = image.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(image.contentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(image.bytes());
    }

    private ResponseEntity<byte[]> pdfResponse(CardDatasetService.GeneratedDatasetSheet generated, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(SHEET_ID_HEADER, generated.sheetId())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename)
                        .build()
                        .toString())
                .body(generated.pdf());
    }
}
