package fr.insastronaute.webapi.common.adminaudit;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'auteur peut manquer, mais seulement sur une ligne reprise.
 *
 * <p>La reprise des decisions passees identifie le moderateur en re-hachant le
 * {@code keycloak_sub} de chaque utilisateur ; un moderateur parti ne hache
 * plus vers rien, et sa decision est reprise sans auteur plutot qu'omise.
 * Rendre la colonne nullable pour cela ouvre la porte a une ligne ecrite en
 * direct sans auteur - exactement ce qui viderait le registre de sa raison
 * d'etre. La contrainte referme cette porte, et ce test la garde.</p>
 *
 * <p>Aucun test JVM ne peut voir la contrainte a l'oeuvre : la suite tourne sur
 * H2 avec Flyway desactive et un schema regenere qui ne cree jamais les CHECK.
 * Le seul endroit ou la regle se lit, c'est le texte de la migration - d'ou la
 * meme forme que {@link AdminActionTypeTargetPresenceMigrationTest} : la
 * migration qui fait foi est decouverte, pas nommee, et c'est la forme du
 * predicat qu'on epingle, pas seulement sa presence.</p>
 */
class AdminActionActorPresenceMigrationTest {

    private static final Path MIGRATIONS_DIR = Paths.get("src/main/resources/db/migration");

    private static final String CONSTRAINT_MARKER = "ck_admin_action_events_actor_presence";

    private static final Pattern VERSION_PREFIX = Pattern.compile("^V(\\d+)__");

    /**
     * Les deux colonnes dont la regle parle. Elle en met une en condition de
     * l'autre : un predicat qui n'en nommerait qu'une ne dirait pas la regle.
     */
    private static final List<String> COLUMNS_THE_RULE_IS_ABOUT = List.of("backfilled", "actor_user_id");

    /**
     * La forme du predicat : une ligne reprise, ou un auteur present.
     *
     * <p>C'est la polarite qui compte, et c'est elle qu'une migration
     * ulterieure peut inverser sans que rien d'autre ne bronche.
     * {@code (backfilled = FALSE) OR (actor_user_id IS NOT NULL)} nommerait les
     * memes colonnes et n'exigerait un auteur que sur les lignes reprises,
     * c'est-a-dire l'inverse exact de la regle voulue ; {@code IS NULL} au lieu
     * de {@code IS NOT NULL} ferait pire encore. Les deux passeraient un test
     * qui se contenterait de chercher les noms de colonnes.</p>
     */
    private static final Pattern ACTOR_PRESENCE_PREDICATE = Pattern.compile(
            "\\(\\s*backfilled\\s*=\\s*TRUE\\s*\\)\\s*OR\\s*\\(\\s*actor_user_id\\s+IS\\s+NOT\\s+NULL\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    @Test
    void theActorPresenceConstraintDemandsAnAuthorOnEveryLiveRow() throws IOException {
        Path migration = latestMigrationRecreatingTheConstraint();
        String constraint = actorPresenceConstraint(readMigration(migration), migration);

        // Une extraction qui ne rendrait que le nom de la contrainte
        // comparerait du vide a du vide et passerait sans rien avoir verifie.
        assertThat(COLUMNS_THE_RULE_IS_ABOUT)
                .as("la regle doit porter sur au moins une colonne")
                .isNotEmpty();

        List<String> mentioned = new ArrayList<>();
        String lowered = constraint.toLowerCase(Locale.ROOT);
        for (String column : COLUMNS_THE_RULE_IS_ABOUT) {
            if (lowered.contains(column)) {
                mentioned.add(column);
            }
        }

        assertThat(mentioned)
                .as("la contrainte %s de %s doit nommer les deux colonnes de la regle",
                        CONSTRAINT_MARKER, migration)
                .containsExactlyElementsOf(COLUMNS_THE_RULE_IS_ABOUT);

        assertThat(constraint)
                .as("la contrainte %s de %s doit n'autoriser l'absence d'auteur que sur une "
                        + "ligne reprise : (backfilled = TRUE) OR (actor_user_id IS NOT NULL)",
                        CONSTRAINT_MARKER, migration)
                .containsPattern(ACTOR_PRESENCE_PREDICATE);
    }

    /**
     * Le schema autorise l'absence d'auteur sur une ligne reprise ; l'entite
     * doit l'autoriser aussi.
     *
     * <p>Deuxieme endroit pour une seule regle, donc deuxieme occasion de
     * diverger : remettre {@code nullable = false} sur l'association
     * compilerait, et le schema de test regenere refuserait des lors la ligne
     * meme que la migration vient de rendre possible - alors que la production,
     * elle, l'accepterait. La reprise echouerait en test sans qu'on sache
     * pourquoi.</p>
     */
    @Test
    void theEntityAllowsTheMissingAuthorTheConstraintPermits() throws NoSuchFieldException {
        Field actor = AdminActionEvent.class.getDeclaredField("actor");
        JoinColumn joinColumn = actor.getAnnotation(JoinColumn.class);

        assertThat(joinColumn)
                .as("l'auteur doit rester une association explicite vers users")
                .isNotNull();
        assertThat(joinColumn.name()).isEqualTo("actor_user_id");
        assertThat(joinColumn.nullable())
                .as("l'auteur doit pouvoir manquer : c'est ce que %s autorise sur une ligne reprise",
                        CONSTRAINT_MARKER)
                .isTrue();
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
     * Le texte de la contrainte, a partir de la <em>derniere</em> mention de
     * son nom et jusqu'au point-virgule qui la termine.
     *
     * <p>La derniere, pas la premiere : Postgres ne sait pas etendre un CHECK
     * en place, et une migration qui voudrait changer la regle supprimerait la
     * contrainte puis la recreerait dans le meme fichier. La premiere mention
     * y serait le {@code DROP}, dont le texte extrait ne contiendrait aucun
     * predicat, et le test echouerait sur une migration correcte.</p>
     */
    private String actorPresenceConstraint(String sql, Path migration) {
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
}
