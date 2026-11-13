package fr.insastronaute.webapi.card.print;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CardPrintQrSpacingMigrationTest {

    @Test
    void migrationTargetsTheExistingCardPrintTemplatesTable() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V83__card_print_qr_spacing.sql"));

        assertThat(migration)
                .contains("ALTER TABLE card_print_templates")
                .doesNotContain("ALTER TABLE card_print_template\n");
    }
}
