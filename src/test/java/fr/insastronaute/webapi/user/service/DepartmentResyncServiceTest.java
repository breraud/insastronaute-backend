package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakGroupMembershipGateway;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Relire le departement d'un joueur sans attendre sa prochaine connexion.
 *
 * <p>Le departement est ecrit a la connexion, depuis le jeton : deplacer quelqu'un de
 * groupe dans Keycloak ne se voit nulle part tant qu'il ne revient pas.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentResyncServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private KeycloakGroupMembershipGateway gateway;

    private DepartmentResyncService service() {
        return new DepartmentResyncService(userRepository, departmentRepository, gateway, transactionManager());
    }

    private AppUser player(UUID departmentId) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setDepartment(departmentId);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private Department department(String shortName) {
        Department department = new Department();
        department.setId(UUID.randomUUID());
        department.setShortName(shortName);
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        return department;
    }

    /**
     * L'attribut prime sur les groupes, comme a la connexion.
     *
     * <p>C'est le defaut qui a vide soixante-dix pour cent des departements : la
     * resynchronisation ne lisait que les groupes, et la connexion lit d'abord l'attribut.
     * Les deux divergent des que la synchronisation qui pousse l'un vers l'autre est
     * desactivee, echoue, ou vise un groupe absent.</p>
     */
    @Test
    void readsTheDepartmentAttributeFirst() {
        Department iti = department("ITI");
        AppUser player = player(null);
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of("ITI"));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("ITI")).thenReturn(Optional.of(iti));

        var outcome = service().resync(player.getId());

        assertThat(outcome.currentDepartment()).isEqualTo("ITI");
        assertThat(player.getDepartment()).isEqualTo(iti.getId());
        // Les groupes ne sont meme pas interroges : l'attribut a repondu.
        verify(gateway, never()).listUserGroups(any());
    }

    /**
     * Une lecture muette n'efface rien.
     *
     * <p>La connexion peut se permettre d'effacer : elle lit un jeton qui decrit
     * l'utilisateur en entier. Ici on interroge deux endpoints, et leur silence ne prouve
     * pas une absence -- c'est ce qui a vide les departements la premiere fois.</p>
     */
    @Test
    void keepsTheDepartmentWhenKeycloakSaysNothing() {
        Department gm = department("GM");
        AppUser player = player(gm.getId());
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of());
        when(gateway.listUserGroups("player-sub")).thenReturn(List.of());

        var outcome = service().resync(player.getId());

        assertThat(outcome.changed()).isFalse();
        assertThat(player.getDepartment()).isEqualTo(gm.getId());
        verify(userRepository, never()).save(any());
    }

    /** Sans attribut, on retombe sur les groupes -- l'autre moitie de la regle. */
    @Test
    void fallsBackToGroupsWhenTheAttributeIsAbsent() {
        Department iti = department("ITI");
        AppUser player = player(null);
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of());
        when(gateway.listUserGroups("player-sub"))
                .thenReturn(List.of(new KeycloakGroupMembershipGateway.Group("iti-id", "ITI")));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("ITI")).thenReturn(Optional.of(iti));

        assertThat(service().resync(player.getId()).currentDepartment()).isEqualTo("ITI");
    }

    /**
     * Un attribut que la base ne reconnait pas efface bien le departement.
     *
     * <p>Keycloak a parle : il dit « ce joueur est en BUREAU », qui n'est pas un
     * departement. C'est une reponse, pas un silence.</p>
     */
    @Test
    void clearsWhenKeycloakNamesSomethingUnknown() {
        Department gm = department("GM");
        AppUser player = player(gm.getId());
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of("BUREAU"));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("BUREAU")).thenReturn(Optional.empty());

        var outcome = service().resync(player.getId());

        assertThat(outcome.changed()).isTrue();
        assertThat(player.getDepartment()).isNull();
    }

    @Test
    void writesTheDepartmentKeycloakNowReports() {
        Department gm = department("GM");
        Department iti = department("ITI");
        AppUser player = player(gm.getId());
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of());
        when(gateway.listUserGroups("player-sub"))
                .thenReturn(List.of(new KeycloakGroupMembershipGateway.Group("iti-id", "ITI")));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("ITI")).thenReturn(Optional.of(iti));

        var outcome = service().resync(player.getId());

        assertThat(outcome.changed()).isTrue();
        assertThat(outcome.previousDepartment()).isEqualTo("GM");
        assertThat(outcome.currentDepartment()).isEqualTo("ITI");
        assertThat(player.getDepartment()).isEqualTo(iti.getId());
        verify(userRepository).save(player);
    }

    /** Rien n'a bouge : on ne reecrit pas, et on le dit. */
    @Test
    void leavesAnUnchangedDepartmentAlone() {
        Department iti = department("ITI");
        AppUser player = player(iti.getId());
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of());
        when(gateway.listUserGroups("player-sub"))
                .thenReturn(List.of(new KeycloakGroupMembershipGateway.Group("iti-id", "ITI")));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("ITI")).thenReturn(Optional.of(iti));

        var outcome = service().resync(player.getId());

        assertThat(outcome.changed()).isFalse();
        verify(userRepository, never()).save(any());
    }

    /**
     * Un groupe que la base ne connait pas ne vaut pas un departement.
     *
     * <p>Le joueur retire de tout groupe gere perd bien son departement : c'est ce que
     * Keycloak dit, et c'est ce qu'on ecrit.</p>
     */
    @Test
    void clearsTheDepartmentWhenNoGroupMatches() {
        Department gm = department("GM");
        AppUser player = player(gm.getId());
        when(gateway.listUserDepartmentAttribute("player-sub")).thenReturn(List.of());
        when(gateway.listUserGroups("player-sub"))
                .thenReturn(List.of(new KeycloakGroupMembershipGateway.Group("other-id", "BUREAU")));
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("BUREAU")).thenReturn(Optional.empty());

        var outcome = service().resync(player.getId());

        assertThat(outcome.changed()).isTrue();
        assertThat(outcome.currentDepartment()).isNull();
        assertThat(player.getDepartment()).isNull();
    }

    /**
     * Un compte sans identifiant Keycloak n'est pas un compte sans departement.
     *
     * <p>Il n'y a rien a relire pour lui : effacer ce qu'il porte au motif qu'on ne sait
     * pas demander supprimerait une information juste.</p>
     */
    @Test
    void doesNotClearWhatItCannotRead() {
        Department gm = department("GM");
        AppUser player = player(gm.getId());
        player.setKeycloakSub(null);

        var outcome = service().resync(player.getId());

        assertThat(outcome.changed()).isFalse();
        assertThat(player.getDepartment()).isEqualTo(gm.getId());
        verify(gateway, never()).listUserGroups(any());
    }

    /** Un compte illisible est compte a part, et n'arrete pas le balayage. */
    @Test
    void aSweepSurvivesAnUnreadableAccount() {
        Department iti = department("ITI");
        AppUser broken = player(iti.getId());
        AppUser fine = player(null);
        when(userRepository.findAll()).thenReturn(List.of(broken, fine));
        when(gateway.listUserDepartmentAttribute(any())).thenThrow(new RuntimeException("keycloak down"));

        var report = service().resyncAll();

        assertThat(report.examined()).isEqualTo(2);
        assertThat(report.unreadable()).isEqualTo(2);
        assertThat(report.changed()).isZero();
        assertThat(broken.getDepartment()).isEqualTo(iti.getId());
    }

    /** Un vrai gestionnaire de transaction, sans base derriere. */
    private static PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
    }
}
