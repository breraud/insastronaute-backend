package fr.insastronaute.webapi.common.web;

import fr.insastronaute.webapi.cardteam.service.CardTeamRoleDirectoryUnavailableException;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditUnavailableException;
import fr.insastronaute.webapi.common.security.roles.StudentLimitedActionForbiddenException;
import fr.insastronaute.webapi.medialibrary.service.RoleDirectoryUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleDisconnectedClient(
            AsyncRequestNotUsableException exception,
            HttpServletRequest request
    ) {
        LOGGER.debug("Client déconnecté pendant {} {}", request.getMethod(), request.getRequestURI());
    }

    @ExceptionHandler(CodedBadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleCodedBadRequest(
            CodedBadRequestException exception,
            HttpServletRequest request
    ) {
        return codedError(
                HttpStatus.BAD_REQUEST,
                exception.getCode(),
                exception.getMessage(),
                request,
                exception.getFieldErrors()
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            BadRequestException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, exception.getFieldErrors());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            ForbiddenException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(StudentLimitedActionForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleStudentLimitedForbidden(
            StudentLimitedActionForbiddenException exception,
            HttpServletRequest request
    ) {
        return codedError(
                HttpStatus.FORBIDDEN,
                "STUDENT_LIMITED_ACTION_FORBIDDEN",
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(CodedConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleCodedConflict(
            CodedConflictException exception,
            HttpServletRequest request
    ) {
        return codedError(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<TooManyRequestsResponse> handleTooManyRequests(
            TooManyRequestsException exception,
            HttpServletRequest request
    ) {
        long retryAfterSeconds = Math.max(1, (exception.getRemainingCooldownMs() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(new TooManyRequestsResponse(
                        fr.insastronaute.webapi.common.timetravel.TimeTravelContext.offsetDateTimeNow(),
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        exception.getRemainingCooldownMs(),
                        "PLACE_COOLDOWN"
                ));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<TooManyRequestsResponse> handleRateLimitExceeded(
            RateLimitExceededException exception,
            HttpServletRequest request
    ) {
        long retryAfterSeconds = Math.max(1, (exception.getRetryAfterMs() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(new TooManyRequestsResponse(
                        fr.insastronaute.webapi.common.timetravel.TimeTravelContext.offsetDateTimeNow(),
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        exception.getRetryAfterMs(),
                        "RATE_LIMITED"
                ));
    }

    @ExceptionHandler(RateLimitUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitUnavailable(
            RateLimitUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleStorageUnavailable(
            StorageUnavailableException exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (response.isCommitted()) {
            // S3 tombe avant une entree suivante d'un export : meme cas que le filet general.
            throw abandonCommittedResponse(exception, request);
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(CardTeamRoleDirectoryUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleCardTeamRoleDirectoryUnavailable(
            CardTeamRoleDirectoryUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(RoleDirectoryUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaLibraryRoleDirectoryUnavailable(
            RoleDirectoryUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(ModerationAuditUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleModerationAuditUnavailable(
            ModerationAuditUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldViolation> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ApiErrorResponse.FieldViolation(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        boolean missingShopSlot = exception.getBindingResult().getFieldErrors().stream()
                .anyMatch(fieldError -> (fieldError.getObjectName().equals("shopOrderRequest")
                        || fieldError.getObjectName().equals("shopOrderSlotRequest"))
                        && fieldError.getField().equals("slotId")
                        && fieldError.getRejectedValue() == null);
        String code = missingShopSlot ? "SHOP_SLOT_REQUIRED" : exception.getBindingResult().getFieldErrors().stream()
                .anyMatch(fieldError -> fieldError.getObjectName().equals("placeConfigUpdateDto")
                        && (fieldError.getField().equals("canvasWidth")
                        || fieldError.getField().equals("canvasHeight"))
                        && fieldError.getRejectedValue() instanceof Number number
                        && number.longValue() > 2000)
                ? "PLACE_DIMENSIONS_TOO_LARGE" : null;

        return codedError(
                HttpStatus.BAD_REQUEST,
                code,
                fieldErrors.size() == 1 ? fieldErrors.getFirst().message() : "La requête contient des champs invalides.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            TypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, clientMessage(exception), request);
    }

    @ExceptionHandler({
            PropertyReferenceException.class,
            InvalidDataAccessApiUsageException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidDataAccessRequest(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        if (containsPropertyReferenceCause(exception) || looksLikeInvalidProperty(exception)) {
            return error(HttpStatus.BAD_REQUEST, "Paramètre de tri invalide.", request);
        }

        return error(HttpStatus.BAD_REQUEST, "Requête invalide.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(
            HttpServletRequest request
    ) {
        if (AppErrorController.isBrowserRequest(request)) {
            return AppErrorController.htmlError(HttpStatus.NOT_FOUND, request.getRequestURI());
        }

        return error(HttpStatus.NOT_FOUND, "Ressource introuvable.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(
            HttpServletRequest request
    ) {
        if (AppErrorController.isBrowserRequest(request)) {
            return AppErrorController.htmlError(HttpStatus.METHOD_NOT_ALLOWED, request.getRequestURI());
        }

        return error(HttpStatus.METHOD_NOT_ALLOWED, "Méthode HTTP non autorisée pour cette ressource.", request);
    }

    private static String clientMessage(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException) {
            return "Corps de requête invalide.";
        }
        if (exception instanceof MissingServletRequestParameterException missingParameter) {
            return "Paramètre requis manquant : " + missingParameter.getParameterName() + ".";
        }
        if (exception instanceof TypeMismatchException) {
            return "Paramètre de requête invalide.";
        }
        if (exception instanceof HandlerMethodValidationException) {
            return "La requête contient des paramètres invalides.";
        }
        return "Requête invalide.";
    }

    private static boolean containsPropertyReferenceCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PropertyReferenceException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean looksLikeInvalidProperty(Throwable exception) {
        String message = exception.getMessage();
        return message != null && message.contains("No property") && message.contains("found for type");
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return error(status, message, request, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> fieldErrors
    ) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ApiErrorResponse(
                fr.insastronaute.webapi.common.timetravel.TimeTravelContext.offsetDateTimeNow(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }

    private static ResponseEntity<ApiErrorResponse> codedError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> fieldErrors
    ) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ApiErrorResponse(
                fr.insastronaute.webapi.common.timetravel.TimeTravelContext.offsetDateTimeNow(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors,
                code
        ));
    }

    // Catch-all for unexpected exceptions (NPEs, IllegalStateException, etc.); without this they fall through
    // to Spring's default error handling which returns an empty 500 body with no diagnostic information.
    // Spring Security exceptions (AuthenticationException -> 401, AuthorizationDeniedException -> 403) and
    // ResponseStatusException (4xx) are excluded so they pass through with their correct status.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws Exception {
        // Un client qui part pendant l'ecriture du JSON arrive ici enveloppe dans un
        // HttpMessageNotWritableException : Spring choisit le gestionnaire sur le type
        // exterieur, et celui-ci l'emporte sur handleDisconnectedClient avant que la
        // cause ne soit regardee. Ce n'est pas un incident, et la reponse est deja
        // inutilisable : rien a journaliser en erreur, rien a renvoyer.
        if (causedByDisconnectedClient(exception)) {
            LOGGER.debug("Client déconnecté pendant {} {}", request.getMethod(), request.getRequestURI());
            return null;
        }
        if (response.isCommitted()) {
            throw abandonCommittedResponse(exception, request);
        }
        if (exception instanceof org.springframework.web.server.ResponseStatusException rse
                && rse.getStatusCode().is4xxClientError()) {
            return error(HttpStatus.valueOf(rse.getStatusCode().value()), rse.getMessage(), request);
        }
        if (exception instanceof org.springframework.security.authorization.AuthorizationDeniedException) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
        }
        if (exception instanceof org.springframework.security.core.AuthenticationException) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
        }
        return unexpected(exception, request);
    }

    /**
     * Une panne apres le premier octet : le statut et une partie du fichier sont deja
     * partis.
     *
     * <p>Repondre ici ne servirait a rien : le corps JSON s'ajouterait au fichier, ou
     * echouerait contre son type. Pire, une reponse terminee proprement ferait croire au
     * navigateur qu'un zip ou une video tronques sont complets. L'exception d'origine est
     * donc rendue telle quelle : Spring renonce sans rien ecrire, la relance jusqu'au
     * conteneur, et Tomcat ferme la connexion sans le dernier morceau. Le client voit un
     * telechargement echoue.</p>
     *
     * <p>Le journal nomme la route et non l'adresse : celle d'un media porte
     * l'identifiant de la soumission, celle d'un export l'identifiant de l'export.</p>
     */
    private static <E extends Exception> E abandonCommittedResponse(E exception, HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        LOGGER.error("Panne après le début de la réponse sur {} {} : connexion coupée",
                request.getMethod(), route == null ? "(route inconnue)" : route, exception);
        return exception;
    }

    private static boolean causedByDisconnectedClient(Throwable exception) {
        // Borne plutot que confiance : une chaine de causes peut boucler sur elle-meme.
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Une erreur inattendue, diagnosticable sans etre publiee.
     *
     * <p>Le detail partait auparavant dans le corps de la reponse : classe de
     * l'exception et message, tels quels. L'intention etait juste - un 500 vide est
     * illisible en exploitation - mais le destinataire ne l'etait pas. Une erreur
     * JDBC porte la requete SQL entiere, donc la liste des colonnes de la table
     * qu'elle interrogeait ; c'est ainsi que le schema de {@code users},
     * {@code keycloak_sub} et {@code email} compris, est parti chez un client.</p>
     *
     * <p>Le diagnostic n'est pas perdu pour autant, il change de canal : la trace
     * complete va au journal, et l'appelant ne recoit qu'un identifiant. Celui qui
     * signale le probleme donne cet identifiant, et une recherche dans le journal
     * rend l'exception exacte - ce que le message publie donnait, sans le donner a
     * tout le monde.</p>
     */
    private static ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        String incident = UUID.randomUUID().toString().substring(0, 8);
        LOGGER.error("Erreur inattendue [{}] sur {} {}", incident, request.getMethod(), request.getRequestURI(), exception);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue. Communiquez la reference " + incident + " pour qu'on la retrouve.",
                request);
    }
}
