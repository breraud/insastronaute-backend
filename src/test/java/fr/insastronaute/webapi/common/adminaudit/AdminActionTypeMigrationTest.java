package fr.insastronaute.webapi.common.adminaudit;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La contrainte CHECK de la migration et l'enumeration doivent dire la meme
 * chose.
 *
 * <p>Rien d'autre ne les tient ensemble : {@code ddl-auto=validate} ne regarde
 * pas les contraintes CHECK, et les tests tournent sur H2 avec un schema
 * regenere qui ne les cree jamais. Une valeur ajoutee a {@link AdminActionType}
 * sans migration compilerait, passerait toute la suite, et echouerait au
 * premier INSERT en production - sur le geste meme qu'on voulait tracer.</p>
 */
class AdminActionTypeMigrationTest {

    private static final Path MIGRATIONS_DIR = Paths.get("src/main/resources/db/migration");

    /**
     * Ce que porte chaque migration qui (re)cree la contrainte, qu'elle la
     * cree pour la premiere fois ou la remplace entiere.
     */
    private static final String CONSTRAINT_MARKER = "ck_admin_action_events_action";

    private static final Pattern VERSION_PREFIX = Pattern.compile("^V(\\d+)__");

    @Test
    void theMigrationAcceptsExactlyTheActionsTheEnumDeclares() throws IOException {
        Path migration = latestMigrationRecreatingTheConstraint();
        List<String> declaredInSql = actionsAllowedByTheCheckConstraint(readMigration(migration), migration);
        List<String> declaredInJava = new ArrayList<>();
        for (AdminActionType action : AdminActionType.values()) {
            declaredInJava.add(action.name());
        }

        assertThat(declaredInSql)
                .as("la contrainte CHECK de %s doit lister exactement AdminActionType.values()", migration)
                .containsExactlyInAnyOrderElementsOf(declaredInJava);
    }

    /**
     * La derniere migration qui (re)cree la contrainte, decouverte plutot que
     * nommee. Postgres ne sait pas etendre un CHECK en place : chaque valeur
     * ajoutee a l'enumeration passe par une migration qui supprime la
     * contrainte et la recree entiere, et le fichier qui fait foi change a
     * chaque fois. Un chemin code en dur resterait vert contre un fichier
     * perime des qu'une migration suivante recree la meme contrainte : ce
     * test ne le remarquerait jamais, alors que la base, elle, appliquerait la
     * derniere. La liste decouverte doit rester non vide - une decouverte qui
     * ne trouve rien comparerait deux listes vides et passerait sans avoir
     * rien verifie.
     */
    private Path latestMigrationRecreatingTheConstraint() throws IOException {
        List<Path> candidates;
        try (Stream<Path> tree = Files.walk(MIGRATIONS_DIR)) {
            candidates = tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(this::containsConstraintMarker)
                    .sorted(Comparator.comparingInt(this::migrationVersion))
                    .toList();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }

        assertThat(candidates)
                .as("au moins une migration sous %s doit (re)creer %s", MIGRATIONS_DIR.toAbsolutePath(),
                        CONSTRAINT_MARKER)
                .isNotEmpty();

        return candidates.get(candidates.size() - 1);
    }

    private boolean containsConstraintMarker(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(CONSTRAINT_MARKER);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private int migrationVersion(Path path) {
        Matcher version = VERSION_PREFIX.matcher(path.getFileName().toString());
        assertThat(version.find())
                .as("le nom de migration %s doit commencer par V<n>__", path.getFileName())
                .isTrue();
        return Integer.parseInt(version.group(1));
    }

    private String readMigration(Path migration) throws IOException {
        return Files.readString(migration, StandardCharsets.UTF_8);
    }

    /**
     * La liste litterale, lue a partir de la <em>derniere</em> mention du nom
     * de la contrainte - celle du {@code ADD CONSTRAINT}, la premiere etant le
     * {@code DROP} qui la precede dans le meme fichier.
     *
     * <p>Depuis V113, une meme migration recree aussi
     * {@code ck_admin_action_events_target_presence}, qui porte sa propre
     * liste {@code action IN (...)} - celle des seuls types de portee globale.
     * Chercher le premier {@code action IN} du fichier entier lirait cette
     * liste-la si l'ordre des deux blocs changeait, et ferait echouer le test
     * sur une migration correcte. On s'ancre donc sur le nom de la contrainte
     * qu'on verifie.</p>
     */
    private List<String> actionsAllowedByTheCheckConstraint(String sql, Path migration) {
        int start = sql.lastIndexOf(CONSTRAINT_MARKER);
        assertThat(start)
                .as("la contrainte %s doit exister dans %s", CONSTRAINT_MARKER, migration)
                .isNotNegative();
        int end = sql.indexOf(';', start);
        assertThat(end)
                .as("la contrainte %s de %s doit se terminer par un point-virgule",
                        CONSTRAINT_MARKER, migration)
                .isNotNegative();

        Matcher constraint = Pattern.compile("action\\s+IN\\s*\\((.*?)\\)", Pattern.DOTALL)
                .matcher(sql.substring(start, end));
        assertThat(constraint.find())
                .as("la contrainte CHECK sur la colonne action doit exister dans %s", migration)
                .isTrue();

        List<String> actions = new ArrayList<>();
        Matcher values = Pattern.compile("'([^']+)'").matcher(constraint.group(1));
        while (values.find()) {
            actions.add(values.group(1));
        }
        return actions;
    }
}
