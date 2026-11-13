package fr.insastronaute.webapi.treasurehunt.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RandomPerPlayerVaultCodeMigrationTest {
    @Test
    void migrationRandomizesCodesAndScopesUniquenessToThePlayer() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V69__random_per_player_vault_codes.sql"));

        assertThat(migration)
                .contains("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
                .containsIgnoringCase("drop constraint player_vault_codes_code_key")
                .containsIgnoringCase("unique (user_id, code)")
                .containsIgnoringCase("order by issued_at, id")
                .containsIgnoringCase("drop function treasure_hunt_vault_code(bigint)")
                .containsIgnoringCase("drop sequence player_vault_code_sequence");
    }
}
