package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.service.MediaLibraryMediaService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryStorage;
import fr.insastronaute.webapi.medialibrary.service.MediaThumbnailService;
import fr.insastronaute.webapi.proof.service.ProofRangeNotSatisfiableException;
import fr.insastronaute.webapi.proof.web.ProofMediaHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Les fichiers de la Mediatheque.
 *
 * <p>Ecrits a la main dans la reponse, et non par un StreamingResponseBody : le projet ne
 * regle pas spring.mvc.async.request-timeout, et le delai asynchrone de Tomcat (30 s)
 * couperait une video telechargee sur une connexion lente.</p>
 */
@RestController
@RequestMapping("/api/media-library/media")
@Tag(name = "Media Library")
public class MediaLibraryMediaController {

    private final MediaLibraryMediaService mediaService;
    private final MediaThumbnailService thumbnailService;

    public MediaLibraryMediaController(MediaLibraryMediaService mediaService, MediaThumbnailService thumbnailService) {
        this.mediaService = mediaService;
        this.thumbnailService = thumbnailService;
    }

    @ExceptionHandler(ProofRangeNotSatisfiableException.class)
    public ResponseEntity<Void> invalidRange(ProofRangeNotSatisfiableException exception) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + exception.objectSize());
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, private, max-age=0");
        return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    @GetMapping("/{submissionId}/{index}/content")
    @Operation(summary = "Read or download a proof file", description = "Range-capable. download=1 adds an attachment disposition.")
    public void content(@PathVariable UUID submissionId,
                        @PathVariable int index,
                        @RequestParam(defaultValue = "false") boolean download,
                        @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                        @AuthenticationPrincipal OidcUser oidcUser,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        if (refusedHead(request, response)) {
            return;
        }
        MediaLibraryMediaService.OpenedMedia opened = mediaService.openContent(
                oidcUser.getSubject(), Authorities.of(oidcUser), submissionId, index, range, download);
        try (InputStream input = opened.stream().inputStream()) {
            try {
                HttpHeaders headers = ProofMediaHeaders.forMedia(opened.stream());
                if (download) {
                    headers.setContentDisposition(ContentDisposition.attachment().filename(opened.downloadName()).build());
                }
                response.setStatus(opened.stream().partial() ? HttpStatus.PARTIAL_CONTENT.value() : HttpStatus.OK.value());
                ProofMediaHeaders.writeTo(headers, response);
                input.transferTo(response.getOutputStream());
            } catch (IOException | RuntimeException exception) {
                // La tuile video que le navigateur coupe a la premiere image passe ici.
                MediaLibraryStorage.abandon(input);
                throw exception;
            }
        } catch (IOException | RuntimeException exception) {
            UnsentResponse.discard(response);
            throw exception;
        }
    }

    @GetMapping("/{submissionId}/{index}/thumbnail")
    @Operation(summary = "Proof thumbnail", description = "480 px JPEG, or the original when it cannot be decoded. 404 for videos.")
    public void thumbnail(@PathVariable UUID submissionId,
                          @PathVariable int index,
                          @AuthenticationPrincipal OidcUser oidcUser,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        if (refusedHead(request, response)) {
            return;
        }
        MediaThumbnailService.Thumbnail thumbnail = thumbnailService.thumbnail(
                oidcUser.getSubject(), Authorities.of(oidcUser), submissionId, index);
        response.setStatus(HttpStatus.OK.value());
        ProofMediaHeaders.writeTo(ProofMediaHeaders.forBytes(thumbnail.contentType(), thumbnail.bytes().length), response);
        response.getOutputStream().write(thumbnail.bytes());
    }

    /**
     * HEAD est refuse avant tout appel.
     *
     * <p>Spring repond a HEAD avec le gestionnaire de GET, mais RateLimitInterceptor ne
     * compte que GET. Un HEAD passait donc la limite, lisait l'objet S3 entier pour n'en
     * rien envoyer, publiait une lecture et journalisait un telechargement qui n'avait pas
     * eu lieu.</p>
     */
    private static boolean refusedHead(HttpServletRequest request, HttpServletResponse response) {
        if (!HttpMethod.HEAD.matches(request.getMethod())) {
            return false;
        }
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        response.setHeader(HttpHeaders.ALLOW, HttpMethod.GET.name());
        return true;
    }
}
