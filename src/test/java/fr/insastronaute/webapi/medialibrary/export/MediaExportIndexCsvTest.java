package fr.insastronaute.webapi.medialibrary.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaExportIndexCsvTest {

    private static MediaExportIndexCsv.Row row(String author, boolean missing) {
        return new MediaExportIndexCsv.Row("racine/defi/f.jpg", "Défi; spécial", "Soirée", "VALIDATED",
                Instant.parse("2026-09-14T16:03:00Z"), author, List.of("Léo", "Inès"), 0, "image/jpeg", 2048,
                "Alex", Instant.parse("2026-10-01T08:00:00Z"), missing);
    }

    @Test
    void writesABomAHeaderAndOneLinePerMedia() {
        String csv = new String(MediaExportIndexCsv.render(List.of(row("Camille", false), row("Sam", true))), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿chemin;defi;categorie;statut;date;auteur;participants;position;type;taille;retenu_par;retenu_le;etat\r\n");
        assertThat(csv).contains("racine/defi/f.jpg;\"Défi; spécial\";Soirée;VALIDATED;2026-09-14 18:03;Camille;Léo, Inès;1;image/jpeg;2048;Alex;2026-10-01 10:00;ok\r\n");
        assertThat(csv).contains(";Sam;").endsWith(";manquant\r\n");
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        String csv = new String(MediaExportIndexCsv.render(List.of(row("=HYPERLINK(\"x\")", false))), StandardCharsets.UTF_8);

        assertThat(csv).contains(";\"'=HYPERLINK(\"\"x\"\")\";");
    }
}
