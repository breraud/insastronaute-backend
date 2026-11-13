package fr.insastronaute.webapi.treasurehunt.controller;

import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntPathUpdateRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntPathResponse;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointResponse;
import fr.insastronaute.webapi.treasurehunt.dto.AdminPathInitializationRequest;
import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntContentResponse;
import fr.insastronaute.webapi.treasurehunt.dto.OrderedIdsRequest;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntContentRequest;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntActivationStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntVisibilityRequest;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

import java.util.List;

@RestController
@RequestMapping("/api/admin/treasure-hunt")
public class AdminTreasureHuntController {
    private final TreasureHuntAdminService treasureHuntAdminService;

    public AdminTreasureHuntController(TreasureHuntAdminService treasureHuntAdminService) {
        this.treasureHuntAdminService = treasureHuntAdminService;
    }

    @GetMapping("/status")
    public TreasureHuntActivationStatusResponse status() { return treasureHuntAdminService.activationStatus(); }

    @PostMapping("/activate")
    public TreasureHuntActivationStatusResponse activate() { return treasureHuntAdminService.activate(); }

    @PostMapping("/deactivate")
    public TreasureHuntActivationStatusResponse deactivate() { return treasureHuntAdminService.deactivate(); }

    @PutMapping("/visibility")
    @Operation(summary = "Show or hide the treasure hunt", description = "Hides the treasure hunt from the player front entirely, without touching its activation.")
    public TreasureHuntActivationStatusResponse setVisibility(@Valid @RequestBody TreasureHuntVisibilityRequest request) {
        return treasureHuntAdminService.setVisible(Boolean.TRUE.equals(request.visible()));
    }

    @GetMapping("/paths")
    public List<AdminTreasureHuntPathResponse> listPaths() {
        return treasureHuntAdminService.listPaths();
    }

    @PostMapping("/paths/initialize")
    public List<AdminTreasureHuntPathResponse> initializeFixedPaths(@Valid @RequestBody AdminPathInitializationRequest request) {
        List<AdminTreasureHuntPathResponse> response = treasureHuntAdminService.initializeFixedPaths(request); treasureHuntAdminService.deactivateIfActive(); return response;
    }

    @PutMapping("/paths/{position}")
    public AdminTreasureHuntPathResponse updatePath(
            @PathVariable Integer position,
            @Valid @RequestBody AdminTreasureHuntPathUpdateRequest request
    ) {
        AdminTreasureHuntPathResponse response = treasureHuntAdminService.updatePath(position, request); treasureHuntAdminService.deactivateIfActive(); return response;
    }

    @PutMapping("/paths/order")
    public List<AdminTreasureHuntPathResponse> reorderPaths(@Valid @RequestBody OrderedIdsRequest request) {
        List<AdminTreasureHuntPathResponse> response = treasureHuntAdminService.reorderPaths(request); treasureHuntAdminService.deactivateIfActive(); return response;
    }

    @GetMapping("/paths/{pathId}/checkpoints")
    public List<AdminCheckpointResponse> listCheckpoints(@PathVariable UUID pathId) {
        return treasureHuntAdminService.listCheckpoints(pathId);
    }

    @PostMapping("/paths/{pathId}/checkpoints")
    public AdminCheckpointResponse createCheckpoint(@PathVariable UUID pathId, @Valid @RequestBody AdminCheckpointRequest request) {
        AdminCheckpointResponse response = treasureHuntAdminService.createCheckpoint(pathId, request); treasureHuntAdminService.deactivateIfActive(); return response;
    }

    @PutMapping("/paths/{pathId}/checkpoints/order")
    public List<AdminCheckpointResponse> reorderCheckpoints(@PathVariable UUID pathId, @Valid @RequestBody OrderedIdsRequest request) {
        List<AdminCheckpointResponse> response = treasureHuntAdminService.reorderCheckpoints(pathId, request); treasureHuntAdminService.deactivateIfActive(); return response;
    }

