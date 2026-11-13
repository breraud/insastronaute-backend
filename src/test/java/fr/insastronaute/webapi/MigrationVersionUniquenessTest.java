package fr.insastronaute.webapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationVersionUniquenessTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([^_]+)__.*\\.sql$");

    @Test
    void flywayMigrationVersionsAreUnique() throws IOException {
        Path migrationDirectory = Path.of("src/main/resources/db/migration");

        Map<String, List<String>> migrationsByVersion;
        try (var files = Files.list(migrationDirectory)) {
            migrationsByVersion = files
                    .map(path -> path.getFileName().toString())
                    .map(VERSIONED_MIGRATION::matcher)
                    .filter(Matcher::matches)
                    .collect(Collectors.groupingBy(
                            matcher -> matcher.group(1),
                            Collectors.mapping(Matcher::group, Collectors.toList())));
        }

        assertThat(migrationsByVersion)
                .allSatisfy((version, migrations) -> assertThat(migrations)
                        .as("Flyway version V%s", version)
                        .hasSize(1));
    }
}
