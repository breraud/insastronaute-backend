package fr.insastronaute.webapi.treasurehunt.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AlphanumericVaultCodeMigrationTest {
    @Test
    void migrationEncodesExistingCodesWithTheReadableAlphabet() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V68__six_character_alphanumeric_vault_codes.sql"));

        assertThat(migration)
                .contains("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
                .containsIgnoringCase("treasure_hunt_vault_code")
                .containsIgnoringCase("maxvalue 1073741823")
                .containsIgnoringCase("no cycle")
                .containsIgnoringCase("row_number() over (order by issued_at, id)")
                .contains("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$");
    }
}
