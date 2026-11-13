package fr.insastronaute.webapi.common.web;

import fr.insastronaute.webapi.cardteam.service.CardTeamRoleDirectoryUnavailableException;
import fr.insastronaute.webapi.common.security.roles.StudentLimitedActionForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void studentLimitedActionsReturnCodedForbiddenResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/challenges");

        ResponseEntity<ApiErrorResponse> response = handler.handleStudentLimitedForbidden(
                new StudentLimitedActionForbiddenException(),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("STUDENT_LIMITED_ACTION_FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Cette action n'est pas autorisee pour ce compte.");
    }

    @Test
    void rateLimitResponseContainsRetryHeaderAndStableCode() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/treasure-hunt/checkpoints/validate"
        );

        ResponseEntity<TooManyRequestsResponse> response = handler.handleRateLimitExceeded(
                new RateLimitExceededException(4_250),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().remainingCooldownMs()).isEqualTo(4_250);
    }

    @Test
    void unavailableRateLimiterReturnsServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/treasure-hunt/checkpoints/validate"
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimitUnavailable(
                new RateLimitUnavailableException(),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Le contrôle de débit est temporairement indisponible.");
    }

    @Test
    void unavailableCardTeamRoleDirectoryReturnsServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/card-teams/invitations");

        ResponseEntity<ApiErrorResponse> response = handler.handleCardTeamRoleDirectoryUnavailable(
                new CardTeamRoleDirectoryUnavailableException(),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("L'annuaire des rôles est temporairement indisponible.");
    }
    /**
     * Le cas qui a fuite : une erreur JDBC porte la requete SQL entiere, donc la
     * liste des colonnes de la table interrogee. Publiee telle quelle, elle a
     * livre le schema de {@code users} - {@code keycloak_sub}, {@code email},
     * {@code onboarding_rejection_reason} - a qui appelait la route.
     */
    @Test
    void anUnexpectedFailureTellsTheClientNothingAboutTheSchema() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/action-events");
        Exception jdbc = new IllegalStateException(
                "JDBC exception executing SQL [ERROR: could not determine data type of parameter $18] "
                        + "[select aae1_0.id, a1_0.keycloak_sub, a1_0.email from admin_action_events aae1_0]");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(jdbc, request, new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        String message = response.getBody().message();
        assertThat(message)
                .doesNotContain("keycloak_sub")
                .doesNotContain("email")
                .doesNotContain("admin_action_events")
                .doesNotContain("select")
                .doesNotContain("IllegalStateException");
    }

    /** Le diagnostic n'est pas perdu : il change de canal, et la reference le relie au journal. */
    @Test
    void anUnexpectedFailureHandsBackAReferenceToQuoteBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");

        ResponseEntity<ApiErrorResponse> first = handler.handleUnexpected(new RuntimeException("boom"), request, new MockHttpServletResponse());
        ResponseEntity<ApiErrorResponse> second = handler.handleUnexpected(new RuntimeException("boom"), request, new MockHttpServletResponse());

        assertThat(first.getBody().message()).containsIgnoringCase("reference");
        // Deux incidents, deux references : sinon elle ne designe rien dans le journal.
        assertThat(first.getBody().message()).isNotEqualTo(second.getBody().message());
    }

    @Test
    void unexpectedFailureExplicitlyOverridesAStreamingMediaTypeWithJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/moderation/submissions/submission/proofs/0/content");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("stream failed"), request, new MockHttpServletResponse());

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void disconnectedStreamingClientIsHandledWithoutASecondResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/moderation/submissions/submission/proofs/0/content");

        handler.handleDisconnectedClient(
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client"),
                request);
    }

    /**
     * Le client coupe pendant que le JSON s'ecrit : Spring enveloppe alors la
     * deconnexion dans un HttpMessageNotWritableException, que le filet general
     * attrape avant que le gestionnaire des deconnexions ne voie la cause. Observe
     * en production sur GET /api/challenges/department-shares, journalise en
     * « Erreur inattendue » avec une reference que personne n'avait a citer.
     */
    @Test
    void aClientLeavingWhileTheJsonIsWrittenIsNotAnIncident(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/challenges/department-shares");
        Exception wrapped = new HttpMessageNotWritableException(
                "Could not write JSON: ServletOutputStream failed to write: java.io.IOException: Broken pipe",
                new AsyncRequestNotUsableException(
                        "ServletOutputStream failed to write: java.io.IOException: Broken pipe",
                        new IOException("Broken pipe")));

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(wrapped, request, new MockHttpServletResponse());

        // La reponse est deja inutilisable : en ecrire une seconde ne ferait que relancer l'erreur.
        assertThat(response).isNull();
        assertThat(output).doesNotContain("Erreur inattendue");
    }

    @Test
    void aJsonWritingFailureThatIsNotADisconnectionStaysAnIncident(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/challenges/department-shares");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new HttpMessageNotWritableException("Could not write JSON: infinite recursion"), request,
                new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(output).contains("Erreur inattendue");
    }

    // ── Une reponse deja commencee ──────────────────────────────────────────────

    /**
     * Des octets sont deja partis : un corps JSON ne ferait que s'ajouter au fichier, et
     * la reponse se terminerait proprement. L'exception d'origine, relancee, fait renoncer
     * Spring ; Tomcat coupe alors la connexion et le navigateur voit l'echec.
     */
    @Test
    void anUnexpectedFailureAfterTheFirstByteIsRethrownAsIs(CapturedOutput output) {
        MockHttpServletRequest request = mediaRequest();
        IOException failure = new IOException("Panne S3 au milieu du fichier");

        assertThatThrownBy(() -> handler.handleUnexpected(failure, request, committed())).isSameAs(failure);

        // Le journal nomme la route, jamais la preuve qu'on lisait.
        assertThat(output)
                .contains("/api/media-library/media/{submissionId}/{index}/content")
                .doesNotContain(SUBMISSION_ID);
    }

    @Test
    void storageGoingDownAfterTheFirstByteIsRethrownAsIs(CapturedOutput output) {
        MockHttpServletRequest request = mediaRequest();
        StorageUnavailableException failure = new StorageUnavailableException("Le stockage des médias ne répond pas.", null);

        assertThatThrownBy(() -> handler.handleStorageUnavailable(failure, request, committed())).isSameAs(failure);

        assertThat(output).doesNotContain(SUBMISSION_ID);
    }

    @Test
    void storageGoingDownBeforeTheFirstByteStillAnswers503() {
        ResponseEntity<ApiErrorResponse> response = handler.handleStorageUnavailable(
                new StorageUnavailableException("Le stockage des médias ne répond pas.", null),
                mediaRequest(), new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).isEqualTo("Le stockage des médias ne répond pas.");
    }

    @Test
    void anUnexpectedFailureBeforeTheFirstByteStillAnswers500() throws Exception {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IOException("Panne S3 avant le premier octet"), mediaRequest(), new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).containsIgnoringCase("reference");
    }

    /** Un client parti en cours de route n'est toujours pas un incident, reponse commencee ou non. */
    @Test
    void aClientGoneAfterTheFirstByteIsStillNotAnIncident(CapturedOutput output) throws Exception {
        Exception gone = new AsyncRequestNotUsableException("ServletOutputStream failed to write",
                new IOException("Broken pipe"));

        assertThat(handler.handleUnexpected(gone, mediaRequest(), committed())).isNull();
        assertThat(output).doesNotContain("Erreur");
    }

    private static final String SUBMISSION_ID = "6f1c2a7e-0d3b-4c51-9a8e-2b7d4e5f6a70";

    private static MockHttpServletRequest mediaRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/media-library/media/" + SUBMISSION_ID + "/0/content");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/media-library/media/{submissionId}/{index}/content");
        return request;
    }

    private static MockHttpServletResponse committed() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        return response;
    }

    /** Les statuts que Spring Security produit doivent continuer de passer intacts. */
    @Test
    void securityFailuresKeepTheirOwnStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");

        assertThat(handler.handleUnexpected(
                new org.springframework.security.authorization.AuthorizationDeniedException("refuse"), request,
                new MockHttpServletResponse())
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
