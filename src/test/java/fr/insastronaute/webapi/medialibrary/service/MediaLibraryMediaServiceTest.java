package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.MediaReadChannel;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaLibraryMediaServiceTest {

    private final MediaLibraryAccess access = mock(MediaLibraryAccess.class);
    private final MediaLibraryResolver resolver = mock(MediaLibraryResolver.class);
    private final MediaLibraryStorage storage = mock(MediaLibraryStorage.class);
    private final MediaReadAuditPublisher audit = mock(MediaReadAuditPublisher.class);
    private final AdminActionJournal journal = mock(AdminActionJournal.class);
    private final MediaLibraryMediaService service = new MediaLibraryMediaService(access, resolver, storage, audit, journal);

    private final UUID submissionId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final MediaLibraryResolver.ResolvedMedia media = new MediaLibraryResolver.ResolvedMedia(
            submissionId, 1, "proofs/u/c/b.mp4", "video/mp4", authorId, "Camille Martin", "Défi", Instant.parse("2026-09-14T10:00:00Z"));

    @Test
    void aReadIsAuditedWithItsChannel() {
        when(resolver.resolve(submissionId, 1)).thenReturn(media);
        when(storage.open("proofs/u/c/b.mp4", null)).thenReturn(ProofMediaStream.full("video/mp4", 4, new ByteArrayInputStream(new byte[4])));

        var opened = service.openContent("staff-sub", Set.of("ROLE_ADMIN"), submissionId, 1, null, false);

        assertThat(opened.downloadName()).isEqualTo("defi_camille-martin_2026-09-14_2.mp4");
        verify(audit).publish(submissionId, "staff-sub", 1, 4, MediaReadChannel.MEDIA_LIBRARY);
        verify(journal, never()).record(any(), any(), any(), anyMap());
    }

    @Test
    void aResumedDownloadIsJournaledOnce() {
        when(resolver.resolve(submissionId, 1)).thenReturn(media);
        when(storage.open("proofs/u/c/b.mp4", "bytes=0-"))
                .thenReturn(new ProofMediaStream("video/mp4", 1000, 0, 999, true, new ByteArrayInputStream(new byte[0])));
        when(storage.open("proofs/u/c/b.mp4", "bytes=100-"))
                .thenReturn(new ProofMediaStream("video/mp4", 1000, 100, 999, true, new ByteArrayInputStream(new byte[0])));

        service.openContent("staff-sub", Set.of("ROLE_ADMIN"), submissionId, 1, "bytes=0-", true);
        service.openContent("staff-sub", Set.of("ROLE_ADMIN"), submissionId, 1, "bytes=100-", true);

        verify(journal, times(1)).record("staff-sub", authorId, AdminActionType.MEDIA_LIBRARY_FILE_DOWNLOADED,
                Map.of("submissionId", submissionId.toString(), "mediaIndex", 1));
    }

    @Test
    void noAccessNoStorage() {
        doThrow(new ForbiddenException("non")).when(access).require(any(), any());

        assertThatThrownBy(() -> service.openContent("sub", Set.of(), submissionId, 1, null, false))
                .isInstanceOf(ForbiddenException.class);
        verify(storage, never()).open(any(), any());
    }

    @Test
    void aFailedAuditClosesTheStorageStream() throws Exception {
        java.io.InputStream input = mock(java.io.InputStream.class);
        when(resolver.resolve(submissionId, 1)).thenReturn(media);
        when(storage.open("proofs/u/c/b.mp4", null)).thenReturn(ProofMediaStream.full("video/mp4", 4, input));
        doThrow(new IllegalStateException("redis")).when(audit).publish(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(), any(MediaReadChannel.class));

        assertThatThrownBy(() -> service.openContent("sub", Set.of("ROLE_ADMIN"), submissionId, 1, null, false))
                .isInstanceOf(IllegalStateException.class);
        verify(input).close();
    }

    /** Le flux S3 n'a pas ete lu : le fermer seul le viderait d'abord, jusqu'au dernier octet. */
    @Test
    void aFailedAuditAbandonsTheStorageStreamBeforeClosingIt() {
        AbortableTestStream input = AbortableTestStream.of(new byte[4]);
        when(resolver.resolve(submissionId, 1)).thenReturn(media);
        when(storage.open("proofs/u/c/b.mp4", null)).thenReturn(ProofMediaStream.full("video/mp4", 4, input));
        doThrow(new IllegalStateException("redis")).when(audit).publish(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(), any(MediaReadChannel.class));

        assertThatThrownBy(() -> service.openContent("sub", Set.of("ROLE_ADMIN"), submissionId, 1, null, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(input.events()).containsExactly("abort", "close");
    }
}
