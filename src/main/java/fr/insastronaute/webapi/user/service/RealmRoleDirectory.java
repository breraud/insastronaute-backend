package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Qui est staff, qui est etudiant.
 *
 * <p>L'application ne le sait pas : les roles vivent dans Keycloak. On lui demande les
 * membres de chaque role plutot que les roles de chaque compte -- quinze appels contre
 * deux mille -- et on garde le resultat quelques minutes.</p>
 *
 * <p>Les deux populations se lisent d'un seul rafraichissement, parce qu'elles servent a
 * la meme question posee dans deux sens : la page des comptes trie le staff, le classement
 * ne retient que les etudiants qui n'en sont pas. Les separer ferait deux caches
 * desynchronises, et un compte pourrait un instant etre ni l'un ni l'autre.</p>
 */
@Service
public class RealmRoleDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealmRoleDirectory.class);

    /** Le role que l'inscription attribue. Sans lui, un compte existe sans etre valide. */
    public static final String STUDENT_ROLE = "STUDENT";
    public static final String STUDENT_LIMITED_ROLE = "STUDENT_LIMITED";

    /**
     * Assez court pour qu'une nomination se voie dans la seance, assez long pour ne pas
     * interroger Keycloak a chaque frappe dans le champ de recherche.
     */
    private static final Duration FRESHNESS = Duration.ofMinutes(5);

    /**
     * Apres un echec on reessaie bien plus tot : garder cinq minutes un annuaire qu'on sait
     * perime punirait tout le monde pour une secousse de quelques secondes. Assez long,
     * cependant, pour ne pas marteler un Keycloak deja en peine.
     */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(30);

    private final KeycloakRealmRoleGateway roleGateway;
    private final Duration freshness;
    private final Duration retryAfterFailure;

    private volatile Set<String> staff = Set.of();
    private volatile Set<String> teamStaff = Set.of();
    private volatile Set<String> students = Set.of();
    private volatile Set<String> studentLimited = Set.of();
    private volatile boolean known = false;
    private volatile long refreshedAtNanos = Long.MIN_VALUE;
    private volatile long validForNanos = 0L;

    @Autowired
    public RealmRoleDirectory(KeycloakRealmRoleGateway roleGateway) {
        this(roleGateway, FRESHNESS, RETRY_AFTER_FAILURE);
    }

    RealmRoleDirectory(KeycloakRealmRoleGateway roleGateway, Duration freshness, Duration retryAfterFailure) {
        this.roleGateway = roleGateway;
        this.freshness = freshness;
        this.retryAfterFailure = retryAfterFailure;
    }

    /** Les identifiants Keycloak de tout le staff, tous roles confondus. */
    public Set<String> staff() {
        ensureFresh();
        return staff;
    }

    /** Le staff compatible avec les equipes de cartes, comptes limites compris. */
    public Set<String> teamStaff() {
        ensureFresh();
        return teamStaff;
    }

    /** Les identifiants Keycloak des comptes portant le role etudiant. */
    public Set<String> students() {
        ensureFresh();
        return students;
    }

    /** Les identifiants Keycloak des comptes etudiants limites (4A). */
    public Set<String> studentLimited() {
        ensureFresh();
        return studentLimited;
    }

    /** Vrai si ce compte porte le role etudiant limite. */
    public boolean isStudentLimited(String keycloakSub) {
        return keycloakSub != null && studentLimited().contains(keycloakSub);
    }

    /** Invalide le cache de l'annuaire pour forcer un rechargement au prochain appel. */
    public synchronized void invalidateCache() {
        refreshedAtNanos = Long.MIN_VALUE;
        validForNanos = 0L;
    }

    /**
     * Vrai si Keycloak a repondu au moins une fois.
     *
     * <p>Sans cette distinction, un annuaire vide se lit « personne n'est etudiant » aussi
     * bien que « on n'a pas pu demander ». La premiere lecture viderait le classement, la
     * seconde doit seulement laisser les choses en l'etat -- d'ou ce drapeau, que
     * l'appelant consulte avant de refuser quelqu'un.</p>
     */
    public boolean isKnown() {
        ensureFresh();
        return known;
    }

    /** Vrai si ce compte porte au moins un role de staff. */
    public boolean isStaff(String keycloakSub) {
        return keycloakSub != null && staff().contains(keycloakSub);
    }

    private void ensureFresh() {
        long now = System.nanoTime();
        if (isFresh(now)) {
            return;
        }
        refresh(now);
    }

    private boolean isFresh(long now) {
        return refreshedAtNanos != Long.MIN_VALUE && now - refreshedAtNanos < validForNanos;
    }

    private synchronized void refresh(long now) {
        // Une seconde requete arrivee pendant l'appel precedent repart avec le resultat
        // frais plutot que de relancer quinze appels.
        if (isFresh(now)) {
            return;
        }

        Set<String> collectedStaff = new HashSet<>();
        Set<String> collectedLimited = new HashSet<>();
        Set<String> collectedStudents = new HashSet<>();
        List<String> unreadable = new ArrayList<>();

        Set<String> roles = new LinkedHashSet<>(SecurityConfig.STAFF_ROLE_NAMES);
        roles.add(STUDENT_LIMITED_ROLE);
        roles.add(STUDENT_ROLE);

        for (String role : roles) {
            Optional<List<String>> answer = roleGateway.listUsersInRealmRole(role);
            if (answer.isEmpty()) {
                unreadable.add(role);
                continue;
            }
            if (STUDENT_ROLE.equals(role)) {
                collectedStudents.addAll(answer.get());
            } else if (STUDENT_LIMITED_ROLE.equals(role)) {
                collectedLimited.addAll(answer.get());
            } else {
                collectedStaff.addAll(answer.get());
            }
        }

        // Un seul role illisible suffit a fausser les deux tris : ses membres passeraient
        // pour des joueurs d'un cote, et disparaitraient du classement de l'autre. On garde
        // alors l'annuaire precedent -- perime vaut mieux que faux -- et on reessaie bien
        // plus tot. Publier une liste amputee serait pire que ne pas la mettre a jour.
        if (!unreadable.isEmpty()) {
            LOGGER.warn("Annuaire des roles non mis a jour : {} illisible(s), on garde le precedent"
                    + " ({} staff, {} etudiants).", unreadable, staff.size(), students.size());
            refreshedAtNanos = now;
            validForNanos = retryAfterFailure.toNanos();
            return;
        }

        staff = Set.copyOf(collectedStaff);
        studentLimited = Set.copyOf(collectedLimited);
        collectedLimited.addAll(collectedStaff);
        teamStaff = Set.copyOf(collectedLimited);
        students = Set.copyOf(collectedStudents);
        known = true;
        refreshedAtNanos = now;
        validForNanos = freshness.toNanos();
        LOGGER.info("Annuaire des roles rafraichi : {} staff, {} etudiants.", staff.size(), students.size());
    }
}
