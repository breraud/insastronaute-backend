package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.MediaReadChannel;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaThumbnailServiceTest {

    private final MediaLibraryAccess access = mock(MediaLibraryAccess.class);
    private final MediaLibraryResolver resolver = mock(MediaLibraryResolver.class);
    private final MediaLibraryStorage storage = mock(MediaLibraryStorage.class);
    private final MediaThumbnailRenderer renderer = mock(MediaThumbnailRenderer.class);
    private final MediaReadAuditPublisher audit = mock(MediaReadAuditPublisher.class);

    private final UUID submissionId = UUID.randomUUID();

    private MediaLibraryResolver.ResolvedMedia media(String key, String type) {
        return new MediaLibraryResolver.ResolvedMedia(submissionId, 0, key, type, UUID.randomUUID(), "A", "D", Instant.EPOCH);
    }

    @Test
    void aThumbnailIsRenderedOnceThenServedFromMemoryAndAuditedEachTime() throws Exception {
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, renderer, audit, 2, Duration.ofSeconds(1));
        when(resolver.resolve(submissionId, 0)).thenReturn(media("proofs/u/c/a.jpg", "image/jpeg"));
        when(storage.open("proofs/u/c/a.jpg", null)).thenAnswer(call ->
                ProofMediaStream.full("image/jpeg", 3, new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(renderer.render(any())).thenReturn(new byte[]{9, 9});

        service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0);
        var second = service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0);

        assertThat(second.bytes()).containsExactly(9, 9);
        assertThat(second.contentType()).isEqualTo("image/jpeg");
        verify(storage, times(1)).open("proofs/u/c/a.jpg", null);
        verify(audit, times(2)).publish(submissionId, "sub", 0, 2, MediaReadChannel.MEDIA_LIBRARY);
    }

    @Test
    void theCacheNeverBypassesEligibility() throws Exception {
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, renderer, audit, 2, Duration.ofSeconds(1));
        when(resolver.resolve(submissionId, 0))
                .thenReturn(media("proofs/u/c/a.jpg", "image/jpeg"))
                .thenThrow(new NotFoundException("Média introuvable."));
        when(storage.open(any(), any())).thenAnswer(call ->
                ProofMediaStream.full("image/jpeg", 1, new ByteArrayInputStream(new byte[]{1})));
        when(renderer.render(any())).thenReturn(new byte[]{9});

        service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0);

        assertThatThrownBy(() -> service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anUnreadableImageIsServedAsIs() throws Exception {
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, renderer, audit, 2, Duration.ofSeconds(1));
        when(resolver.resolve(submissionId, 0)).thenReturn(media("proofs/u/c/a.webp", "image/webp"));
        when(storage.open(any(), any())).thenAnswer(call ->
                ProofMediaStream.full("image/webp", 2, new ByteArrayInputStream(new byte[]{4, 5})));
        when(renderer.render(any())).thenThrow(new UnsupportedThumbnailException());

        var thumbnail = service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0);

        assertThat(thumbnail.contentType()).isEqualTo("image/webp");
        assertThat(thumbnail.bytes()).containsExactly(4, 5);
    }

    /** Une image refusee n'est jamais servie telle quelle : le navigateur s'y casserait aussi. */
    @Test
    void aRefusedImageIsNotFound() throws Exception {
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, renderer, audit, 2, Duration.ofSeconds(1));
        when(resolver.resolve(submissionId, 0)).thenReturn(media("proofs/u/c/a.jpg", "image/jpeg"));
        when(storage.open(any(), any())).thenAnswer(call ->
                ProofMediaStream.full("image/jpeg", 2, new ByteArrayInputStream(new byte[]{4, 5})));
        when(renderer.render(any())).thenThrow(new RefusedThumbnailException("trop grande"));

        assertThatThrownBy(() -> service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0))
                .isInstanceOf(NotFoundException.class);
        verify(audit, never()).publish(any(), any(), anyInt(), anyLong(), any());
    }

    /** Une vraie photo coupee : un 404, pas un 500. */
    @Test
    void aTruncatedJpegIsNotFoundRatherThanAnError() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(3072, 2048, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", encoded);
        byte[] truncated = java.util.Arrays.copyOf(encoded.toByteArray(), 300);
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, new MediaThumbnailRenderer(),
                audit, 2, Duration.ofSeconds(1));
        when(resolver.resolve(submissionId, 0)).thenReturn(media("proofs/u/c/a.jpg", "image/jpeg"));
        when(storage.open(any(), any())).thenAnswer(call ->
                ProofMediaStream.full("image/jpeg", truncated.length, new ByteArrayInputStream(truncated)));

        assertThatThrownBy(() -> service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aVideoHasNoThumbnail() {
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, renderer, audit, 2, Duration.ofSeconds(1));
        when(resolver.resolve(submissionId, 0)).thenReturn(media("proofs/u/c/a.mp4", "video/mp4"));

        assertThatThrownBy(() -> service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0))
                .isInstanceOf(NotFoundException.class);
        verify(storage, never()).open(any(), any());
    }

    @Test
    void noFreeSlotMeansUnavailable() {
        MediaThumbnailService service = new MediaThumbnailService(access, resolver, storage, renderer, audit, 0, Duration.ZERO);
        when(resolver.resolve(eq(submissionId), eq(0))).thenReturn(media("proofs/u/c/a.jpg", "image/jpeg"));

        assertThatThrownBy(() -> service.thumbnail("sub", Set.of("ROLE_ADMIN"), submissionId, 0))
                .isInstanceOf(StorageUnavailableException.class);
    }
}
