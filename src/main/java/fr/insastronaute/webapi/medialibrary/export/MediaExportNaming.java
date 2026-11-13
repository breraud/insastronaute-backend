package fr.insastronaute.webapi.medialibrary.export;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * Les noms de tout ce que la Mediatheque fait sortir : chemins du zip, fichiers
 * telecharges, parties.
 *
 * <p>Tout passe par {@link #slug} : de l'ASCII en minuscules, des tirets, rien qui
 * puisse remonter d'un dossier ni deplaire a un systeme de fichiers.</p>
 */
public final class MediaExportNaming {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int MAX_SLUG = 60;
    private static final String NO_NAME = "sans-nom";

    private MediaExportNaming() {
    }

    public static String slug(String value) {
        if (value == null) {
            return NO_NAME;
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG) {
            slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? NO_NAME : slug;
    }

    public static String day(Instant instant) {
        return instant == null ? "sans-date" : LocalDate.ofInstant(instant, PARIS).toString();
    }

    public static String rootFolder(Instant snapshotAt) {
        return "mediatheque-" + day(snapshotAt);
    }

    public static String extension(String objectKey) {
        int dot = objectKey == null ? -1 : objectKey.lastIndexOf('.');
        String extension = dot < 0 ? "" : objectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,5}") ? extension : "bin";
    }

    public static String entryPath(String root, String challengeTitle, Instant submittedAt, String authorName,
                                   int otherMembers, UUID submissionId, int mediaIndex, String extension) {
        String members = otherMembers > 0 ? slug(authorName) + "+" + otherMembers : slug(authorName);
        return root + "/" + slug(challengeTitle) + "/" + day(submittedAt) + "_" + members + "_"
                + submissionId.toString().substring(0, 8) + "_" + (mediaIndex + 1) + "." + extension;
    }

    public static String downloadFileName(String challengeTitle, String authorName, Instant submittedAt,
                                          int mediaIndex, String extension) {
        return slug(challengeTitle) + "_" + slug(authorName) + "_" + day(submittedAt) + "_" + (mediaIndex + 1) + "." + extension;
    }

    public static String partFileName(Instant snapshotAt, int partNumber, int partCount) {
        return rootFolder(snapshotAt) + "-partie-" + partNumber + "-sur-" + partCount + ".zip";
    }
}
