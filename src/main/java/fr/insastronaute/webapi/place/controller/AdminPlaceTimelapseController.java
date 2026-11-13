package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.place.domain.PlaceTimelapseColouring;
import fr.insastronaute.webapi.place.service.PlaceTimelapseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;

/**
 * L'historique des poses, rendu rejouable.
 *
 * <p>Reserve aux administrateurs comme tout {@code /api/admin/**}. L'historique sert
 * d'abord a la moderation : il dit qui a pose quoi, et n'a pas a s'ouvrir plus loin.
 * Le flux exporte ici n'emporte d'ailleurs ni auteur ni horodatage — seulement des
 * couleurs et des positions.</p>
 */
@RestController
@RequestMapping("/api/admin/place/timelapse")
@Tag(name = "Admin Place")
public class AdminPlaceTimelapseController {

    private final PlaceTimelapseService timelapseService;

    public AdminPlaceTimelapseController(PlaceTimelapseService timelapseService) {
        this.timelapseService = timelapseService;
    }

    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(
            summary = "Replay stream of the canvas over a period",
            description = "A compact binary stream — 10-byte header, then 7 bytes per placement (x, y, RGB) in "
                    + "placement order. The browser turns it into a video; the server does no rendering. Only the "
                    + "active generation is included, so coordinates match the exported size. colorBy=DEPARTMENT "
                    + "repaints each placement in its author's department colour."
    )
    public ResponseEntity<StreamingResponseBody> timelapse(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "PLACED") PlaceTimelapseColouring colorBy
    ) {
        // La demande se juge maintenant, pas dans le corps : celui-ci s'ecrit apres que
        // le statut est parti, et un refus decouvert la-bas ne donnerait pas une erreur
        // mais un fichier tronque.
        PlaceTimelapseService.Replay replay = timelapseService.prepare(colorBy, from, to);
        StreamingResponseBody body = out -> timelapseService.writeTo(out, replay);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"place-timelapse-" + colorBy.name().toLowerCase() + ".pltl\"")
                .body(body);
    }
}
