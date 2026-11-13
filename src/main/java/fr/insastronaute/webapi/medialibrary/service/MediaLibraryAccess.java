package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.medialibrary.repository.MediaLibraryGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;

/**
 * Qui peut ouvrir la Mediatheque : un administrateur, ou un membre du staff qu'un
 * administrateur a designe.
 *
 * <p>Le droit se relit en base a chaque requete. Un role Keycloak resterait dans la
 * session jusqu'a son rafraichissement ; une ligne retiree, elle, coupe l'acces des la
 * requete suivante.</p>
 */
@Service
public class MediaLibraryAccess {

    private static final String ADMIN = "ROLE_ADMIN";

    private final MediaLibraryGrantRepository grants;

    public MediaLibraryAccess(MediaLibraryGrantRepository grants) {
        this.grants = grants;
    }

    @Transactional(readOnly = true)
    public boolean canUse(String keycloakSub, Collection<String> authorities) {
        if (authorities == null) {
            return false;
        }
        if (authorities.contains(ADMIN)) {
            return true;
        }
        if (keycloakSub == null || !isStaff(authorities)) {
            return false;
        }
        return grants.existsByUser_KeycloakSub(keycloakSub);
    }

    public void require(String keycloakSub, Collection<String> authorities) {
        if (!canUse(keycloakSub, authorities)) {
            throw new ForbiddenException("La Médiathèque ne vous est pas ouverte.");
        }
    }

    static boolean isStaff(Collection<String> authorities) {
        return Arrays.stream(SecurityConfig.STAFF_ROLES).anyMatch(role -> authorities.contains("ROLE_" + role));
    }
}
