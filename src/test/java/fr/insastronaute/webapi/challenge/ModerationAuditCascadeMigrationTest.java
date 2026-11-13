package fr.insastronaute.webapi.challenge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qui empêche la trace de modération de disparaître avec ce qu'elle raconte.
 *
 * <p>Flyway ne tourne pas dans les tests — le schéma vient d'Hibernate, qui ne génère
 * aucune clé étrangère pour {@code moderation_audit_events} puisque l'entité ne porte
 * qu'un UUID. Tout ce SQL ne s'exécute qu'en production, et ces assertions sont tout
 * ce qui le relit.</p>
 *
 * <p>L'histoire de ces trois migrations, parce qu'elle explique la forme finale :
 * V72 posait une clé étrangère sans clause, donc RESTRICT, et un défi dont une preuve
 * avait été modérée devenait indestructible. V167 l'a passée en CASCADE, ce qui
 * débloquait au prix de la trace. V168 la retire : une table d'audit ne doit ni
 * empêcher ce qu'elle trace de disparaître, ni disparaître avec lui.</p>
 */
class ModerationAuditCascadeMigrationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /**
     * L'audit ne dépend plus de la survie de la soumission.
     *
     * <p>{@code submission_id} reste, comme identifiant historique : c'est par lui que
     * ces lignes se lisent, et la seule chose qui les relie entre elles. Ce qui part,
     * c'est la contrainte — et avec elle les deux mauvaises issues, le verrou et
     * l'effacement.</p>
     */
    @Test
    void theModerationAuditOutlivesWhatItRecords() throws IOException {
        Path migration = MIGRATIONS.resolve("V168__moderation_audit_survives_deletion.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("drop constraint if exists moderation_audit_events_submission_id_fkey")
                .contains("add column submission_deleted_at")
                // Et surtout pas une cascade rétablie par inadvertance.
                .doesNotContain("on delete cascade");
    }

    /**
     * Supprimer un défi ne détruit plus rien, donc ne se heurte plus à rien.
     *
     * <p>C'est la vraie sortie du blocage de V72 : le geste pose une date au lieu
     * d'effacer. Les points tombent parce que le recalcul écarte les défis supprimés,
     * et reviennent quand la date part.</p>
     */
    @Test
    void deletingAChallengeBecomesReversible() throws IOException {
        Path migration = MIGRATIONS.resolve("V169__challenge_soft_delete.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("alter table challenges")
                .contains("add column deleted_at")
                .contains("add column deleted_by_keycloak_sub");

        // La restauration s'écrit au registre, et les deux contraintes énumèrent les
        // gestes en dur : sans ces deux lignes, chaque restauration violerait l'une ou
        // l'autre.
        assertThat(sql)
                .contains("'challenge_restored'")
                .contains("ck_admin_action_events_action")
                .contains("ck_admin_action_events_target_presence");
    }
}
