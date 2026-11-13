package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakGroupMembershipGateway;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Relit le departement d'un joueur dans Keycloak, sans attendre sa prochaine connexion.
 *
 * <p>Le departement est ecrit en base a la connexion, depuis le jeton : deplacer quelqu'un
 * de groupe dans Keycloak ne se voit donc nulle part tant qu'il ne revient pas. Ce service
 * demande ses groupes a Keycloak directement — la passerelle sait le faire — et ecrit ce
 * qu'elle repond.</p>
 *
 * <p>La correspondance est celle de la connexion, et pas une autre : le nom du groupe, sans
 * distinction de casse, contre {@code keycloak_group_name}. Deux regles differentes
 * finiraient par ranger le meme joueur dans deux departements selon le chemin emprunte.</p>
 */
@Service
public class DepartmentResyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepartmentResyncService.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final KeycloakGroupMembershipGateway gateway;
    private final TransactionTemplate perUserTransaction;

    public DepartmentResyncService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            KeycloakGroupMembershipGateway gateway,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.gateway = gateway;
        // Une transaction par compte : un balayage de deux mille joueurs qui echouerait sur
        // le dernier ne doit pas defaire les mille neuf cent quatre-vingt-dix-neuf premiers.
        this.perUserTransaction = new TransactionTemplate(transactionManager);
        this.perUserTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /** Ce qu'une relecture a change pour un joueur. */
    public record Outcome(UUID userId, String previousDepartment, String currentDepartment, boolean changed) {
    }

    /** Ce qu'un balayage complet a trouve. */
    public record SweepReport(int examined, int changed, int unreadable) {
    }

    @Transactional
    public Outcome resync(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."));
        return apply(user);
    }

    /**
     * Relit tout le monde.
     *
     * <p>Un appel Keycloak par compte : c'est lent, et c'est pourquoi ce balayage se
     * declenche a la main ou une fois par nuit, jamais sur le chemin d'une requete
     * ordinaire.</p>
     *
     * <p>Un compte dont Keycloak refuse de repondre est compte a part et laisse tel quel :
     * ecrire « aucun departement » sur une panne de lecture effacerait une information
     * juste au motif qu'on n'a pas su la relire.</p>
     */
    public SweepReport resyncAll() {
        int examined = 0;
        int changed = 0;
        int unreadable = 0;

        for (UUID userId : userRepository.findAll().stream().map(AppUser::getId).toList()) {
            examined++;
            try {
                Outcome outcome = perUserTransaction.execute(status -> resync(userId));
                if (outcome != null && outcome.changed()) {
                    changed++;
                }
            } catch (RuntimeException exception) {
                unreadable++;
                LOGGER.warn("Departement non relu pour le joueur {} : {}", userId, exception.getMessage());
            }
        }

        LOGGER.info("Relecture des departements : {} comptes lus, {} corriges, {} illisibles.",
                examined, changed, unreadable);
        return new SweepReport(examined, changed, unreadable);
    }

    private Outcome apply(AppUser user) {
        String previous = departmentName(user.getDepartment());
        String keycloakSub = user.getKeycloakSub();
        if (keycloakSub == null || keycloakSub.isBlank()) {
            // Aucun compte d'authentification : rien a relire, et rien a effacer non plus.
            return new Outcome(user.getId(), previous, previous, false);
        }

        /*
         * L'attribut d'abord, les groupes en repli — exactement l'ordre de la connexion.
         *
         * Les deux divergent en pratique : la synchronisation qui pousse l'attribut vers
         * les groupes peut etre desactivee, echouer, ou viser un groupe absent. Ne lire que
         * les groupes effacait le departement de tous ceux dont il ne tenait qu'a
         * l'attribut — c'est-a-dire presque tout le monde.
         */
        List<String> attributeValues = gateway.listUserDepartmentAttribute(keycloakSub);
        List<String> groupNames = attributeValues.isEmpty()
                ? gateway.listUserGroups(keycloakSub).stream()
                        .map(KeycloakGroupMembershipGateway.Group::name)
                        .toList()
                : List.of();
        UUID matched = attributeValues.isEmpty()
                ? matchDepartment(groupNames)
                : matchDepartment(attributeValues);

        /*
         * Un compte dont Keycloak ne dit rien garde ce qu'il porte.
         *
         * Effacer sur une lecture muette est ce qui a vide les departements la premiere
         * fois. La connexion, elle, peut se le permettre : elle lit un jeton qui decrit
         * l'utilisateur en entier. Ici on interroge deux endpoints, et leur silence commun
         * ne prouve pas une absence.
         *
         * Une reponse qui ne correspond a rien, en revanche, en est une : un joueur que
         * Keycloak dit « BUREAU » n'a pas de departement, et on l'ecrit.
         */
        if (matched == null && attributeValues.isEmpty() && groupNames.isEmpty()) {
            return new Outcome(user.getId(), previous, previous, false);
        }

        boolean changed = !Objects.equals(user.getDepartment(), matched);
        if (changed) {
            user.setDepartment(matched);
            userRepository.save(user);
        }
        return new Outcome(user.getId(), previous, departmentName(matched), changed);
    }

    /** Le premier candidat que la base reconnait, ou null. Meme regle qu'a la connexion. */
    private UUID matchDepartment(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Optional<Department> department =
                    departmentRepository.findByKeycloakGroupNameIgnoreCase(candidate.trim());
            if (department.isPresent()) {
                return department.get().getId();
            }
        }
        return null;
    }

    private String departmentName(UUID departmentId) {
        return departmentId == null
                ? null
                : departmentRepository.findById(departmentId).map(Department::getShortName).orElse(null);
    }
}
