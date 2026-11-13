package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.common.web.ApiExceptionHandler;
import fr.insastronaute.webapi.common.web.AppErrorController;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryExportService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryMediaService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryResolver;
import fr.insastronaute.webapi.medialibrary.service.MediaThumbnailService;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Une panne apres le premier octet doit couper la connexion, dans un vrai Tomcat.
 *
 * <p>MockMvc ne le montre pas : la coupure est l'affaire du conteneur. Le test monte
 * donc un Tomcat reduit au strict necessaire - Spring MVC, ApiExceptionHandler et la page
 * d'erreur de l'application - et lit la reponse octet par octet. Une reponse terminee
 * proprement (dernier morceau {@code 0}, connexion gardee ouverte) ferait croire au
 * navigateur qu'un zip tronque est complet.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = MediaLibraryBrokenStreamTest.WebOnly.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaLibraryBrokenStreamTest {

    private static final int SENT_BEFORE_FAILURE = 200_000;

    @LocalServerPort private int port;
    @MockitoBean private MediaLibraryExportService exportService;
    @MockitoBean private MediaLibraryMediaService mediaService;
    @MockitoBean private MediaThumbnailService thumbnailService;

    @Test
    void anS3ReadFailureMidPartCutsTheConnection(CapturedOutput output) throws Exception {
        UUID exportId = givenAPartThatFailsMidway(new IOException("Panne S3 simulee au milieu d'un fichier"));

        RawResponse response = get("/api/media-library/exports/" + exportId + "/parts/1");

        assertThat(response.statusLine()).contains(" 200 ");
        assertThat(response.header("transfer-encoding")).isEqualToIgnoringCase("chunked");
        assertThat(response.bodyLength()).isGreaterThan(SENT_BEFORE_FAILURE);
        assertThat(response.endsWithLastChunk()).as("aucun dernier morceau : le zip ne parait pas complet").isFalse();
        assertThat(response.closedByServer()).as("la connexion est coupee, pas gardee ouverte").isTrue();
        // Une panne, une ligne : la page d'erreur que Tomcat inclut ensuite n'ajoute rien.
        assertThat(output.getOut())
                .containsOnlyOnce("Panne après le début de la réponse")
                .doesNotContain("GET /error")
                .doesNotContain(exportId.toString());
    }

    @Test
    void storageGoingDownBeforeALaterEntryCutsTheConnectionToo() throws Exception {
        UUID exportId = givenAPartThatFailsMidway(new StorageUnavailableException("Le stockage des médias ne répond pas.", null));

        RawResponse response = get("/api/media-library/exports/" + exportId + "/parts/1");

        assertThat(response.statusLine()).contains(" 200 ");
        assertThat(response.endsWithLastChunk()).isFalse();
        assertThat(response.closedByServer()).isTrue();
    }

    @Test
    void aFileCutMidwayIsShorterThanItsContentLengthAndTheConnectionIsCut() throws Exception {
        UUID submissionId = UUID.randomUUID();
        MediaLibraryResolver.ResolvedMedia media = new MediaLibraryResolver.ResolvedMedia(
                submissionId, 0, "proofs/u/c/a.mp4", "video/mp4", UUID.randomUUID(), "Camille", "Défi", Instant.EPOCH);
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), any(), anyBoolean()))
                .thenReturn(new MediaLibraryMediaService.OpenedMedia(media,
                        ProofMediaStream.full("video/mp4", 1_000_000, failingAfter(SENT_BEFORE_FAILURE)), "x.mp4"));

        RawResponse response = get("/api/media-library/media/" + submissionId + "/0/content");

        assertThat(response.statusLine()).contains(" 200 ");
        assertThat(response.header("content-length")).isEqualTo("1000000");
        assertThat(response.bodyLength()).isLessThan(1_000_000);
        assertThat(response.closedByServer()).isTrue();
    }

    /** Rien n'est encore parti : la reponse redevient une erreur JSON, sans rien du zip. */
    @Test
    void aFailureBeforeTheFirstByteIsACleanJsonError() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(exportService.acquireSlot()).thenReturn(null);
        when(exportService.preparePart(eq("staff-sub"), any(), eq(exportId), anyInt()))
                .thenReturn(new MediaLibraryExportService.PartPlan(Instant.parse("2026-10-02T08:00:00Z"), 1, 1, List.of()));
        doAnswer(call -> {
            throw new StorageUnavailableException("Le stockage des médias ne répond pas.", null);
        }).when(exportService).writePart(any(), any());

        RawResponse response = get("/api/media-library/exports/" + exportId + "/parts/1");

        assertThat(response.statusLine()).contains(" 503 ");
        assertThat(response.header("content-type")).startsWith("application/json");
        assertThat(response.header("content-disposition")).isNull();
        assertThat(response.text()).contains("Le stockage des médias ne répond pas.");
    }

    /**
     * Le fichier annonce sa taille avant le premier octet : une erreur qui garderait ce
     * Content-Length ferait attendre au client des octets qui ne viendront jamais.
     */
    @Test
    void aFileFailingBeforeItsFirstByteIsAJsonErrorWithoutTheFileHeaders() throws Exception {
        UUID submissionId = UUID.randomUUID();
        MediaLibraryResolver.ResolvedMedia media = new MediaLibraryResolver.ResolvedMedia(
                submissionId, 0, "proofs/u/c/a.mp4", "video/mp4", UUID.randomUUID(), "Camille", "Défi", Instant.EPOCH);
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), any(), anyBoolean()))
                .thenReturn(new MediaLibraryMediaService.OpenedMedia(media,
                        new ProofMediaStream("video/mp4", 1_000_000, 0, 999, true, failingAfter(0)), "x.mp4"));

        RawResponse response = get("/api/media-library/media/" + submissionId + "/0/content");

        assertThat(response.statusLine()).contains(" 500 ");
        assertThat(response.header("content-type")).startsWith("application/json");
        assertThat(response.header("content-range")).as("%s", response.head()).isNull();
        assertThat(response.header("content-length")).isNotEqualTo("1000");
        assertThat(response.text()).contains("reference");
    }

    private UUID givenAPartThatFailsMidway(Exception failure) throws IOException {
        UUID exportId = UUID.randomUUID();
        when(exportService.acquireSlot()).thenReturn(null);
        when(exportService.preparePart(eq("staff-sub"), any(), eq(exportId), anyInt()))
                .thenReturn(new MediaLibraryExportService.PartPlan(Instant.parse("2026-10-02T08:00:00Z"), 1, 1, List.of()));
        doAnswer(call -> {
            OutputStream out = call.getArgument(1);
            out.write(new byte[SENT_BEFORE_FAILURE]);
            out.flush();
            throw failure;
        }).when(exportService).writePart(any(), any());
        return exportId;
    }

    /** Des octets, puis une panne : un flux S3 qui casse en cours de lecture. */
    private static InputStream failingAfter(int bytes) {
        return new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position >= bytes) {
                    throw new IOException("Panne S3 simulee au milieu du fichier");
                }
                position++;
                return 0;
            }
        };
    }

    /**
     * Une requete HTTP/1.1 brute, en keep-alive : une reponse terminee proprement laisse la
     * connexion ouverte, et la lecture expire. Seule une coupure la ferme.
     */
    private RawResponse get(String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(3_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nAccept: */*\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            boolean closed;
            try {
                InputStream input = socket.getInputStream();
                for (int read; (read = input.read(buffer)) != -1; ) {
                    received.write(buffer, 0, read);
                }
                closed = true;
            } catch (SocketTimeoutException stillOpen) {
                closed = false;
            } catch (SocketException reset) {
                closed = true;
            }
            return new RawResponse(received.toByteArray(), closed);
        }
    }

    private record RawResponse(byte[] bytes, boolean closedByServer) {

        private String head() {
            String all = new String(bytes, StandardCharsets.ISO_8859_1);
            int end = all.indexOf("\r\n\r\n");
            return end < 0 ? all : all.substring(0, end);
        }

        String statusLine() {
            return head().lines().findFirst().orElse("");
        }

        String header(String name) {
            return head().lines()
                    .filter(line -> line.toLowerCase(Locale.ROOT).startsWith(name + ":"))
                    .map(line -> line.substring(line.indexOf(':') + 1).trim())
                    .findFirst().orElse(null);
        }

        int bodyLength() {
            return bytes.length - head().length() - 4;
        }

        String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        boolean endsWithLastChunk() {
            return new String(bytes, StandardCharsets.ISO_8859_1).endsWith("\r\n0\r\n\r\n");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            TomcatServletWebServerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            ErrorMvcAutoConfiguration.class
    })
    @Import({
            MediaLibraryExportController.class,
            MediaLibraryMediaController.class,
            ApiExceptionHandler.class,
            AppErrorController.class
    })
    static class WebOnly implements WebMvcConfigurer {

        private static final OidcUser STAFF = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                OidcIdToken.withTokenValue("token").subject("staff-sub")
                        .issuedAt(Instant.parse("2026-10-02T08:00:00Z")).expiresAt(Instant.parse("2126-10-02T08:00:00Z"))
                        .build());

        /** Sans Spring Security : l'utilisateur connecte est fixe, un membre du staff. */
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType() == OidcUser.class;
                }

                @Override
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    return STAFF;
                }
            });
        }
    }
}
