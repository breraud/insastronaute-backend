package fr.insastronaute.webapi.onboarding.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.auth.sessionreset.UserSessionRevocationService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.onboarding.dto.PendingOnboardingDto;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La file de validation d'accueil et son rattrapage de deploiement.
 *
 * <p>La regle qui ne plie pas : l'appel Keycloak d'abord, l'ecriture en base
 * ensuite, jamais l'inverse. Si Keycloak refuse, rien n'est ecrit et le compte
 * reste dans la file pour qu'un respo reessaie. L'ordre inverse laisserait la
 * base affirmer un acces que Keycloak n'a jamais accorde.</p>
 */
@Service
public class AdminOnboardingService {

    private static final String STUDENT_ROLE = "STUDENT";

    // Cadence a laquelle le rattrapage journalise sa progression : assez souvent
    // pour qu'un operateur qui regarde les logs pendant l'inte voie que ca
    // avance, assez rare pour ne pas noyer les logs sur plusieurs centaines
    // de comptes.
    private static final int PROGRESS_LOG_INTERVAL = 50;

    private static final Logger log = LoggerFactory.getLogger(AdminOnboardingService.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final KeycloakRealmRoleGateway roleGateway;
    private final AdminActionJournal journal;
    private final UserSessionRevocationService sessionRevocationService;
    private final TransactionTemplate perAccountTransaction;
    private final RealmRoleDirectory roleDirectory;

    public AdminOnboardingService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            KeycloakRealmRoleGateway roleGateway,
            AdminActionJournal journal,
            UserSessionRevocationService sessionRevocationService,
            PlatformTransactionManager transactionManager
    ) {
        this(
                userRepository,
                departmentRepository,
                roleGateway,
                journal,
                sessionRevocationService,
                transactionManager,
                null
        );
    }

