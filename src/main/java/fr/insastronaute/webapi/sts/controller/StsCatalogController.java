package fr.insastronaute.webapi.sts.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sts/catalog")
@Tag(name = "STS Catalog")
public class StsCatalogController {

    private final StsGameDataService gameDataService;
    private final ObjectMapper objectMapper;

    public StsCatalogController(StsGameDataService gameDataService, ObjectMapper objectMapper) {
        this.gameDataService = gameDataService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/cards")
    @Operation(summary = "Get STS cards catalog", description = "Returns the cards JSON from the latest game config release.")
    public Object cards(@RequestParam(required = false) String version) {
        return apiJson(gameDataService.cards(resolveVersion(version)));
    }

    @GetMapping("/version")
    @Operation(summary = "Get active STS catalog version", description = "Returns the immutable version of the latest game config release.")
    public Map<String, String> version() {
        return Map.of("dataVersion", gameDataService.latestVersion());
    }

    @GetMapping("/enemies")
    @Operation(summary = "Get STS enemies catalog", description = "Returns enemy JSON documents from the latest game config release.")
    public Object enemies(@RequestParam(required = false) String version) {
        return apiJson(gameDataService.enemies(resolveVersion(version)));
    }

    @GetMapping("/enemy-pool")
    @Operation(summary = "Get STS enemy pool", description = "Returns the raw EnemyPool JSON from the latest game config release.")
    public Object enemyPool(@RequestParam(required = false) String version) {
        return apiJson(gameDataService.enemyPool(resolveVersion(version)));
    }

    @GetMapping("/characters")
    @Operation(summary = "Get STS characters catalog", description = "Returns character JSON documents from the latest game config release.")
    public Object characters(@RequestParam(required = false) String version) {
        return apiJson(gameDataService.characters(resolveVersion(version)));
    }

    @GetMapping("/events")
    @Operation(summary = "Get STS events catalog", description = "Returns the STS event JSON from the latest game config release.")
    public Object events(@RequestParam(required = false) String version) {
        return apiJson(gameDataService.events(resolveVersion(version)));
    }

    private String resolveVersion(String requestedVersion) {
        return requestedVersion == null || requestedVersion.isBlank()
                ? gameDataService.latestVersion()
                : gameDataService.resolveVersion(requestedVersion);
    }

    private Object apiJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
    }
}
