package fr.insastronaute.webapi.user;

import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void searchAdminUsersWithoutFiltersReturnsPagedUsers() {
        AppUser alice = saveUser("sub-alice", "alice@example.com", "Alice", Gender.FEMALE, null, false);
        AppUser bob = saveUser("sub-bob", "bob@example.com", "Bob", Gender.MALE, null, true);

        Page<AppUser> users = userRepository.searchAdminUsers(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20, Sort.by("displayName").ascending())
        );

        assertThat(users.getTotalElements()).isEqualTo(2);
        assertThat(users.getContent()).extracting(AppUser::getId).containsExactly(alice.getId(), bob.getId());
    }

    @Test
    void searchAdminUsersAppliesOptionalFilters() {
        UUID departmentId = UUID.randomUUID();
        AppUser alice = saveUser("sub-alice", "alice@example.com", "Alice", Gender.FEMALE, departmentId, true);
        saveUser("sub-bob", "bob@example.com", "Bob", Gender.MALE, departmentId, true);
        saveUser("sub-alicia", "alicia@example.com", "Alicia", Gender.FEMALE, UUID.randomUUID(), true);
        saveUser("sub-alina", "alina@example.com", "Alina", Gender.FEMALE, departmentId, false);

        Page<AppUser> users = userRepository.searchAdminUsers(
                " ali ",
                departmentId,
                Gender.FEMALE,
                true,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20, Sort.by("displayName").ascending())
        );

        assertThat(users.getTotalElements()).isEqualTo(1);
        assertThat(users.getContent()).extracting(AppUser::getId).containsExactly(alice.getId());
    }

    /**
     * Retrouver les apprentis d'un coup, sans les chercher un par un.
     *
     * <p>La coche PERF est posee a la main : sans filtre, verifier qu'une promotion
     * entiere la porte demanderait d'ouvrir chaque fiche. Le filtre passe par la requete
     * et non par un tri d'apres-coup, sinon les pages sont de tailles inegales et le
     * compteur est faux.</p>
     */
    @Test
    void searchAdminUsersCanIsolateApprentices() {
        UUID departmentId = UUID.randomUUID();
        AppUser apprentice = saveUser("sub-app", "app@example.com", "Apprentie", Gender.FEMALE, departmentId, false);
        apprentice.setPerf(true);
        userRepository.saveAndFlush(apprentice);
        saveUser("sub-classmate", "classmate@example.com", "Camarade", Gender.MALE, departmentId, false);

        Page<AppUser> users = userRepository.searchAdminUsers(
                null, null, null, null, null, null, null, null, true,
                PageRequest.of(0, 20, Sort.by("displayName").ascending())
        );

        assertThat(users.getTotalElements()).isEqualTo(1);
        assertThat(users.getContent()).extracting(AppUser::getId).containsExactly(apprentice.getId());
    }

    /**
     * Un joueur supprime dont le registre retient la fiche ne se melange plus aux vivants.
     *
     * <p>Sa ligne reste en base parce qu'une trace le nomme, pas parce qu'il joue encore :
     * le voir dans la liste ordinaire faisait chercher longtemps pourquoi un compte
     * « existe » sans repondre.</p>
     */
    @Test
    void deletedPlayersAreSetApartFromTheLiving() {
        AppUser alive = saveUser("sub-alive", "alive@example.com", "Vivante", Gender.FEMALE, null, false);
        AppUser gone = saveUser("sub-gone", "gone@example.com", "Partie", Gender.FEMALE, null, true);
        gone.setDeletedAt(java.time.Instant.parse("2026-09-01T10:00:00Z"));
        userRepository.saveAndFlush(gone);

        Page<AppUser> living = userRepository.searchAdminUsers(
                null, null, null, null, null, null, null, false, null, PageRequest.of(0, 20));
        Page<AppUser> deleted = userRepository.searchAdminUsers(
                null, null, null, null, null, null, null, true, null, PageRequest.of(0, 20));
        Page<AppUser> everyone = userRepository.searchAdminUsers(
                null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(living.getContent()).extracting(AppUser::getId).containsExactly(alive.getId());
        assertThat(deleted.getContent()).extracting(AppUser::getId).containsExactly(gone.getId());
        assertThat(everyone.getContent()).hasSize(2);
    }

    /** Les quatre etats d'accueil se demandent un par un. */
    @Test
    void onboardingStatusSortsStudentsIntoFourBuckets() {
        AppUser notStarted = saveUser("sub-1", "a@example.com", "Aucune", Gender.FEMALE, null, false);
        AppUser pending = saveUser("sub-2", "b@example.com", "Attente", Gender.FEMALE, null, false);
        AppUser validated = saveUser("sub-3", "c@example.com", "Acceptee", Gender.FEMALE, null, false);
        AppUser rejected = saveUser("sub-4", "d@example.com", "Refusee", Gender.FEMALE, null, false);
        pending.setOnboardingStatus(OnboardingStatus.PENDING);
        validated.setOnboardingStatus(OnboardingStatus.VALIDATED);
        rejected.setOnboardingStatus(OnboardingStatus.REJECTED);
        userRepository.saveAllAndFlush(java.util.List.of(pending, validated, rejected));

        assertThat(searchByStatus(OnboardingStatus.NOT_STARTED)).containsExactly(notStarted.getId());
        assertThat(searchByStatus(OnboardingStatus.PENDING)).containsExactly(pending.getId());
        assertThat(searchByStatus(OnboardingStatus.VALIDATED)).containsExactly(validated.getId());
        assertThat(searchByStatus(OnboardingStatus.REJECTED)).containsExactly(rejected.getId());
    }

    private java.util.List<UUID> searchByStatus(OnboardingStatus status) {
        return userRepository.searchAdminUsers(
                        null, null, null, null, null, null, status, false, null, PageRequest.of(0, 20))
                .getContent().stream().map(AppUser::getId).toList();
    }

    private AppUser saveUser(
            String keycloakSub,
            String email,
            String displayName,
            Gender gender,
            UUID departmentId,
            boolean hiddenFromLeaderboards
    ) {
        AppUser user = new AppUser();
        user.setKeycloakSub(keycloakSub);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setGender(gender);
        user.setDepartment(departmentId);
        user.setHiddenFromLeaderboards(hiddenFromLeaderboards);
        return userRepository.saveAndFlush(user);
    }
}