    @Autowired
    public AdminOnboardingService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            KeycloakRealmRoleGateway roleGateway,
            AdminActionJournal journal,
            UserSessionRevocationService sessionRevocationService,
            PlatformTransactionManager transactionManager,
            RealmRoleDirectory roleDirectory
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleGateway = roleGateway;
        this.journal = journal;
        this.sessionRevocationService = sessionRevocationService;
        this.roleDirectory = roleDirectory;
        // Une transaction neuve par compte (cf. backfill()), pas une seule qui
        // engloberait tout le balayage : voir le commentaire de backfill().
        this.perAccountTransaction = new TransactionTemplate(transactionManager);
        this.perAccountTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<PendingOnboardingDto> pending() {
        return userRepository.findByOnboardingStatusOrderByOnboardingSubmittedAtAsc(OnboardingStatus.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // Le pendant de la file d'attente : le recours humain promis a un compte
    // refuse (spec 2.4, "il est humain plutot que logiciel") n'existe que si un
    // respo peut le retrouver quelque part dans le panel. Meme DTO que /pending,
    // meme groupe de roles ; seul l'ordre change, par date de decision.
    @Transactional(readOnly = true)
    public List<PendingOnboardingDto> rejected() {
        return userRepository.findByOnboardingStatusOrderByOnboardingDecidedAtDesc(OnboardingStatus.REJECTED)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void validate(UUID userId, AppUser decider) {
        AppUser target = require(userId);
        OnboardingStatus previous = target.getOnboardingStatus();
        // Keycloak d'abord : si l'assignation echoue, rien n'est ecrit et le compte
        // reste dans la file, ou un respo reessaiera. L'inverse laisserait la base
        // affirmer un acces que Keycloak n'a pas accorde.
        roleGateway.assignRealmRole(target.getKeycloakSub(), STUDENT_ROLE);
        roleGateway.removeRealmRole(target.getKeycloakSub(), RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        target.setOnboardingStatus(OnboardingStatus.VALIDATED);
        target.setOnboardingRejectionReason(null);
        target.setOnboardingDecidedAt(TimeTravelContext.instantNow());
        target.setOnboardingDecidedBy(decider);
        userRepository.save(target);
        if (roleDirectory != null) {
            roleDirectory.invalidateCache();
        }
        sessionRevocationService.revokeAll(target.getKeycloakSub());
        recordDecision(decider, target, AdminActionType.ONBOARDING_VALIDATED, previous, null);
    }

    @Transactional
    public void validateLimited(UUID userId, AppUser decider) {
        AppUser target = require(userId);
        OnboardingStatus previous = target.getOnboardingStatus();
        roleGateway.assignRealmRole(target.getKeycloakSub(), RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        roleGateway.removeRealmRole(target.getKeycloakSub(), STUDENT_ROLE);
        target.setHiddenFromLeaderboards(true);
        target.setOnboardingStatus(OnboardingStatus.VALIDATED);
        target.setOnboardingRejectionReason(null);
        target.setOnboardingDecidedAt(TimeTravelContext.instantNow());
        target.setOnboardingDecidedBy(decider);
        userRepository.save(target);
        if (roleDirectory != null) {
            roleDirectory.invalidateCache();
        }
        sessionRevocationService.revokeAll(target.getKeycloakSub());
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("previousStatus", previous == null ? null : previous.name());
        details.put("role", RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        journal.record(decider.getKeycloakSub(), target.getId(), AdminActionType.ONBOARDING_VALIDATED, details);
    }

    @Transactional
    public void reject(UUID userId, AppUser decider, String rawReason) {
        String reason = requireReason(rawReason);
        AppUser target = require(userId);
        if (target.getOnboardingStatus() == OnboardingStatus.VALIDATED) {
            throw new CodedConflictException(
                    "ONBOARDING_CANCELLATION_REQUIRED",
                    "Une inscription deja validee doit etre annulee avec l'action reservee aux respos defis."
            );
        }
        rejectTarget(target, decider, reason);
    }

    @Transactional
    public void cancel(UUID userId, AppUser decider, String rawReason) {
        String reason = requireReason(rawReason);
        AppUser target = require(userId);
        if (target.getOnboardingStatus() != OnboardingStatus.VALIDATED) {
            throw new CodedConflictException(
                    "ONBOARDING_NOT_VALIDATED",
                    "Seule une inscription validee peut etre annulee."
            );
        }
        rejectTarget(target, decider, reason);
    }

    private String requireReason(String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isEmpty()) {
            throw new CodedBadRequestException("ONBOARDING_REASON_REQUIRED", "Un motif est requis.");
        }
        return reason;
    }

    private void rejectTarget(AppUser target, AppUser decider, String reason) {
        // Lors d'une annulation, le role et toutes les sessions sont retires avant
        // d'ecrire la decision. Le prochain appel repasse ainsi par Keycloak.
        OnboardingStatus previous = target.getOnboardingStatus();
        roleGateway.removeRealmRole(target.getKeycloakSub(), STUDENT_ROLE);
        roleGateway.removeRealmRole(target.getKeycloakSub(), RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        roleGateway.logoutUser(target.getKeycloakSub());
        sessionRevocationService.revokeAll(target.getKeycloakSub());
        if (roleDirectory != null) {
            roleDirectory.invalidateCache();
        }
        target.setOnboardingStatus(OnboardingStatus.REJECTED);
        target.setOnboardingRejectionReason(reason);
        target.setOnboardingDecidedAt(TimeTravelContext.instantNow());
        target.setOnboardingDecidedBy(decider);
        userRepository.save(target);
        recordDecision(decider, target, AdminActionType.ONBOARDING_REJECTED, previous, reason);
    }

    /**
     * Porte la decision au registre, apres Keycloak et apres l'ecriture.
     *
     * <p>Ici et pas avant : la ligne affirme un acces accorde ou repris, et l'ecrire
     * pendant que Keycloak peut encore refuser en ferait une affirmation fausse. La fiche
     * du joueur ne garde que la derniere decision — un compte valide, refuse, puis
     * revalide n'y laisse qu'une ligne. Le registre, lui, garde la suite.</p>
     *
     * <p>L'etat precedent voyage dans les details : c'est lui qui distingue une premiere
     * admission d'un retour en arriere.</p>
     */
    private void recordDecision(
            AppUser decider,
            AppUser target,
            AdminActionType action,
            OnboardingStatus previousStatus,
            String reason
    ) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("previousStatus", previousStatus == null ? null : previousStatus.name());
        if (reason != null) {
            details.put("reason", reason);
        }
        journal.record(decider.getKeycloakSub(), target.getId(), action, details);
    }

    /**
     * Rattrapage de deploiement : au premier lancement de la porte, aucun compte
     * ne porte encore STUDENT. Sans ce passage, chaque compte existant serait
     * bloque en meme temps, en plein inte, et devrait etre valide un par un.
     *
     * <p>Ne touche que les comptes NOT_STARTED : ce sont eux que le rattrapage
     * existe pour sauver, car ils n'ont jamais rien soumis. Un compte REJECTED
     * porte une decision humaine deliberee qu'un traitement en masse ne doit
     * jamais annuler ; un compte PENDING est une soumission legitime arrivee
     * apres la mise en place de la porte, qui attend encore l'examen d'un
     * respo, et le valider en masse court-circuiterait cet examen. C'est cette
     * frontiere plus etroite, et non "tout ce qui n'est pas VALIDATED", qui
     * fait que ce traitement est sans danger si on le rejoue : sur des comptes
     * deja NOT_STARTED, il ne fait que reassigner un role que Keycloak porte
     * deja, sans effet de son cote ; il ne touche jamais un compte deja
     * tranche par un humain ou encore en attente d'un.</p>
     *
     * <p><strong>Une transaction par compte, pas une seule pour tout le
     * balayage.</strong> Charger tous les utilisateurs et faire un appel
     * Keycloak synchrone par compte a l'interieur d'une transaction unique
     * depasse a quelques centaines de comptes le timeout d'origine d'environ
     * 100 s de Cloudflare (voir le rattrapage en 7.2 de la spec, qui recommande
     * d'ailleurs de lancer cet appel contre l'origine plutot qu'a travers
     * Cloudflare) et garde une connexion poolee prise pendant toute la duree.
     * L'operation etant deja idempotente (reassigner un role deja present est
     * sans effet chez Keycloak), un timeout cote client qui coupe la requete
     * en cours de route laisse une progression partielle qu'une relance
     * termine, plutot qu'une longue transaction qu'un timeout abandonnerait
     * dans un etat ambigu.</p>
     */
    public int backfill() {
        List<AppUser> notStarted = userRepository.findAll().stream()
                .filter(user -> user.getOnboardingStatus() == OnboardingStatus.NOT_STARTED)
                .toList();

        int processed = 0;
        for (AppUser user : notStarted) {
            try {
                perAccountTransaction.executeWithoutResult(status -> processOneBackfillAccount(user));
            } catch (RuntimeException e) {
                // Un compte durablement casse - un keycloakSub que Keycloak ne connait
                // pas, par exemple - arrete la campagne au meme endroit a chaque
                // relance : les precedents sont deja VALIDATED donc ecartes du filtre,
                // et celle-ci vient remourir sur lui. Sans ce message l'operateur ne
                // recoit qu'un 500 nu et doit relire la base pour savoir ou ca coince.
                log.error("Rattrapage d'accueil interrompu sur le compte {} apres {}/{} comptes commis : {}",
                        user.getId(), processed, notStarted.size(), e.getMessage());
                throw e;
            }
            processed++;
            if (processed % PROGRESS_LOG_INTERVAL == 0 || processed == notStarted.size()) {
                log.info("Rattrapage d'accueil : {}/{} comptes traites", processed, notStarted.size());
            }
        }

        return processed;
    }

    private void processOneBackfillAccount(AppUser user) {
        roleGateway.assignRealmRole(user.getKeycloakSub(), STUDENT_ROLE);
        user.setOnboardingStatus(OnboardingStatus.VALIDATED);
        userRepository.save(user);
    }

    private AppUser require(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable."));
    }

    private PendingOnboardingDto toDto(AppUser user) {
        return new PendingOnboardingDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                departmentName(user),
                user.getGender(),
                user.getOnboardingSubmittedAt(),
                user.getOnboardingRejectionReason()
        );
    }

    private String departmentName(AppUser user) {
        if (user.getDepartment() == null) {
            return null;
        }
        return departmentRepository.findById(user.getDepartment())
                .map(Department::getShortName)
                .orElse(null);
    }
}
