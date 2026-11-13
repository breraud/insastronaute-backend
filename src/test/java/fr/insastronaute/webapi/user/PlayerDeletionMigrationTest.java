package fr.insastronaute.webapi.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway ne tourne pas dans les tests -- le schema vient d'Hibernate -- donc ce SQL n'est
 * execute qu'en production. Ces deux assertions sont tout ce qui le relit.
 */
class PlayerDeletionMigrationTest {

    @Test
    void theUsersTableLearnsWhenAPlayerWasDeleted() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V127__delete_players.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertThat(sql).contains("add column deleted_at timestamp with time zone");
    }

    /**
     * PLAYER_DELETED rejoint la liste des actions sans cible.
     *
     * <p>Sans cette ligne, chaque suppression violerait
     * {@code ck_admin_action_events_target_presence} : le geste s'ecrit sans cible, et la
     * contrainte l'exigerait. La liste est une enumeration en dur cote base, donc ajouter
     * la valeur cote Java ne suffit pas.</p>
     */
    @Test
    void theRegistryAcceptsATargetlessDeletionEvent() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V128__player_deleted_action.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("drop constraint ck_admin_action_events_target_presence")
                .contains("'player_deleted'")
                .contains("(target_user_id is null)");
    }
}
