package fr.insastronaute.webapi.cardteam;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CardTeamMigrationTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V90__card_teams.sql");

    private static final Path PAIR_MIGRATION =
            Path.of("src/main/resources/db/migration/V126__card_team_born_from_a_pair.sql");

    @Test
    void migrationCreatesTeamsMembershipRequestsInvitationsAndExtensionFlag() throws IOException {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("create table card_teams")
                .contains("create table card_team_members")
                .contains("create table card_team_join_requests")
                .contains("create table card_team_invitations")
                .contains("unique (user_id)")
                .contains("add column extension boolean not null default false");
    }

    /**
     * Une equipe nait a deux, donc une invitation peut n'en viser aucune.
     *
     * <p>Flyway ne tourne pas dans les tests -- le schema vient de Hibernate -- donc ce
     * SQL n'est execute qu'en production. La contrainte est ce qui empeche une invitation
     * sans equipe ni nom : elle n'aboutirait a rien au moment de l'accepter.</p>
     */
    @Test
    void inviteesCanCarryTheNameOfATeamThatDoesNotExistYet() throws IOException {
        assertThat(PAIR_MIGRATION).exists();
        String sql = Files.readString(PAIR_MIGRATION).toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("alter column team_id drop not null")
                .contains("add column proposed_team_name varchar(60)")
                .contains("check ((team_id is null) <> (proposed_team_name is null))");
    }
}
