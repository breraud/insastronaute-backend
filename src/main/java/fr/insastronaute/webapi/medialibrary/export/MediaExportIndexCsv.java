package fr.insastronaute.webapi.medialibrary.export;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * L'index d'une partie d'export, lisible dans Excel en francais : UTF-8 avec BOM, des
 * points-virgules.
 *
 * <p>Un nom de joueur vient d'un joueur : une cellule qui commencerait par {@code =},
 * {@code +}, {@code -} ou {@code @} deviendrait une formule a l'ouverture. Elle est
 * prefixee d'une apostrophe.</p>
 */
public final class MediaExportIndexCsv {

    public static final String HEADER =
            "chemin;defi;categorie;statut;date;auteur;participants;position;type;taille;retenu_par;retenu_le;etat";

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record Row(String path, String challenge, String category, String status, Instant submittedAt,
                      String author, List<String> participants, int mediaIndex, String contentType, long sizeBytes,
                      String selectedBy, Instant selectedAt, boolean missing) {
    }

    private MediaExportIndexCsv() {
    }

    public static byte[] render(List<Row> rows) {
        StringBuilder csv = new StringBuilder("﻿").append(HEADER).append("\r\n");
        for (Row row : rows) {
            csv.append(String.join(";",
                    cell(row.path()),
                    cell(row.challenge()),
                    cell(row.category()),
                    cell(row.status()),
                    cell(minute(row.submittedAt())),
                    cell(row.author()),
                    cell(String.join(", ", row.participants())),
                    Integer.toString(row.mediaIndex() + 1),
                    cell(row.contentType()),
                    Long.toString(row.sizeBytes()),
                    cell(row.selectedBy()),
                    cell(minute(row.selectedAt())),
                    row.missing() ? "manquant" : "ok"
            )).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String minute(Instant instant) {
        return instant == null ? "" : LocalDateTime.ofInstant(instant, MediaExportNaming.PARIS).format(MINUTE);
    }

    private static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean quote = safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                || !safe.equals(value);
        return quote ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
