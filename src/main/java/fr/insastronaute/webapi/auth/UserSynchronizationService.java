package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardEnrollmentService;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSynchronizationService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaderboardEnrollmentService leaderboardEnrollmentService;

    public UserSynchronizationService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            LeaderboardEnrollmentService leaderboardEnrollmentService
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.leaderboardEnrollmentService = leaderboardEnrollmentService;
    }

    @Transactional
    public AppUser syncWithKeycloak(OidcUser oidcUser) {
        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String displayName = firstNonBlank(oidcUser.getFullName(), oidcUser.getPreferredUsername(), email);
        Optional<AppUser> existingUser = userRepository.findByKeycloakSub(sub);
        // Un joueur supprime ne rentre plus, quoi qu'il presente. Son compte Keycloak a
        // ete efface, mais un jeton deja emis reste valable jusqu'a son expiration : sans
        // cette garde il continuerait de jouer pendant ce temps. La poser ici les ferme
        // tous d'un coup, plutot qu'un predicat a ajouter dans chaque endpoint.
        existingUser.filter(AppUser::isDeleted).ifPresent(deleted -> {
            throw new ForbiddenException("Ce compte a ete supprime.");
        });
        boolean isNewUser = existingUser.isEmpty();

        AppUser user = existingUser.orElseGet(() -> {
                    AppUser newUser = new AppUser();
                    newUser.setKeycloakSub(sub);
                    return newUser;
                });

        user.setEmail(email);
        user.setDisplayName(displayName);
        if (hasRoleOtherThanStudent(oidcUser.getAuthorities())) {
            user.setHiddenFromLeaderboards(true);
        }

        List<String> departments = oidcUser.getClaimAsStringList("department");
        boolean useDepartmentClaim = oidcUser.hasClaim("department");
        List<String> departmentCandidates = useDepartmentClaim
                ? departments
                : oidcUser.getClaimAsStringList("groups");
        UUID matchedDepartmentId = null;

        if (departmentCandidates != null && !departmentCandidates.isEmpty()) {
            for (String candidate : departmentCandidates) {
                if (candidate == null) {
                    continue;
                }

                String groupName = candidate.trim();

                if (!useDepartmentClaim && groupName.startsWith("/")) {
                    groupName = groupName.substring(1);
                }

                if (!useDepartmentClaim) {
                    int lastSlashIndex = groupName.lastIndexOf('/');
                    if (lastSlashIndex >= 0) {
                        groupName = groupName.substring(lastSlashIndex + 1);
                    }
                }

                if (groupName.isBlank()) {
                    continue;
                }

                Optional<Department> departmentOpt = departmentRepository.findByKeycloakGroupNameIgnoreCase(groupName);

                if (departmentOpt.isPresent()) {
                    matchedDepartmentId = departmentOpt.get().getId();
                    break;
                }
            }
        }

        user.setDepartment(matchedDepartmentId);
        AppUser savedUser = userRepository.save(user);

        if (isNewUser) {
            leaderboardEnrollmentService.addNewUserToLatestUserSnapshots(savedUser);
        }

        // Le departement force est deja resolu par TimeTravelFilter, qui refuse a
        // l'entree une valeur qui ne designe rien. S'il est la, il existe.
        //
        // On superpose sur l'utilisateur reel plutot que d'en reconstruire un. Une copie
        // champ par champ oubliait `passkeyPromptAnsweredAt` et `passkeyPromptSnoozedUntil`
        // -- la suggestion de passkey revenait a chaque rafraichissement malgre un refus
        // enregistre -- et une quarantaine d'appelants recoivent cet objet, dont plusieurs
        // le reenregistrent : tout ce que la liste oubliait etait efface en base. Le
        // defaut n'etait pas la liste mais le fait qu'il y en ait une.
        UUID forcedDepartmentId = TimeTravelContext.getForcedDepartmentId();
        if (forcedDepartmentId != null) {
            savedUser.overrideDepartment(forcedDepartmentId);
        }

        return savedUser;
    }

    /**
     * Le premier des candidats qui porte quelque chose, sans ses espaces de bord.
     *
     * <p>Keycloak rend le nom tel qu'il a ete tape a l'inscription. Une espace au bout ne
     * se voit sur aucun ecran, mais elle se compare : elle avait rendu un joueur
     * impossible a supprimer depuis le panel, ou l'admin retape le nom pour confirmer.
     * Elle est retiree ici, la ou le nom entre, plutot que partout ou il ressort.</p>
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private static boolean hasRoleOtherThanStudent(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return false;
        }

        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .anyMatch(authority -> !"ROLE_STUDENT".equals(authority));
    }
}
