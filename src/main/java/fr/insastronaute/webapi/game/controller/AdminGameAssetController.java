package fr.insastronaute.webapi.game.controller;

import fr.insastronaute.webapi.game.dto.GameAssetLimitsDto;
import fr.insastronaute.webapi.game.dto.GameAssetReleaseDto;
import fr.insastronaute.webapi.game.service.GameAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/game")
@Tag(name = "Admin Game Assets")
public class AdminGameAssetController {

    private final GameAssetService gameAssetService;
    private final GameAssetLimitsDto limits;

    public AdminGameAssetController(
            GameAssetService gameAssetService,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize,
            @Value("${spring.servlet.multipart.max-request-size}") DataSize maxRequestSize,
            @Value("${server.tomcat.max-part-count}") int maxPartCount
    ) {
        this.gameAssetService = gameAssetService;
        this.limits = new GameAssetLimitsDto(maxFileSize.toBytes(), maxRequestSize.toBytes(), maxPartCount);
    }

    /**
     * Les bornes que le panel doit verifier avant d'envoyer.
     *
     * <p>Un envoi trop gros est refuse par Tomcat des l'en-tete, avant la moindre ligne de
     * code applicatif : la connexion se ferme pendant que le navigateur ecrit encore, et
     * ce qu'il en rapporte est une erreur reseau illisible. Mieux vaut ne pas partir.</p>
     */
    @GetMapping("/assets/limits")
    @Operation(
            summary = "Upload limits for a game build",
            description = "The multipart limits actually in force, so the panel can refuse an upload that would be "
                    + "rejected by the container before reaching any application code."
    )
    public GameAssetLimitsDto limits() {
        return limits;
    }

    @PostMapping(path = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish game assets", description = "Uploads a game build folder to public S3 and switches the active version.")
    public GameAssetReleaseDto publish(
            @RequestParam String version,
            @RequestParam List<MultipartFile> files
    ) {
        return gameAssetService.publish(version, files);
    }
}
