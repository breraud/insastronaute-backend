package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.place.dto.PlaceBonusStatusDto;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentContributionsDto;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentDto;
import fr.insastronaute.webapi.place.dto.PlacePixelAuthorDto;
import fr.insastronaute.webapi.place.dto.PlacePixelRequest;
import fr.insastronaute.webapi.place.dto.PlacePixelUpdate;
import fr.insastronaute.webapi.place.dto.PlaceVisibilityDto;
import fr.insastronaute.webapi.place.service.PlaceCanvasSnapshot;
import fr.insastronaute.webapi.place.service.PlaceDepartmentFresqueService;
import fr.insastronaute.webapi.place.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/place")
@Tag(name = "Place")
public class PlaceController {

    private final PlaceService placeService;
    private final PlaceDepartmentFresqueService departmentFresque;
    private final fr.insastronaute.webapi.place.service.PlaceResultsService placeResultsService;

    public PlaceController(PlaceService placeService,
                           PlaceDepartmentFresqueService departmentFresque,
                           fr.insastronaute.webapi.place.service.PlaceResultsService placeResultsService) {
        this.placeService = placeService;
        this.departmentFresque = departmentFresque;
        this.placeResultsService = placeResultsService;
    }

    @PostMapping("/pixel")
    @Operation(
            summary = "Place a pixel",
            description = "Uses a challenge-earned bonus pixel first when available; otherwise uses the base cooldown. "
                    + "Returns 429 with remainingCooldownMs and Retry-After when the selected cooldown is active. "
                    + "Set bonus=true to pay tokens and skip the cooldown entirely; two paid pixels in a row are refused."
    )
    public PlacePixelUpdate placePixel(
            @AuthenticationPrincipal OidcUser oidcUser,
            @Valid @RequestBody PlacePixelRequest request
    ) {
        return placeService.placePixel(oidcUser.getSubject(), request);
    }

    @GetMapping("/pixel")
    @Operation(
            summary = "Read who painted a pixel",
            description = "Returns the author of one cell, when it was placed and the author's department, or nothing "
                    + "when the organisers chose not to show authors."
    )
    public ResponseEntity<PlacePixelAuthorDto> pixelAuthor(@RequestParam int x, @RequestParam int y) {
        return placeService.pixelAuthor(x, y)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/visibility")
    @Operation(
            summary = "Is r/place shown to players",
            description = "Tells the player front whether the event exists for it at all: active, and within its window. "
                    + "A hidden event is also closed to play."
    )
    public PlaceVisibilityDto getVisibility() {
        return new PlaceVisibilityDto(placeService.isEventVisible(), placeService.isEventOpen(),
                placeService.isDepartmentCanvasVisible(), placeService.showsResults());
    }

    @GetMapping("/bonus")
    @Operation(
            summary = "Bonus pixel price and availability",
            description = "Returns what the next paid pixel costs and whether it can be bought right now: a purchase "
                    + "needs enough tokens, and a normal-cooldown pixel since the last purchase."
    )
    public PlaceBonusStatusDto bonusStatus(@AuthenticationPrincipal OidcUser oidcUser) {
        return placeService.bonusStatus(oidcUser.getSubject());
    }

    @GetMapping("/results")
    @Operation(
            summary = "The finished fresco: rankings and totals",
            description = "Available once the event has ended and the admin has published the results. 404 otherwise: "
                    + "a ranking frozen while pixels still move would be wrong a second later."
    )
    public fr.insastronaute.webapi.place.dto.PlaceResultsDto results(@AuthenticationPrincipal OidcUser oidcUser) {
        return placeResultsService.results(oidcUser == null ? null : oidcUser.getSubject());
    }

    @GetMapping(value = "/canvas", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get the current canvas image", description = "Returns the active canvas as an image/png binary response.")
    public ResponseEntity<byte[]> canvas() {
        return png(placeService.canvasPng());
    }

    // ── Fresque des departements ────────────────────────────────────────────────
    //
    // Une lecture de la fresque libre, pas un second canevas : on n'y pose rien. Ses
    // mises a jour en direct passent par le topic de la fresque libre, dont chaque
    // pixel porte la couleur du departement de son auteur.

    @GetMapping(value = "/department/canvas", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(
            summary = "Get the department view of the canvas",
            description = "The free canvas with each pixel painted in its author's department colour. Carries the free "
                    + "canvas revision: its live updates are the free canvas topic, using departmentColor."
    )
    public ResponseEntity<byte[]> departmentCanvas() {
        return png(departmentFresque.snapshot());
    }

    @GetMapping("/department/contributions")
    @Operation(
            summary = "Share of the canvas held by each department",
            description = "Pixels currently showing each department's colour on the department view, every department "
                    + "listed, most present first."
    )
    public PlaceDepartmentContributionsDto departmentContributions() {
        return departmentFresque.contributions();
    }

    @GetMapping("/department/me")
    @Operation(summary = "The player's department and its colour", description = "No content when the player has no department.")
    public ResponseEntity<PlaceDepartmentDto> myDepartment(@AuthenticationPrincipal OidcUser oidcUser) {
        return placeService.departmentOf(oidcUser.getSubject())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static ResponseEntity<byte[]> png(PlaceCanvasSnapshot snapshot) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header("X-Place-Revision", Long.toString(snapshot.descriptor().revision()))
                .body(snapshot.png());
    }
}
