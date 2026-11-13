package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.challenge.audit.ModerationAuditUnavailableException;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryMediaService;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryResolver;
import fr.insastronaute.webapi.medialibrary.service.MediaThumbnailService;
import fr.insastronaute.webapi.medialibrary.service.MediaThumbnailService.Thumbnail;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import fr.insastronaute.webapi.proof.service.ProofRangeNotSatisfiableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaLibraryMediaControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MediaLibraryMediaService mediaService;
    @MockitoBean private MediaThumbnailService thumbnailService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private final UUID submissionId = UUID.randomUUID();
    private final MediaLibraryResolver.ResolvedMedia media = new MediaLibraryResolver.ResolvedMedia(
            submissionId, 0, "proofs/u/c/a.jpg", "image/jpeg", UUID.randomUUID(), "Camille", "Défi", Instant.EPOCH);

    @Test
    void aDownloadIsWrittenSynchronouslyAsAnAttachment() throws Exception {
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), eq(null), eq(true)))
                .thenReturn(new MediaLibraryMediaService.OpenedMedia(media,
                        ProofMediaStream.full("image/jpeg", 3, new ByteArrayInputStream(new byte[]{1, 2, 3})), "defi_camille_1970-01-01_1.jpg"));

        mockMvc.perform(get("/api/media-library/media/{id}/{index}/content", submissionId, 0).param("download", "1")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"defi_camille_1970-01-01_1.jpg\""))
                .andExpect(header().string("Cache-Control", "no-store, private, max-age=0"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void aPartialReadAnswers206AndABadRange416() throws Exception {
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), eq("bytes=1-1"), eq(false)))
                .thenReturn(new MediaLibraryMediaService.OpenedMedia(media,
                        new ProofMediaStream("image/jpeg", 3, 1, 1, true, new ByteArrayInputStream(new byte[]{2})), "x.jpg"));
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), eq("bytes=9-"), eq(false)))
                .thenThrow(new ProofRangeNotSatisfiableException(3));

        mockMvc.perform(get("/api/media-library/media/{id}/{index}/content", submissionId, 0).header("Range", "bytes=1-1")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1-1/3"));
        mockMvc.perform(get("/api/media-library/media/{id}/{index}/content", submissionId, 0).header("Range", "bytes=9-")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */3"));
    }

    @Test
    void aFailedReadAuditAnswers503() throws Exception {
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), eq(null), eq(false)))
                .thenThrow(new ModerationAuditUnavailableException(new RuntimeException("redis")));

        mockMvc.perform(get("/api/media-library/media/{id}/{index}/content", submissionId, 0)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("La journalisation de sécurité est temporairement indisponible."));
    }

    /**
     * S3 lache avant le premier octet : l'erreur ne garde ni la taille ni la plage du
     * fichier, qui feraient attendre au client des octets qui ne viendront jamais.
     */
    @Test
    void aReadFailingBeforeTheFirstByteIsAJsonErrorWithoutTheFileHeaders() throws Exception {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Panne S3 avant le premier octet");
            }
        };
        when(mediaService.openContent(eq("staff-sub"), any(), eq(submissionId), eq(0), eq("bytes=0-999"), eq(true)))
                .thenReturn(new MediaLibraryMediaService.OpenedMedia(media,
                        new ProofMediaStream("video/mp4", 1_000_000, 0, 999, true, failing), "defi_camille_1970-01-01_1.mp4"));

        mockMvc.perform(get("/api/media-library/media/{id}/{index}/content", submissionId, 0)
                        .param("download", "1").header("Range", "bytes=0-999")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Content-Range"))
                .andExpect(header().doesNotExist("Accept-Ranges"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.status").value(500));
    }

    /**
     * Spring repond a HEAD avec le gestionnaire de GET, mais la limite de debit ne compte
     * que GET : un HEAD lisait tout l'objet S3, publiait une lecture et journalisait un
     * telechargement qui n'avait pas eu lieu. Il est refuse avant tout appel.
     */
    @Test
    void headIsRefusedBeforeAnythingIsRead() throws Exception {
        mockMvc.perform(head("/api/media-library/media/{id}/{index}/content", submissionId, 0).param("download", "1")
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
        mockMvc.perform(head("/api/media-library/media/{id}/{index}/thumbnail", submissionId, 0)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));

        verifyNoInteractions(mediaService, thumbnailService);
    }

    @Test
    void aThumbnailIsWrittenWithTheSecurityHeaders() throws Exception {
        when(thumbnailService.thumbnail(eq("staff-sub"), any(), eq(submissionId), eq(0)))
                .thenReturn(new MediaThumbnailService.Thumbnail("image/jpeg", new byte[]{7, 7}));

        mockMvc.perform(get("/api/media-library/media/{id}/{index}/thumbnail", submissionId, 0)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-store, private, max-age=0"))
                .andExpect(content().bytes(new byte[]{7, 7}));
    }
}
