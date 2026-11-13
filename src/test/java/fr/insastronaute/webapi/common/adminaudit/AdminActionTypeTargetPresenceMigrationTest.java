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
 * La contrainte de presence de cible et {@code AdminActionType.isGlobalScope()}
 * doivent dire la meme chose.
 *
 * <p>Une ligne de portee globale n'a pas de cible, une ligne nominative en a
 * obligatoirement une. Cette regle est ecrite deux fois - une fois en Java
 * pour que l'appelant se prenne une erreur comprehensible, une fois dans le
 * schema pour qu'un cablage errone echoue a l'insertion au lieu de produire
 * une ligne vide de sens. Deux endroits qui enoncent une seule regle, c'est
 * exactement ainsi qu'ils divergent : ajouter un type global sans toucher a la
 * migration compilerait, passerait toute la suite, et echouerait au premier
 * INSERT en production.</p>
 *
 * <p>Meme forme que {@link AdminActionTypeMigrationTest}, qui compare la liste
 * des valeurs autorisees : la migration qui fait foi est decouverte, pas
 * nommee.</p>
 */
class AdminActionTypeTargetPresenceMigrationTest {

    private static final Path MIGRATIONS_DIR = Paths.get("src/main/resources/db/migration");

    private static final String CONSTRAINT_MARKER = "ck_admin_action_events_target_presence";

    private static final Pattern VERSION_PREFIX = Pattern.compile("^V(\\d+)__");

    /**
     * La fin du predicat : la parenthese qui ferme la liste des types, puis
     * l'egalite avec {@code target_user_id IS NULL}. C'est ce qui fait dire a
     * la contrainte les deux sens d'un coup, et c'est exactement ce qu'une
     * inversion (<code>&lt;&gt;</code>, ou <code>IS NOT NULL</code>) casserait
     * sans toucher a la liste des types.
     */
    private static final Pattern TARGET_PRESENCE_PREDICATE =
            Pattern.compile("\\)\\s*=\\s*\\(\\s*target_user_id\\s+IS\\s+NULL\\s*\\)", Pattern.CASE_INSENSITIVE);

    @Test
    void theTargetPresenceConstraintNamesExactlyTheGlobalScopeTypes() throws IOException {
        Path migration = latestMigrationRecreatingTheConstraint();
        String constraint = targetPresenceConstraint(readMigration(migration), migration);

        List<String> declaredInSql = actionsNamedByTheConstraint(constraint, migration);
        List<String> declaredInJava = new ArrayList<>();
        for (AdminActionType action : AdminActionType.values()) {
            if (action.isGlobalScope()) {
                declaredInJava.add(action.name());
            }
        }

        // Deux listes vides se compareraient egales sans rien avoir verifie.
        assertThat(declaredInJava)
                .as("au moins un type doit etre de portee globale")
                .isNotEmpty();

        assertThat(declaredInSql)
                .as("la contrainte %s de %s doit nommer exactement les types de portee globale",
                        CONSTRAINT_MARKER, migration)
                .containsExactlyInAnyOrderElementsOf(declaredInJava);

        // Citer les bons types ne suffit pas : c'est la forme du predicat qui
        // dit la regle dans les deux sens. Une migration qui ecrirait <> au
        // lieu de =, ou IS NOT NULL au lieu de IS NULL, nommerait exactement
        // les memes types et inverserait la regle en production - et aucun
        // test JVM ne peut la voir a l'oeuvre, la suite tournant sur H2 avec
        // Flyway desactive. Le seul endroit ou on peut la lire, c'est le
        // texte.
        assertThat(constraint)
                .as("la contrainte %s de %s doit egaler la liste des types globaux a "
                        + "(target_user_id IS NULL) : cible interdite sur eux, obligatoire ailleurs",
                        CONSTRAINT_MARKER, migration)
                .containsPattern(TARGET_PRESENCE_PREDICATE);
    }

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
     * Le texte de la contrainte, a partir de la derniere mention de son nom et
     * jusqu'au point-virgule qui la termine : la migration recree aussi la
     * contrainte sur les valeurs autorisees, et lire tout le fichier
     * confondrait les deux listes.
     *
     * <p>La <em>derniere</em> mention, pas la premiere. Ajouter un type de
     * portee globale demande de supprimer la contrainte et de la recreer dans
     * le meme fichier - Postgres ne sait pas etendre un CHECK en place, c'est
     * le motif de V111 et V112. Dans un tel fichier la premiere mention est le
     * {@code DROP CONSTRAINT ...;}, le texte extrait s'arreterait la, aucun
     * {@code action IN (...)} n'y serait trouve, et le test echouerait sur une
     * migration parfaitement correcte. La derniere mention est toujours celle
     * qui fait foi, et si une migration ne fait que supprimer la contrainte,
     * l'extraction ne trouve toujours pas de liste et le test echoue - a
     * juste titre.</p>
     */
    private String targetPresenceConstraint(String sql, Path migration) {
        int start = sql.lastIndexOf(CONSTRAINT_MARKER);
        assertThat(start)
                .as("la contrainte %s doit exister dans %s", CONSTRAINT_MARKER, migration)
                .isNotNegative();
        int end = sql.indexOf(';', start);
        assertThat(end)
                .as("la contrainte %s de %s doit se terminer par un point-virgule",
                        CONSTRAINT_MARKER, migration)
                .isNotNegative();
        return sql.substring(start, end);
    }

    private List<String> actionsNamedByTheConstraint(String constraint, Path migration) {
        Matcher actions = Pattern.compile("action\\s+IN\\s*\\((.*?)\\)", Pattern.DOTALL).matcher(constraint);
        assertThat(actions.find())
                .as("la contrainte %s de %s doit lister les types sans cible", CONSTRAINT_MARKER, migration)
                .isTrue();

        List<String> named = new ArrayList<>();
        Matcher values = Pattern.compile("'([^']+)'").matcher(actions.group(1));
        while (values.find()) {
            named.add(values.group(1));
        }
        return named;
    }
}
