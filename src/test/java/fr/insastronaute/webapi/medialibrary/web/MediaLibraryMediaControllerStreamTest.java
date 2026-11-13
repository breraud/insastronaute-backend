package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.medialibrary.service.AbortableTestStream;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryMediaService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryResolver;
import fr.insastronaute.webapi.medialibrary.service.MediaThumbnailService;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Une video arretee en cours de route : la tuile de la grille demande {@code bytes=0-}
 * et le navigateur coupe des qu'il a la premiere image. Le flux S3 doit alors etre
 * abandonne ; le fermer seul lirait le reste de l'objet, jusqu'a 40 Mio par tuile.
 */
class MediaLibraryMediaControllerStreamTest {

    private final MediaLibraryMediaService mediaService = mock(MediaLibraryMediaService.class);
    private final MediaLibraryMediaController controller =
            new MediaLibraryMediaController(mediaService, mock(MediaThumbnailService.class));

    private final UUID submissionId = UUID.randomUUID();
    private final OidcUser staff = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
            OidcIdToken.withTokenValue("token").subject("staff-sub")
                    .issuedAt(Instant.parse("2026-10-02T08:00:00Z")).expiresAt(Instant.parse("2126-10-02T08:00:00Z"))
                    .build());
    private final MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/media-library/media/x/0/content");

    @Test
    void aClientLeavingMidFileAbandonsTheStorageStream() {
        AbortableTestStream source = givenAVideo(AbortableTestStream.of(new byte[256 * 1024]));

        assertThatThrownBy(() -> controller.content(submissionId, 0, false, "bytes=0-", staff, get,
                        new ClientGoneResponse()))
                .isInstanceOf(IOException.class);

        assertThat(source.events()).containsExactly("abort", "close");
    }

    @Test
    void aStorageFailureMidFileAbandonsTheStorageStream() {
        AbortableTestStream source = givenAVideo(AbortableTestStream.failingAfter(new byte[1024]));

        assertThatThrownBy(() -> controller.content(submissionId, 0, false, "bytes=0-", staff, get,
                        new MockHttpServletResponse()))
                .isInstanceOf(IOException.class);

        assertThat(source.events()).containsExactly("abort", "close");
    }

    @Test
    void aFileReadToTheEndIsClosedWithoutBeingAbandoned() throws Exception {
        AbortableTestStream source = givenAVideo(AbortableTestStream.of(new byte[1024]));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.content(submissionId, 0, false, "bytes=0-", staff, get, response);

        assertThat(response.getContentAsByteArray()).hasSize(1024);
        assertThat(source.events()).containsExactly("close");
    }

    private AbortableTestStream givenAVideo(AbortableTestStream source) {
        MediaLibraryResolver.ResolvedMedia media = new MediaLibraryResolver.ResolvedMedia(
                submissionId, 0, "proofs/u/c/a.mp4", "video/mp4", UUID.randomUUID(), "Camille", "Défi", Instant.EPOCH);
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), any(), anyBoolean()))
                .thenReturn(new MediaLibraryMediaService.OpenedMedia(media,
                        new ProofMediaStream("video/mp4", 40L * 1024 * 1024, 0, 40L * 1024 * 1024 - 1, true, source), "x.mp4"));
        return source;
    }

    /** Le navigateur est parti : la premiere ecriture echoue. */
    private static final class ClientGoneResponse extends MockHttpServletResponse {
        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(int value) throws IOException {
                    throw new IOException("Broken pipe");
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }
            };
        }
    }
}