    @PutMapping("/checkpoints/{checkpointId}")
    public AdminCheckpointResponse updateCheckpoint(@PathVariable UUID checkpointId, @Valid @RequestBody AdminCheckpointRequest request) {
        AdminCheckpointResponse response = treasureHuntAdminService.updateCheckpoint(checkpointId, request); treasureHuntAdminService.deactivateIfActive(); return response;
    }

    @DeleteMapping("/checkpoints/{checkpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCheckpoint(@PathVariable UUID checkpointId) { treasureHuntAdminService.deleteCheckpoint(checkpointId); treasureHuntAdminService.deactivateIfActive(); }

    @GetMapping("/checkpoints/{checkpointId}/contents")
    public List<AdminTreasureHuntContentResponse> listCheckpointContents(@PathVariable UUID checkpointId) { return treasureHuntAdminService.listCheckpointContents(checkpointId); }

    @PostMapping("/checkpoints/{checkpointId}/contents")
    public AdminTreasureHuntContentResponse createCheckpointContent(@PathVariable UUID checkpointId, @Valid @RequestBody TreasureHuntContentRequest request) { AdminTreasureHuntContentResponse response = treasureHuntAdminService.createCheckpointContent(checkpointId, request); treasureHuntAdminService.deactivateIfActive(); return response; }

    @PostMapping(path = "/checkpoints/{checkpointId}/contents/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminTreasureHuntContentResponse uploadCheckpointContent(@PathVariable UUID checkpointId, @RequestParam MultipartFile file) { AdminTreasureHuntContentResponse response = treasureHuntAdminService.uploadCheckpointContent(checkpointId, file); treasureHuntAdminService.deactivateIfActive(); return response; }

    @PutMapping("/checkpoints/{checkpointId}/contents/order")
    public List<AdminTreasureHuntContentResponse> reorderCheckpointContents(@PathVariable UUID checkpointId, @Valid @RequestBody OrderedIdsRequest request) { List<AdminTreasureHuntContentResponse> response = treasureHuntAdminService.reorderCheckpointContents(checkpointId, request); treasureHuntAdminService.deactivateIfActive(); return response; }

    @GetMapping("/vault/contents")
    public List<AdminTreasureHuntContentResponse> listVaultContents() { return treasureHuntAdminService.listVaultContents(); }

    @PostMapping("/vault/contents")
    public AdminTreasureHuntContentResponse createVaultContent(@Valid @RequestBody TreasureHuntContentRequest request) { AdminTreasureHuntContentResponse response = treasureHuntAdminService.createVaultContent(request); treasureHuntAdminService.deactivateIfActive(); return response; }

    @PostMapping(path = "/vault/contents/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminTreasureHuntContentResponse uploadVaultContent(@RequestParam MultipartFile file) { AdminTreasureHuntContentResponse response = treasureHuntAdminService.uploadVaultContent(file); treasureHuntAdminService.deactivateIfActive(); return response; }

    @PutMapping("/vault/contents/order")
    public List<AdminTreasureHuntContentResponse> reorderVaultContents(@Valid @RequestBody OrderedIdsRequest request) { List<AdminTreasureHuntContentResponse> response = treasureHuntAdminService.reorderVaultContents(request); treasureHuntAdminService.deactivateIfActive(); return response; }

    @PutMapping("/contents/{contentId}")
    public AdminTreasureHuntContentResponse updateContent(@PathVariable UUID contentId, @Valid @RequestBody TreasureHuntContentRequest request) { AdminTreasureHuntContentResponse response = treasureHuntAdminService.updateContent(contentId, request); treasureHuntAdminService.deactivateIfActive(); return response; }

    @DeleteMapping("/contents/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContent(@PathVariable UUID contentId) { treasureHuntAdminService.deleteContent(contentId); treasureHuntAdminService.deactivateIfActive(); }

}
