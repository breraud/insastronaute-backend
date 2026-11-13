package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Qui figure au classement.
 *
 * <p>La regle tient en une phrase : le role etudiant, et pas de role de staff. « Pas de
 * role du tout » ne suffisait pas -- un compte cree mais jamais valide n'a rien fait, et un
 * respo qui joue en marge n'a pas a concourir.</p>
 *
 * <p>Le predicat est prive, et c'est voulu : il n'a pas d'interet hors du service. On
 * l'atteint ici par reflexion plutot que de l'ouvrir pour le seul confort d'un test, ou de
 * monter tout un contexte Spring pour verifier trois booleens.</p>
 */
class LeaderboardEligibilityTest {

    private static boolean rankable(RealmRoleDirectory directory, AppUser user) throws Exception {
        LeaderboardService service = new LeaderboardService(
                null, null, null, null, null, null, null, null, null, null, null, null, directory);
        Method method = LeaderboardService.class.getDeclaredMethod("rankable", AppUser.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, user);
    }

    private static AppUser user(String sub, boolean hidden) {
        AppUser user = new AppUser();
        user.setKeycloakSub(sub);
        user.setHiddenFromLeaderboards(hidden);
        return user;
    }

    private static RealmRoleDirectory directory(List<String> staff, List<String> students) {
        return directory(staff, List.of(), students);
    }

    private static RealmRoleDirectory directory(
            List<String> staff,
            List<String> limitedStudents,
            List<String> students
    ) {
        KeycloakRealmRoleGateway gateway = mock(KeycloakRealmRoleGateway.class);
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.of(List.of()));
        when(gateway.listUsersInRealmRole("ADMIN")).thenReturn(Optional.of(staff));
        when(gateway.listUsersInRealmRole("STUDENT_LIMITED")).thenReturn(Optional.of(limitedStudents));
        when(gateway.listUsersInRealmRole("STUDENT")).thenReturn(Optional.of(students));
        return new RealmRoleDirectory(gateway);
    }

    @Test
    void aStudentWithoutAStaffRoleIsRanked() throws Exception {
        RealmRoleDirectory annuaire = directory(List.of(), List.of("sub-eleve"));
        assertThat(rankable(annuaire, user("sub-eleve", false))).isTrue();
    }

    @Test
    void aStudentWhoIsAlsoStaffIsNotRanked() throws Exception {
        RealmRoleDirectory annuaire = directory(List.of("sub-respo"), List.of("sub-respo"));
        assertThat(rankable(annuaire, user("sub-respo", false))).isFalse();
    }

    @Test
    void aLimitedStudentIsNotRanked() throws Exception {
        RealmRoleDirectory annuaire = directory(
                List.of(),
                List.of("sub-limited"),
                List.of()
        );

        assertThat(rankable(annuaire, user("sub-limited", false))).isFalse();
    }

    @Test
    void aStudentWhoIsAlsoLimitedIsNotRanked() throws Exception {
        RealmRoleDirectory annuaire = directory(
                List.of(),
                List.of("sub-both"),
                List.of("sub-both")
        );

        assertThat(rankable(annuaire, user("sub-both", false))).isFalse();
    }

    /** Le compte cree mais jamais valide : il existe, il n'a rien fait, il ne concourt pas. */
    @Test
    void anAccountWithoutTheStudentRoleIsNotRanked() throws Exception {
        RealmRoleDirectory annuaire = directory(List.of(), List.of("sub-eleve"));
        assertThat(rankable(annuaire, user("sub-inconnu", false))).isFalse();
    }

    /** Masque a la main : la regle des roles ne le repeche pas. */
    @Test
    void aHiddenAccountStaysOutWhateverItsRoles() throws Exception {
        RealmRoleDirectory annuaire = directory(List.of(), List.of("sub-eleve"));
        assertThat(rankable(annuaire, user("sub-eleve", true))).isFalse();
    }

    /**
     * La reserve qui compte le plus.
     *
     * <p>Keycloak muet -- une panne, ou le defi Cloudflare qui a bloque l'annuaire en
     * production -- ne doit pas vider le classement. Ne pas savoir n'est pas savoir que
     * non : on retombe alors sur l'ancienne regle, tout le monde sauf les masques.</p>
     */
    @Test
    void anUnreachableKeycloakLeavesTheLeaderboardAsItWas() throws Exception {
        KeycloakRealmRoleGateway gateway = mock(KeycloakRealmRoleGateway.class);
        when(gateway.listUsersInRealmRole(anyString())).thenReturn(Optional.empty());
        // Le constructeur public suffit : aucune lecture n'aboutissant, l'annuaire ne fait
        // jamais autorite, et la peremption n'entre pas en jeu.
        RealmRoleDirectory annuaire = new RealmRoleDirectory(gateway);

        assertThat(rankable(annuaire, user("sub-inconnu", false))).isTrue();
        assertThat(rankable(annuaire, user("sub-inconnu", true))).isFalse();
    }

    /** Sans annuaire du tout -- les constructions de test l'omettent -- rien ne change. */
    @Test
    void noDirectoryAtAllKeepsTheFormerRule() throws Exception {
        assertThat(rankable(null, user("sub-inconnu", false))).isTrue();
    }
}
