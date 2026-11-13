package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'annuaire des roles.
 *
 * <p>L'application ne connait pas les roles : ils vivent dans Keycloak. On lui demande les
 * membres de chaque role plutot que les roles de chaque compte -- quinze appels contre deux
 * mille.</p>
 */
class RealmRoleDirectoryTest {

    private final KeycloakRealmRoleGateway gateway = mock(KeycloakRealmRoleGateway.class);

    @Test
    void gathersEveryStaffRoleIntoOneSet() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(List.of("sub-admin")));
        when(gateway.listUsersInRealmRole("RESPO_CARTES")).thenReturn(Optional.of(List.of("sub-cartes", "sub-admin")));

        RealmRoleDirectory directory = new RealmRoleDirectory(gateway);

        assertThat(directory.staff()).containsExactlyInAnyOrder("sub-admin", "sub-cartes");
        assertThat(directory.isStaff("sub-cartes")).isTrue();
        assertThat(directory.isStaff("sub-joueur")).isFalse();
        assertThat(directory.isStaff(null)).isFalse();
    }

    /**
     * Les deux populations sont lues d'un seul rafraichissement, et restent distinctes :
     * le role etudiant ne fait pas entrer dans le staff, ni l'inverse.
     */
    @Test
    void keepsPanelStaffSeparateFromTeamStaffAndStudents() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(List.of("sub-admin")));
        when(gateway.listUsersInRealmRole("STUDENT_LIMITED"))
                .thenReturn(Optional.of(List.of("sub-limited", "sub-both")));
        when(gateway.listUsersInRealmRole("STUDENT"))
                .thenReturn(Optional.of(List.of("sub-student", "sub-both")));

        RealmRoleDirectory directory = new RealmRoleDirectory(gateway);

        assertThat(directory.staff()).doesNotContain("sub-limited", "sub-both");
        assertThat(directory.teamStaff()).containsExactlyInAnyOrder("sub-admin", "sub-limited", "sub-both");
        assertThat(directory.students()).containsExactlyInAnyOrder("sub-student", "sub-both");
        assertThat(directory.staff()).containsExactly("sub-admin");
        assertThat(directory.isKnown()).isTrue();
    }

    /**
     * Le resultat tient quelques minutes : sans cache, chaque frappe dans le champ de
     * recherche relancerait quinze appels a Keycloak.
     */
    @Test
    void asksKeycloakOnceForSeveralReads() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of("sub-admin")));
        RealmRoleDirectory directory = new RealmRoleDirectory(gateway);

        directory.staff();
        directory.students();
        directory.isStaff("sub-admin");

        verify(gateway, times(1)).listUsersInRealmRole("ADMIN");
    }

    /**
     * Un role illisible ne doit pas amputer l'annuaire.
     *
     * <p>C'est le defaut qu'un journal de production a montre : la passerelle rendait une
     * liste vide aussi bien pour un role sans membre que pour un appel echoue. Un seul
     * echec suffisait alors a faire passer ses titulaires pour des joueurs -- et, cote
     * classement, a les en faire disparaitre.</p>
     */
    @Test
    void keepsThePreviousDirectoryWhenOneRoleCannotBeRead() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(List.of("sub-admin")));
        RealmRoleDirectory directory = new RealmRoleDirectory(gateway, Duration.ZERO, Duration.ZERO);
        assertThat(directory.staff()).containsExactly("sub-admin");

        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.empty());

        assertThat(directory.staff())
                .as("un role illisible garde l'annuaire precedent au lieu de le vider")
                .containsExactly("sub-admin");
        verify(gateway, atLeastOnce()).listUsersInRealmRole("ADMIN");
    }

    @Test
    void keepsTheWholePreviousDirectoryWhenLimitedRoleCannotBeRead() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(List.of("sub-admin")));
        when(gateway.listUsersInRealmRole("STUDENT_LIMITED"))
                .thenReturn(Optional.of(List.of("sub-limited")));
        when(gateway.listUsersInRealmRole("STUDENT"))
                .thenReturn(Optional.of(List.of("sub-student")));
        RealmRoleDirectory directory = new RealmRoleDirectory(gateway, Duration.ZERO, Duration.ZERO);

        assertThat(directory.teamStaff()).containsExactlyInAnyOrder("sub-admin", "sub-limited");

        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(List.of("sub-new-admin")));
        when(gateway.listUsersInRealmRole("STUDENT_LIMITED")).thenReturn(Optional.empty());
        when(gateway.listUsersInRealmRole("STUDENT"))
                .thenReturn(Optional.of(List.of("sub-new-student")));

        assertThat(directory.teamStaff())
                .as("an unreadable limited role keeps the previous team-staff snapshot")
                .containsExactlyInAnyOrder("sub-admin", "sub-limited");
        assertThat(directory.staff()).containsExactly("sub-admin");
        assertThat(directory.students()).containsExactly("sub-student");
    }

    /**
     * Tant que Keycloak n'a jamais repondu, l'annuaire ne fait autorite sur rien.
     *
     * <p>Sans ce drapeau, un annuaire vide au demarrage se lirait « personne n'est
     * etudiant », et le classement se viderait de lui-meme.</p>
     */
    @Test
    void isNotAuthoritativeUntilKeycloakHasAnswered() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.empty());

        RealmRoleDirectory directory = new RealmRoleDirectory(gateway, Duration.ZERO, Duration.ZERO);

        assertThat(directory.isKnown()).isFalse();
        assertThat(directory.students()).isEmpty();
    }

    /**
     * Un role absent du realm, lui, est une reponse : il n'a personne, et l'annuaire doit
     * se construire sans lui. C'est le cas de MODERATOR, que ce realm ne declare pas.
     */
    @Test
    void aRoleTheRealmDoesNotDeclareSimplyHasNoMembers() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(List.of("sub-admin")));
        when(gateway.listUsersInRealmRole("MODERATOR")).thenReturn(Optional.of(List.of()));

        RealmRoleDirectory directory = new RealmRoleDirectory(gateway);

        assertThat(directory.staff()).containsExactly("sub-admin");
    }

    @Test
    void studentLimitedReturnsOnlyStudentLimitedRoleMembers() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("STUDENT_LIMITED")).thenReturn(Optional.of(List.of("sub-limited-1")));
        when(gateway.listUsersInRealmRole("STUDENT")).thenReturn(Optional.of(List.of("sub-student-1")));

        RealmRoleDirectory directory = new RealmRoleDirectory(gateway);

        assertThat(directory.studentLimited()).containsExactly("sub-limited-1");
        assertThat(directory.isStudentLimited("sub-limited-1")).isTrue();
        assertThat(directory.isStudentLimited("sub-student-1")).isFalse();
    }

    @Test
    void invalidateCacheForcesReFetchOnNextCall() {
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("STUDENT_LIMITED"))
                .thenReturn(Optional.of(List.of("sub-1")))
                .thenReturn(Optional.of(List.of("sub-1", "sub-2")));

        RealmRoleDirectory directory = new RealmRoleDirectory(gateway);
        assertThat(directory.studentLimited()).containsExactly("sub-1");

        directory.invalidateCache();

        assertThat(directory.studentLimited()).containsExactlyInAnyOrder("sub-1", "sub-2");
    }
}
