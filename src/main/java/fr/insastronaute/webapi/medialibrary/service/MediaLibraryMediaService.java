package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.MediaReadChannel;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.medialibrary.export.MediaExportNaming;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Un fichier complet : pour la visionneuse, la lecture video, ou un telechargement.
 *
 * <p>Tout ce qui peut echouer passe avant que le controleur n'ecrive le statut. Une
 * lecture est publiee au registre des lectures ; un telechargement s'ecrit en plus au
 * journal d'administration, une seule fois meme quand le navigateur reprend le
 * transfert en cours de route.</p>
 */
@Service
public class MediaLibraryMediaService {

    public record OpenedMedia(MediaLibraryResolver.ResolvedMedia media, ProofMediaStream stream, String downloadName) {
    }

    private final MediaLibraryAccess access;
    private final MediaLibraryResolver resolver;
    private final MediaLibraryStorage storage;
    private final MediaReadAuditPublisher audit;
    private final AdminActionJournal journal;

    public MediaLibraryMediaService(MediaLibraryAccess access, MediaLibraryResolver resolver, MediaLibraryStorage storage,
                                    MediaReadAuditPublisher audit, AdminActionJournal journal) {
        this.access = access;
        this.resolver = resolver;
        this.storage = storage;
        this.audit = audit;
        this.journal = journal;
    }

    public OpenedMedia openContent(String keycloakSub, Collection<String> authorities, UUID submissionId, int index,
                                   String range, boolean download) {
        access.require(keycloakSub, authorities);
        MediaLibraryResolver.ResolvedMedia media = resolver.resolve(submissionId, index);
        ProofMediaStream stream = storage.open(media.objectKey(), range);
        try {
            audit.publish(media.submissionId(), keycloakSub, index, stream.contentLength(), MediaReadChannel.MEDIA_LIBRARY);
            if (download && stream.start() == 0) {
                journal.record(keycloakSub, media.authorId(), AdminActionType.MEDIA_LIBRARY_FILE_DOWNLOADED,
                        Map.of("submissionId", media.submissionId().toString(), "mediaIndex", index));
            }
        } catch (RuntimeException exception) {
            closeQuietly(stream);
            throw exception;
        }
        String downloadName = MediaExportNaming.downloadFileName(media.challengeTitle(), media.authorName(),
                media.submittedAt(), index, MediaExportNaming.extension(media.objectKey()));
        return new OpenedMedia(media, stream, downloadName);
    }

    private static void closeQuietly(ProofMediaStream stream) {
        // Rien n'a ete lu : sans l'abandon, la fermeture lirait l'objet entier.
        MediaLibraryStorage.abandon(stream.inputStream());
        try {
            stream.inputStream().close();
        } catch (IOException ignored) {
            // Le flux S3 est deja perdu ; l'erreur d'origine est celle qui compte.
        }
    }
}
