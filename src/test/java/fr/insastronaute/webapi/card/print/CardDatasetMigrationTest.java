package fr.insastronaute.webapi.card.print;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheet;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetCode;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les tests tournent sur un schema fabrique par Hibernate ; la production tourne sur Flyway
 * avec {@code ddl-auto=validate}. Une colonne ajoutee a une entite sans migration passe donc
 * toute la suite au vert et refuse de demarrer en production. Ce test ferme cet ecart.
 */
class CardDatasetMigrationTest {

    private static final Path SHEETS =
            Path.of("src/main/resources/db/migration/V84__card_dataset_sheets.sql");
    private static final Path CROPS =
            Path.of("src/main/resources/db/migration/V85__card_dataset_crops.sql");
    private static final Path CROP_FRAMING =
            Path.of("src/main/resources/db/migration/V86__card_dataset_crop_framing.sql");
    private static final Path CROP_DISCARDED =
            Path.of("src/main/resources/db/migration/V87__card_dataset_crop_discarded.sql");
    private static final Path CROP_FRAMING_GEOMETRY =
            Path.of("src/main/resources/db/migration/V95__card_dataset_crop_framing_geometry.sql");
    private static final Path SHEET_PDFS =
            Path.of("src/main/resources/db/migration/V92__card_dataset_sheet_pdfs.sql");

    private static final Path SHEET_CODE_CARD =
            Path.of("src/main/resources/db/migration/V108__card_dataset_sheet_code_card.sql");

    @Test
    void theMigrationsDeclareEveryColumnTheDatasetEntitiesMap() throws IOException {
        // Une entite peut etre decrite par plusieurs migrations : la table d'origine, puis les
        // colonnes ajoutees apres coup. Le test lit l'histoire complete, pas le seul fichier
        // de creation.
        Map<Class<?>, List<Path>> entities = new LinkedHashMap<>();
        entities.put(CardDatasetSheet.class, List.of(SHEETS, SHEET_PDFS));
        entities.put(CardDatasetSheetCode.class, List.of(SHEETS, SHEET_CODE_CARD));
        entities.put(CardDatasetCrop.class, List.of(CROPS, CROP_FRAMING, CROP_FRAMING_GEOMETRY));

        for (Map.Entry<Class<?>, List<Path>> entry : entities.entrySet()) {
            Class<?> entity = entry.getKey();
            String migration = readAll(entry.getValue()).toLowerCase(Locale.ROOT);
            String table = entity.getAnnotation(Table.class).name().toLowerCase(Locale.ROOT);

            assertThat(migration)
                    .as("table %s of %s", table, entity.getSimpleName())
                    .contains("create table " + table);
            for (String column : mappedColumns(entity)) {
                assertThat(migration)
                        .as("column %s of %s", column, entity.getSimpleName())
                        .contains(column);
            }
        }
    }

    @Test
    void sheetPdfMigrationStoresOnlyANullableS3ObjectKey() throws IOException {
        String migration = Files.readString(SHEET_PDFS).toLowerCase(Locale.ROOT);

        assertThat(migration).contains("add column pdf_object_key varchar(512)");
        assertThat(migration).doesNotContain("bytea");
        assertThat(migration).doesNotContain("blob");
        assertThat(migration).doesNotContain("pdf_bytes");
        assertThat(migration).doesNotContain("pdf_object_key varchar(512) not null");
    }

    @Test
    void theMigrationKeepsThePrintedSheetIdentifierUnique() throws IOException {
        String migration = Files.readString(SHEETS).toLowerCase(Locale.ROOT);

        assertThat(migration).contains("unique (sheet_id)");
        assertThat(migration).contains("unique (sheet_id, strip_position)");
    }

    @Test
    void theDatabaseRefusesAFramingNobodyDefined() throws IOException {
        // La contrainte est doublee cote base : une decoupe rangee a tort parmi les cadrages
        // automatiques diluerait sans bruit le lot des cas difficiles, et c'est precisement le
        // lot qu'on cherche a constituer.
        String migration = Files.readString(CROP_FRAMING).toLowerCase(Locale.ROOT);

        assertThat(migration).contains("framing in ('automatic', 'manual')");
        assertThat(migration).contains("not null");
    }

    @Test
    void theDatabaseAcceptsEveryStatusTheCodeCanWriteAndNothingElse() throws IOException {
        // La contrainte nommee est reecrite, pas abandonnee. Laisser tomber la contrainte
        // ouvrirait la base a un statut que le code refuse, et l'ecart ne se verrait qu'au
        // chargement ; ne pas l'etendre ferait echouer le premier ecart en production alors que
        // toute la suite est au vert, puisque les tests tournent sur un schema Hibernate.
        String history = readAll(List.of(CROPS, CROP_DISCARDED)).toLowerCase(Locale.ROOT);

        assertThat(history).contains("drop constraint ck_card_dataset_crops_status");
        String lastCheck = lastStatusCheck(history);
        for (CardDatasetCropStatus status : CardDatasetCropStatus.values()) {
            assertThat(lastCheck)
                    .as("statut %s accepte par la base", status)
                    .contains("'" + status.name().toLowerCase(Locale.ROOT) + "'");
        }
    }

    /** Le dernier CHECK sur status de l'histoire des migrations : c'est celui qui vaut. */
    private String lastStatusCheck(String history) {
        Matcher matcher = Pattern.compile("status in \\(([^)]*)\\)").matcher(history);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        assertThat(last).as("un CHECK sur la colonne status").isNotNull();
        return last;
    }

    private String readAll(List<Path> migrations) throws IOException {
        StringBuilder content = new StringBuilder();
        for (Path migration : migrations) {
            content.append(Files.readString(migration)).append('\n');
        }
        return content.toString();
    }

    private List<String> mappedColumns(Class<?> entity) {
        List<String> columns = new ArrayList<>();
        for (Field field : entity.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && !column.name().isBlank()) {
                columns.add(column.name().toLowerCase(Locale.ROOT));
            }
            JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
            if (joinColumn != null && !joinColumn.name().isBlank()) {
                columns.add(joinColumn.name().toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }
}
