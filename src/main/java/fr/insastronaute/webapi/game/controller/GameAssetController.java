package fr.insastronaute.webapi.game.controller;

import fr.insastronaute.webapi.game.dto.GameAssetReleaseDto;
import fr.insastronaute.webapi.game.service.GameAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Game Assets")
public class GameAssetController {

    private final GameAssetService gameAssetService;

    public GameAssetController(GameAssetService gameAssetService) {
        this.gameAssetService = gameAssetService;
    }

    @GetMapping("/api/public/game")
    @Operation(summary = "Get active game assets", description = "Returns the public S3 folder URL for the active game build.")
    public GameAssetReleaseDto getActiveRelease() {
        return gameAssetService.getActiveRelease();
    }
}
