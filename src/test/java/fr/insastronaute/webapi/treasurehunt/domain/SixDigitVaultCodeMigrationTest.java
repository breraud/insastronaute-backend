package fr.insastronaute.webapi.treasurehunt.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SixDigitVaultCodeMigrationTest {
    @Test
    void migrationReassignsExistingCodesAndConfiguresABoundedSequence() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V67__six_digit_vault_codes.sql"));

        assertThat(migration)
                .containsIgnoringCase("create sequence player_vault_code_sequence")
                .containsIgnoringCase("minvalue 0")
                .containsIgnoringCase("maxvalue 999999")
                .containsIgnoringCase("no cycle")
                .containsIgnoringCase("row_number() over (order by issued_at, id)")
                .containsIgnoringCase("lpad")
                .containsIgnoringCase("varchar(6)")
                .contains("^[0-9]{6}$");
    }
}
