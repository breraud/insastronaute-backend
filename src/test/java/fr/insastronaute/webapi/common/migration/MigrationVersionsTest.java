package fr.insastronaute.webapi.common.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux migrations ne peuvent pas porter le meme numero.
 *
 * <p>Flyway refuse de demarrer dans ce cas - « Found more than one migration with
 * version N » - et l'application entiere ne monte pas. C'est arrive : une migration SQL
 * a ete numerotee en regardant les fichiers {@code .sql} du dossier, sans voir qu'une
 * migration <strong>Java</strong> portait deja ce numero dans
 * {@code src/main/java/db/migration}. Le deploiement a echoue au demarrage.</p>
 *
 * <p>Et rien ne l'avait vu : la suite tourne sur H2 avec {@code spring.flyway.enabled=false}
 * et {@code ddl-auto=create-drop}. <strong>Flyway n'est execute par aucun test</strong>,
 * donc une collision de versions, comme un SQL invalide, ne se decouvre qu'au
 * demarrage d'un vrai serveur. Ce test ne couvre que la collision - la moitie la moins
 * chere a verifier, et celle qui coute un deploiement.</p>
 *
 * <p>Il balaie le classpath comme Flyway le fait, donc il voit les deux natures : les
 * ressources {@code .sql} et les classes compilees.</p>
 */
class MigrationVersionsTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__(.+?)\\.(sql|class)$");

    @Test
    void noTwoMigrationsShareAVersion() throws IOException {
        Map<String, List<String>> byVersion = new LinkedHashMap<>();

        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*")) {
            String name = resource.getFilename();
            if (name == null) {
                continue;
            }
            Matcher matcher = VERSIONED.matcher(name);
            if (!matcher.matches()) {
                continue;
            }
            // Une classe interne comme V115__X$1.class n'est pas une migration.
            if (matcher.group(2).contains("$")) {
                continue;
            }
            byVersion.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(name);
        }

        assertThat(byVersion).isNotEmpty();

        List<String> collisions = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "V" + entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertThat(collisions)
                .as("Deux migrations partagent un numero : Flyway refusera de demarrer. "
                        + "Les migrations Java vivent dans src/main/java/db/migration et comptent aussi.")
                .isEmpty();
    }
}
