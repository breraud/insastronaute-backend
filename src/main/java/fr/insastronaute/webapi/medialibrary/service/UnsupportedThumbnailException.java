package fr.insastronaute.webapi.medialibrary.service;

/** Un format qu'ImageIO ne sait pas lire, le WebP notamment : on servira l'original. */
public class UnsupportedThumbnailException extends RuntimeException {
    public UnsupportedThumbnailException() {
        super("Format d'image non pris en charge pour une vignette.");
    }
}
