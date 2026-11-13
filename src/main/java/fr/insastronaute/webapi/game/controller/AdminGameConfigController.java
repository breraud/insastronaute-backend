package fr.insastronaute.webapi.game.controller;

import fr.insastronaute.webapi.game.service.GameConfigService;
import fr.insastronaute.webapi.game.dto.GameConfigImportDto;
import fr.insastronaute.webapi.game.dto.GameConfigReleaseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/game/configs")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Game Configs")
public class AdminGameConfigController {

    private final GameConfigService gameConfigService;

    public AdminGameConfigController(GameConfigService gameConfigService) {
        this.gameConfigService = gameConfigService;
    }

    @GetMapping
    @Operation(summary = "List game JSON configs", description = "Lists JSON configuration files under the configured public S3 prefix.")
    public List<String> list() {
        return gameConfigService.list();
    }

    @GetMapping(path = "/archive", produces = "application/zip")
    @Operation(
            summary = "Export the editable game configs",
            description = "Returns the whole editable JSON config tree as a zip, empty folders included, "
                    + "so an export can be re-imported without losing the tree."
    )
    public ResponseEntity<byte[]> exportArchive() {
        byte[] archive = gameConfigService.exportArchive();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"game-configs.zip\"")
                .body(archive);
    }

    @PostMapping(path = "/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import editable game configs from an archive",
            description = "Reads and validates the whole archive before writing anything. Without replace=true the "
                    + "import overwrites matching files and leaves the rest in place; with it, the editable prefix "
                    + "is wiped first so the result matches the archive exactly."
    )
    public GameConfigImportDto importArchive(
            @RequestParam MultipartFile archive,
            @RequestParam(defaultValue = "false") boolean replace
    ) {
        return gameConfigService.importArchive(archive, replace);
    }

    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Release current game configs", description = "Copies current JSON configs to an immutable versioned public S3 prefix and updates latest metadata.")
    public GameConfigReleaseDto release(@RequestParam String version) {
        return gameConfigService.release(version);
    }

    @PostMapping(path = "/releases/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload and release game configs", description = "Uploads a JSON config folder directly to a versioned public S3 release and updates latest metadata.")
    public GameConfigReleaseDto releaseUploaded(
            @RequestParam String version,
            @RequestParam List<MultipartFile> files
    ) {
        return gameConfigService.releaseUploaded(version, files);
    }

    @GetMapping("/releases")
    @Operation(summary = "List game config releases", description = "Lists existing versioned game config releases and their JSON files.")
    public List<GameConfigReleaseDto> listReleases() {
        return gameConfigService.listReleases();
    }

    @GetMapping("/releases/latest")
    @Operation(summary = "Get latest game config release", description = "Reads the latest game config release metadata from public S3.")
    public GameConfigReleaseDto latestRelease() {
        return gameConfigService.latestRelease();
    }

    @GetMapping(path = "/releases/{version}/files/{*fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Read a released game JSON config", description = "Reads one JSON file from a versioned game config release as text.")
    public String readReleaseFile(@PathVariable String version, @PathVariable String fileName) {
        return gameConfigService.readReleaseFile(version, normalizeCapturedPath(fileName));
    }

    @DeleteMapping("/releases/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a game config release", description = "Deletes one versioned game config release prefix from public S3.")
    public void deleteRelease(@PathVariable String version) {
        gameConfigService.deleteRelease(version);
    }

    @GetMapping("/folders")
    @Operation(summary = "List game config folders", description = "Lists folders under the editable public S3 config prefix.")
    public List<String> listFolders() {
        return gameConfigService.listFolders();
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Create a game config folder", description = "Creates one S3 folder marker under the editable public S3 config prefix.")
    public void createFolder(@RequestParam String path) {
        gameConfigService.createFolder(path);
    }

    @DeleteMapping("/folders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a game config folder", description = "Deletes one folder and all nested objects under the editable public S3 config prefix.")
    public void deleteFolder(@RequestParam String path) {
        gameConfigService.deleteFolder(path);
    }

    @GetMapping(path = "/{*fileName}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Read a game JSON config", description = "Reads one JSON configuration file from public S3 as text.")
    public String read(@PathVariable String fileName) {
        return gameConfigService.read(normalizeCapturedPath(fileName));
    }

    @PutMapping(path = "/{*fileName}", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Overwrite a game JSON config", description = "Validates JSON text and creates or overwrites one configuration file in public S3.")
    public void write(@PathVariable String fileName, @RequestBody String content) {
        gameConfigService.write(normalizeCapturedPath(fileName), content);
    }

    @DeleteMapping("/{*fileName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a game JSON config", description = "Deletes one JSON configuration file from the editable public S3 prefix.")
    public void deleteFile(@PathVariable String fileName) {
        gameConfigService.deleteFile(normalizeCapturedPath(fileName));
    }

    private String normalizeCapturedPath(String path) {
        String normalizedPath = path;
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }
}
