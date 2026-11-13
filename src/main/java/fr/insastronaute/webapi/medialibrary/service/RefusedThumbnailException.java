package fr.insastronaute.webapi.medialibrary.service;

/**
 * Une image qu'on refuse de decoder : trop grande pour ce qu'elle coute, ou corrompue.
 *
 * <p>A la difference d'un format inconnu ({@link UnsupportedThumbnailException}), on ne
 * sert pas l'original a la place : le navigateur s'y casserait les dents de la meme
 * facon. La vignette repond 404.</p>
 */
public class RefusedThumbnailException extends RuntimeException {
    public RefusedThumbnailException(String message) {
        super(message);
    }

    public RefusedThumbnailException(Throwable cause) {
        super("Image illisible pour une vignette.", cause);
    }
}
