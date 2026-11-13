package fr.insastronaute.webapi.medialibrary.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;
import fr.insastronaute.webapi.challenge.audit.MediaReadChannel;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Les vignettes de la grille.
 *
 * <p>Fabriquees a la volee, jamais ecrites ni sur disque ni dans S3 : aucune copie de
 * plus d'une photo personnelle a purger. Le cache vit en memoire, une heure au plus, et
 * l'acces comme l'eligibilite se verifient avant de le lire. Deux vignettes au plus se
 * fabriquent en meme temps : le jeu tourne sur les memes deux coeurs.</p>
 */
@Service
public class MediaThumbnailService {

    public record Thumbnail(String contentType, byte[] bytes) {
    }

    private static final int SLOTS = 2;
    private static final Duration SLOT_WAIT = Duration.ofSeconds(10);
    private static final long CACHE_BYTES = 64L * 1024 * 1024;

    private final MediaLibraryAccess access;
    private final MediaLibraryResolver resolver;
    private final MediaLibraryStorage storage;
    private final MediaThumbnailRenderer renderer;
    private final MediaReadAuditPublisher audit;
    private final Semaphore slots;
    private final Duration slotWait;
    private final Cache<String, Thumbnail> cache = Caffeine.newBuilder()
            .maximumWeight(CACHE_BYTES)
            .weigher((String key, Thumbnail thumbnail) -> thumbnail.bytes().length)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    @Autowired
    public MediaThumbnailService(MediaLibraryAccess access, MediaLibraryResolver resolver, MediaLibraryStorage storage,
                                 MediaThumbnailRenderer renderer, MediaReadAuditPublisher audit) {
        this(access, resolver, storage, renderer, audit, SLOTS, SLOT_WAIT);
    }

    MediaThumbnailService(MediaLibraryAccess access, MediaLibraryResolver resolver, MediaLibraryStorage storage,
                          MediaThumbnailRenderer renderer, MediaReadAuditPublisher audit, int slots, Duration slotWait) {
        this.access = access;
        this.resolver = resolver;
        this.storage = storage;
        this.renderer = renderer;
        this.audit = audit;
        this.slots = new Semaphore(slots);
        this.slotWait = slotWait;
    }

    public Thumbnail thumbnail(String keycloakSub, Collection<String> authorities, UUID submissionId, int index) {
        access.require(keycloakSub, authorities);
        MediaLibraryResolver.ResolvedMedia media = resolver.resolve(submissionId, index);
        if (media.contentType().startsWith("video/")) {
            throw new NotFoundException(MediaLibraryStorage.MEDIA_NOT_FOUND);
        }
        String key = submissionId + ":" + index;
        Thumbnail thumbnail = cache.getIfPresent(key);
        if (thumbnail == null) {
            thumbnail = render(media);
            cache.put(key, thumbnail);
        }
        audit.publish(submissionId, keycloakSub, index, thumbnail.bytes().length, MediaReadChannel.MEDIA_LIBRARY);
        return thumbnail;
    }

    private Thumbnail render(MediaLibraryResolver.ResolvedMedia media) {
        acquireSlot();
        try {
            byte[] source;
            try (InputStream input = storage.open(media.objectKey(), null).inputStream()) {
                source = input.readAllBytes();
            }
            try {
                return new Thumbnail("image/jpeg", renderer.render(source));
            } catch (UnsupportedThumbnailException exception) {
                return new Thumbnail(media.contentType(), source);
            } catch (RefusedThumbnailException exception) {
                // Trop grande ou corrompue : l'original casserait le navigateur de la meme facon.
                throw new NotFoundException(MediaLibraryStorage.MEDIA_NOT_FOUND);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            slots.release();
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(slotWait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new StorageUnavailableException("Trop de vignettes en préparation, réessayez dans un instant.", null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageUnavailableException("Vignette interrompue.", exception);
        }
    }
}
