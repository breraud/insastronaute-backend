package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.AdminCardInstanceDto;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionChallengeDto;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.dto.AdminUserCardCollectionDto;
import fr.insastronaute.webapi.user.dto.AdminUserDetailDto;
import fr.insastronaute.webapi.user.dto.AdminUserDto;
import fr.insastronaute.webapi.user.dto.AdminUserFriendshipDto;
import fr.insastronaute.webapi.user.dto.AdminUserStatsDto;
import fr.insastronaute.webapi.user.dto.AdminUserSubmissionDto;
import fr.insastronaute.webapi.user.dto.AdminManualChallengeSubmissionRequest;
import fr.insastronaute.webapi.user.dto.AdminRejectChallengeSubmissionRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardChangeRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardRemovalRequest;
import fr.insastronaute.webapi.auth.keycloak.KeycloakAccountGateway;
import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.auth.sessionreset.UserSessionRevocationService;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.user.dto.AdminPlayerDeletionDto;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUserService {

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    /**
     * Ce que la Mediatheque retient d'un compte. Par setter, comme le registre de la
     * monnaie : les trois constructeurs sont appeles en dur par des tests qui n'en ont
     * rien a dire. Spring, lui, l'injecte toujours.
     */
    private fr.insastronaute.webapi.medialibrary.service.MediaLibraryUserReferences mediaLibraryReferences;

    @org.springframework.beans.factory.annotation.Autowired
    void setMediaLibraryReferences(fr.insastronaute.webapi.medialibrary.service.MediaLibraryUserReferences mediaLibraryReferences) {
        this.mediaLibraryReferences = mediaLibraryReferences;
    }

    /**
     * Supprimer un joueur.
     *
     * <p>L'ordre n'est pas indifferent. Le compte Keycloak part en premier, avant toute
     * ecriture : si la suite echoue, l'administrateur relance et le compte deja absent
     * n'arrete rien. L'inverse laisserait un joueur marque supprime mais toujours capable
     * de se connecter.</p>
     *
     * <p>Deux issues ensuite. Si rien ne retient sa ligne, elle disparait vraiment et les
     * cascades emportent avec elle cartes, progression et soumissions. Si le registre
     * d'administration le designe -- comme cible d'une moderation, ou comme auteur s'il
     * fut administrateur -- la ligne survit avec son nom : ces references sont en
     * {@code ON DELETE RESTRICT} parce qu'une trace qui disparait avec son sujet ne trace
     * rien. Le joueur est alors marque supprime, ce qui suffit a le fermer partout,
     * puisque la synchronisation refuse desormais de le laisser entrer.</p>
     */
    @Transactional
    public AdminPlayerDeletionDto deletePlayer(String actorKeycloakSub, UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        if (user.isDeleted()) {
            throw new ConflictException("Ce joueur est deja supprime.");
        }
        if (user.getKeycloakSub() != null && user.getKeycloakSub().equals(actorKeycloakSub)) {
            throw new BadRequestException("Vous ne pouvez pas supprimer votre propre compte.");
        }

        String displayName = user.getDisplayName();
        String email = user.getEmail();

        boolean accountWasMissing = deleteAuthenticationAccount(user);
        detachFromOtherPlayers(userId);
        if (sessionRevocationService != null && user.getKeycloakSub() != null && !user.getKeycloakSub().isBlank()) {
            sessionRevocationService.revokeAll(user.getKeycloakSub());
        }

        // Ecrit avant l'issue, et sans cible : une cible en RESTRICT empecherait la
        // suppression que cette ligne enregistre.
        journal.recordGlobal(actorKeycloakSub, AdminActionType.PLAYER_DELETED, Map.of(
                "userId", userId.toString(),
                "displayName", displayName == null ? "" : displayName,
                "email", email == null ? "" : email,
                "keycloakAccountWasMissing", accountWasMissing
        ));

        String retainedBecause = whatHoldsTheRow(userId);
        if (retainedBecause == null) {
            // La ligne part pour de bon, et la base cascade jusqu'a ses soumissions
            // puis a leur audit. Estampiller avant : apres, il n'y a plus rien a
            // nommer. Seules ses propres soumissions partent -- celles ou il n'etait
            // que participant survivent sans lui.
            if (moderationAuditService != null) {
                moderationAuditService.markSubmissionsDeleted(
                        submissionRepository.findIdsByUserId(userId), now());
            }
            userRepository.delete(user);
            return new AdminPlayerDeletionDto(displayName, true, accountWasMissing, null);
        }

        user.setDeletedAt(now());
        // Le classement sait deja exclure sur ce drapeau : le reutiliser evite d'ajouter
        // un second predicat a chacune de ses requetes.
        user.setHiddenFromLeaderboards(true);
        userRepository.save(user);
        return new AdminPlayerDeletionDto(displayName, false, accountWasMissing, retainedBecause);
    }

    /** @return vrai si le compte n'existait deja plus cote Keycloak. */
    private boolean deleteAuthenticationAccount(AppUser user) {
        String sub = user.getKeycloakSub();
        if (sub == null || sub.isBlank()) {
            // Une ligne sans identifiant Keycloak n'a aucun compte a fermer.
            return true;
        }
        return accountGateway.deleteAccount(sub);
    }

    /**
     * Retirer ce joueur de l'etat de jeu des autres.
     *
     * <p>Ce qui lui appartient en propre part avec sa ligne, ou reste attache a elle. Ce
     * qui se voit chez d'autres joueurs -- son equipe, ses amities -- doit partir de toute
     * facon : un membre fantome dans une equipe est un bug pour les cinq autres.</p>
     */
    private void detachFromOtherPlayers(UUID userId) {
        // Et surtout pas en attrapant le NotFoundException de removeMember : cette
        // methode est transactionnelle, donc Spring marque la transaction rollback-only
        // avant de relancer. L'exception avalee, le commit echouait quand meme, en
        // UnexpectedRollbackException -- loin de la cause, et sur un geste deja fait.
        adminCardTeamService.removeMemberIfPresent(userId);
        friendshipRepository.deleteAll(friendshipRepository.findAllInvolving(userId));
    }

    /**
     * Ce qui empeche la ligne de disparaitre, ou {@code null} si rien ne l'empeche.
     *
     * <p>La question se pose a l'avance plutot qu'en laissant la base refuser : une
     * violation de contrainte empoisonne la transaction, et on ne pourrait plus y ecrire
     * le repli.</p>
     */
    private String whatHoldsTheRow(UUID userId) {
        if (adminActionEventRepository.isReferenced(userId)) {
            return "Le registre d'administration nomme ce joueur.";
        }
        if (cardTeamRepository.existsByCreatorId(userId)) {
            return "Une equipe de cartes qu'il a fondee existe encore.";
        }
        if (mediaLibraryReferences != null && mediaLibraryReferences.isReferenced(userId)) {
            return "La Médiathèque le nomme : accès accordé, média retenu ou export.";
        }
        return null;
    }

    private static java.time.Instant now() {
        return fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    private static final String STATUS_VALIDATED = "VALIDATED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String PROOF_TYPE_TEXT = "TEXT";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final FriendshipRepository friendshipRepository;
    private final CardCollectionRepository cardCollectionRepository;
    private final CardInstanceService cardInstanceService;
    private final CardService cardService;
    private final ProofStorageService proofStorageService;
    private final ChallengeService challengeService;
    private final AdminCardTeamService adminCardTeamService;
    private final AdminActionJournal journal;
    private final KeycloakAccountGateway accountGateway;
    private final AdminActionEventRepository adminActionEventRepository;
    private final CardTeamRepository cardTeamRepository;
    private final KeycloakRealmRoleGateway roleGateway;
    private final RealmRoleDirectory roleDirectory;
    private final UserSessionRevocationService sessionRevocationService;
    /**
     * L'audit de moderation, a estampiller avant une suppression definitive.
     *
     * <p>Injecte par champ, comme ailleurs dans ce projet : les trois constructeurs de
     * ce service sont appeles en dur par des tests qui n'ont rien a dire de l'audit, et
     * une trace non estampillee reste une trace.</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.insastronaute.webapi.challenge.audit.ModerationAuditService moderationAuditService;

    public AdminUserService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            ChallengeSubmissionRepository submissionRepository,
            FriendshipRepository friendshipRepository,
            CardCollectionRepository cardCollectionRepository,
            CardInstanceService cardInstanceService,
            CardService cardService,
            ProofStorageService proofStorageService,
            ChallengeService challengeService,
            AdminCardTeamService adminCardTeamService,
            AdminActionJournal journal,
            KeycloakAccountGateway accountGateway,
            AdminActionEventRepository adminActionEventRepository,
            CardTeamRepository cardTeamRepository
    ) {
        this(
                userRepository,
                departmentRepository,
                submissionRepository,
                friendshipRepository,
                cardCollectionRepository,
                cardInstanceService,
                cardService,
                proofStorageService,
                challengeService,
                adminCardTeamService,
                journal,
                accountGateway,
                adminActionEventRepository,
                cardTeamRepository,
                null,
                null,
                null
        );
    }

    public AdminUserService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            ChallengeSubmissionRepository submissionRepository,
            FriendshipRepository friendshipRepository,
            CardCollectionRepository cardCollectionRepository,
            CardInstanceService cardInstanceService,
            CardService cardService,
            ProofStorageService proofStorageService,
            ChallengeService challengeService,
            AdminCardTeamService adminCardTeamService,
            AdminActionJournal journal,
            KeycloakAccountGateway accountGateway,
            AdminActionEventRepository adminActionEventRepository,
            CardTeamRepository cardTeamRepository,
            KeycloakRealmRoleGateway roleGateway,
            RealmRoleDirectory roleDirectory
    ) {
        this(
                userRepository,
                departmentRepository,
                submissionRepository,
                friendshipRepository,
                cardCollectionRepository,
                cardInstanceService,
                cardService,
                proofStorageService,
                challengeService,
                adminCardTeamService,
                journal,
                accountGateway,
                adminActionEventRepository,
                cardTeamRepository,
                roleGateway,
                roleDirectory,
                null
        );
    }

    @Autowired
    public AdminUserService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            ChallengeSubmissionRepository submissionRepository,
            FriendshipRepository friendshipRepository,
            CardCollectionRepository cardCollectionRepository,
            CardInstanceService cardInstanceService,
            CardService cardService,
            ProofStorageService proofStorageService,
            ChallengeService challengeService,
            AdminCardTeamService adminCardTeamService,
            AdminActionJournal journal,
            KeycloakAccountGateway accountGateway,
            AdminActionEventRepository adminActionEventRepository,
            CardTeamRepository cardTeamRepository,
            KeycloakRealmRoleGateway roleGateway,
            RealmRoleDirectory roleDirectory,
            UserSessionRevocationService sessionRevocationService
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.submissionRepository = submissionRepository;
        this.friendshipRepository = friendshipRepository;
        this.cardCollectionRepository = cardCollectionRepository;
        this.cardInstanceService = cardInstanceService;
        this.cardService = cardService;
        this.proofStorageService = proofStorageService;
        this.challengeService = challengeService;
        this.adminCardTeamService = adminCardTeamService;
        this.journal = journal;
        this.accountGateway = accountGateway;
        this.adminActionEventRepository = adminActionEventRepository;
        this.cardTeamRepository = cardTeamRepository;
        this.roleGateway = roleGateway;
        this.roleDirectory = roleDirectory;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Transactional(readOnly = true)
    public AdminUserDetailDto getUserDetail(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        List<ChallengeSubmission> submissions = submissionRepository
                .findDetailedByUserIdOrParticipantUserIdOrderByCreatedAtDesc(userId);
        List<Friendship> friendships = friendshipRepository.findByUserIdForAdmin(userId);
        List<CardCollection> collections = cardCollectionRepository.findByUserIdForAdmin(userId);
        List<AdminCardInstanceDto> physicalCardInstances = cardInstanceService.searchAdmin(null, null, userId, null, null);

        Boolean staff = roleDirectory != null && user.getKeycloakSub() != null ? roleDirectory.isStaff(user.getKeycloakSub()) : null;
        Boolean studentLimited = roleDirectory != null && user.getKeycloakSub() != null ? roleDirectory.isStudentLimited(user.getKeycloakSub()) : null;

        return new AdminUserDetailDto(
                AdminUserDto.from(user, departmentName(user), staff, studentLimited),
                stats(submissions, friendships, collections, physicalCardInstances),
                submissions.stream().map(submission -> toSubmissionDto(user, submission)).toList(),
                friendships.stream().map(friendship -> toFriendshipDto(user, friendship)).toList(),
                collections.stream().map(this::toCardCollectionDto).toList(),
                physicalCardInstances,
                adminCardTeamService.findForUser(userId)
        );
    }

    @Transactional(readOnly = true)
    public List<PlayerChallengeDto> getAvailableChallenges(UUID userId) {
        return challengeService.getPlayerChallenges(userId);
    }

    @Transactional
    public AdminUserDto updateStudentLimited(UUID userId, boolean studentLimited, String actorKeycloakSub) {
        AppUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        if (user.getKeycloakSub() == null || user.getKeycloakSub().isBlank()) {
            throw new BadRequestException("Ce compte ne possède pas d'identifiant d'authentification.");
        }

        if (studentLimited) {
            roleGateway.assignRealmRole(user.getKeycloakSub(), RealmRoleDirectory.STUDENT_LIMITED_ROLE);
            roleGateway.removeRealmRole(user.getKeycloakSub(), RealmRoleDirectory.STUDENT_ROLE);
            user.setHiddenFromLeaderboards(true);
            if (user.getOnboardingStatus() == OnboardingStatus.REJECTED) {
                user.setOnboardingStatus(OnboardingStatus.VALIDATED);
                user.setOnboardingRejectionReason(null);
                user.setOnboardingDecidedAt(now());
                userRepository.findByKeycloakSub(actorKeycloakSub).ifPresent(user::setOnboardingDecidedBy);
            }
        } else {
            roleGateway.assignRealmRole(user.getKeycloakSub(), RealmRoleDirectory.STUDENT_ROLE);
            roleGateway.removeRealmRole(user.getKeycloakSub(), RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        }

        userRepository.save(user);
        if (roleDirectory != null) {
            roleDirectory.invalidateCache();
        }
        if (sessionRevocationService != null) {
            sessionRevocationService.revokeAll(user.getKeycloakSub());
        }

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("studentLimited", studentLimited);
        journal.record(actorKeycloakSub, user.getId(), AdminActionType.ONBOARDING_VALIDATED, details);

        Boolean staff = roleDirectory != null ? roleDirectory.isStaff(user.getKeycloakSub()) : null;

        return AdminUserDto.from(
                user,
                departmentName(user),
                staff,
                studentLimited
        );
    }

    @Transactional
    public AdminUserDto updateTokenBalance(UUID userId, long tokensDelta, String actorKeycloakSub) {
        if (tokensDelta == 0) {
            throw new BadRequestException("La variation de tokens ne peut pas être nulle.",
                    "tokensDelta", "La variation doit être différente de zéro.");
        }

        AppUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        long updatedTokens;
        try {
            updatedTokens = Math.addExact(user.getTokens(), tokensDelta);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("La variation de tokens est invalide.");
        }
        if (updatedTokens < 0) {
            throw new BadRequestException("Le solde de tokens ne peut pas devenir négatif.",
                    "tokensDelta", "Le retrait dépasse le solde disponible.");
        }

        long adjustment = updatedTokens - user.getTokens();
        user.setTokens(updatedTokens);
        // Une correction corrige les deux monnaies : un gain accorde par erreur puis
        // retire ne doit pas laisser ses PE derriere lui.
        long adjustedEventPoints = user.creditEventPointsFor(fr.insastronaute.webapi.economy.domain.CurrencySource.ADMIN_ADJUSTMENT, adjustment);
        currencyLedger.record(user, fr.insastronaute.webapi.economy.domain.CurrencySource.ADMIN_ADJUSTMENT, adjustment, adjustedEventPoints, null);
        userRepository.save(user);

        journal.record(actorKeycloakSub, userId,
                tokensDelta > 0 ? AdminActionType.TOKENS_GRANTED : AdminActionType.TOKENS_REVOKED,
                Map.of("delta", tokensDelta, "balance", updatedTokens));

        return AdminUserDto.from(user, departmentName(user));
    }

    public void createManualChallengeSubmission(
            UUID userId,
            AdminManualChallengeSubmissionRequest request,
            String actorKeycloakSub
    ) {
        challengeService.createApprovedManualSubmission(
                userId, request.challengeId(), request.comment(), request.bonusPoints(), actorKeycloakSub);
    }

    public void rejectChallengeSubmission(
            UUID userId,
            UUID submissionId,
            AdminRejectChallengeSubmissionRequest request,
            String actorKeycloakSub
    ) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        challengeService.revokeValidatedSubmission(
                user.getId(),
                submissionId,
                request != null ? request.comment() : null,
                actorKeycloakSub
        );
    }

    /**
     * Donne des cartes depuis la fiche joueur.
     *
     * <p>Transactionnel pour que le geste et sa ligne de registre tiennent ou
     * tombent ensemble : sans cela le service de cartes validerait sa propre
     * transaction et chaque journal.record en ouvrirait une autre.</p>
     *
     * <p>Seules les instances reellement attribuees sont journalisees : une
     * selection re-soumise peut contenir des cartes que le joueur detient
     * deja, et le registre est en ajout seul - une ligne ecrite a tort n'est
     * corrigeable par personne.</p>
     */
    @Transactional
    public void addCards(UUID userId, AdminUserCardChangeRequest request, String actorKeycloakSub) {
        if (request.collectionType() == CardCollectionType.VIRTUAL) {
            UUID cardId = requiredCardId(request.cardId());
            int quantity = quantityOrOne(request.quantity());
            String cardName = cardService.addVirtualCards(userId, cardId, quantity);
            journal.record(actorKeycloakSub, userId, AdminActionType.VIRTUAL_CARDS_GRANTED,
                    Map.of("cardId", cardId.toString(), "cardName", cardName, "quantity", quantity));
            return;
        }
        cardInstanceService.assignAvailableToUser(userId, request.cardInstanceIds())
                .forEach(moved -> journal.record(
                        actorKeycloakSub, userId, AdminActionType.PHYSICAL_CARD_ASSIGNED,
                        Map.of("instanceId", moved.instanceId().toString(), "cardName", moved.cardName())));
    }

    /** Reprend des cartes depuis la fiche joueur (voir addCards pour la transaction). */
    @Transactional
    public void removeCards(UUID userId, AdminUserCardRemovalRequest request, String actorKeycloakSub) {
        if (request.collectionType() == CardCollectionType.VIRTUAL) {
            UUID cardId = requiredCardId(request.cardId());
            int quantity = quantityOrOne(request.quantity());
            String cardName = cardService.removeVirtualCards(userId, cardId, quantity);
            journal.record(actorKeycloakSub, userId, AdminActionType.VIRTUAL_CARDS_REVOKED,
                    Map.of("cardId", cardId.toString(), "cardName", cardName, "quantity", quantity));
            return;
        }
        cardInstanceService.releaseOwnedByUser(userId, request.cardInstanceIds())
                .forEach(moved -> journal.record(
                        actorKeycloakSub, userId, AdminActionType.PHYSICAL_CARD_RELEASED,
                        Map.of("instanceId", moved.instanceId().toString(), "cardName", moved.cardName())));
    }

    private UUID requiredCardId(UUID cardId) {
        if (cardId == null) {
            throw new BadRequestException("L'identifiant de la carte est requis.");
        }
        return cardId;
    }

    private int quantityOrOne(Integer quantity) {
        return quantity == null ? 1 : quantity;
    }

    private String departmentName(AppUser user) {
        if (user.getDepartment() == null) {
            return null;
        }
        return departmentRepository.findById(user.getDepartment())
                .map(Department::getShortName)
                .orElse(null);
    }

    private AdminUserStatsDto stats(
            List<ChallengeSubmission> submissions,
            List<Friendship> friendships,
            List<CardCollection> collections,
            List<AdminCardInstanceDto> physicalCardInstances
    ) {
        return new AdminUserStatsDto(
                submissions.size(),
                countSubmissions(submissions, STATUS_PENDING),
                countSubmissions(submissions, STATUS_WAITING_CONFIRMATION),
                countSubmissions(submissions, STATUS_VALIDATED),
                countSubmissions(submissions, STATUS_REJECTED),
                friendships.size(),
                friendships.stream().filter(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED).count(),
                friendships.stream().filter(friendship -> friendship.getStatus() == FriendshipStatus.PENDING).count(),
                countCards(collections, CardCollectionType.VIRTUAL),
                countCards(collections, CardCollectionType.PHYSICAL),
                physicalCardInstances.size()
        );
    }

    private long countSubmissions(List<ChallengeSubmission> submissions, String status) {
        return submissions.stream()
                .filter(submission -> status.equals(submission.getStatus()))
                .count();
    }

    private long countCards(List<CardCollection> collections, CardCollectionType collectionType) {
        return collections.stream()
                .filter(collection -> collection.getCollectionType() == collectionType)
                .mapToLong(collection -> collection.getQuantity() == null ? 0 : collection.getQuantity())
                .sum();
    }

    private AdminUserSubmissionDto toSubmissionDto(AppUser targetUser, ChallengeSubmission submission) {
        AppUser submitter = submission.getUser();
        return new AdminUserSubmissionDto(
                submission.getId(),
                submission.getStatus(),
                submission.getCreatedAt(),
                submission.getValidatedAt(),
                submitter.getId(),
                displayName(submitter),
                submitter.getEmail(),
                !submitter.getId().equals(targetUser.getId()),
                submission.getProofType(),
                PROOF_TYPE_TEXT.equals(submission.getProofType()) ? submission.getProofData() : null,
                proofMedia(submission),
                submission.getComment(),
                submission.getModerationComment(),
                submission.getBonusPoints(),
                Boolean.TRUE.equals(submission.getMessagesEnabled()),
                toChallengeDto(submission.getChallenge()),
                submission.getParticipants().stream()
                        .sorted(Comparator.comparing(this::displayName, Comparator.nullsLast(String::compareToIgnoreCase)))
                        .map(participant -> new SubmissionParticipantDto(
                                participant.getId(),
                                displayName(participant),
                                participant.getEmail(),
                                null,
                                null,
                                null,
                                false,
                                false
                        ))
                        .toList()
        );
    }

    private List<fr.insastronaute.webapi.challenge.dto.ProofMediaDto> proofMedia(ChallengeSubmission submission) {
        if (PROOF_TYPE_TEXT.equals(submission.getProofType())) {
            return List.of();
        }
        List<String> keys = proofStorageService.parseProofFiles(submission.getProofData());
        return java.util.stream.IntStream.range(0, keys.size())
                .mapToObj(index -> new fr.insastronaute.webapi.challenge.dto.ProofMediaDto(
                        index, proofStorageService.contentTypeForProofKey(keys.get(index))))
                .toList();
    }

    private ModerationSubmissionChallengeDto toChallengeDto(Challenge challenge) {
        return new ModerationSubmissionChallengeDto(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getDescription(),
                challenge.getPointsReward(),
                challenge.getCategory(),
                challenge.getValidationMode(),
                Boolean.TRUE.equals(challenge.getSensitive()),
                challenge.getMinGroupSize(),
                challenge.getMaxGroupSize()
        );
    }

    private AdminUserFriendshipDto toFriendshipDto(AppUser user, Friendship friendship) {
        boolean outgoing = friendship.getRequester().getId().equals(user.getId());
        AppUser otherUser = outgoing ? friendship.getAddressee() : friendship.getRequester();
        return new AdminUserFriendshipDto(
                friendship.getId(),
                friendship.getStatus(),
                outgoing ? "OUTGOING" : "INCOMING",
                otherUser.getId(),
                displayName(otherUser),
                otherUser.getEmail(),
                friendship.getCreatedAt(),
                friendship.getRespondedAt()
        );
    }

    private AdminUserCardCollectionDto toCardCollectionDto(CardCollection collection) {
        Card card = collection.getCard();
        return new AdminUserCardCollectionDto(
                collection.getId(),
                collection.getCollectionType(),
                card.getId(),
                card.getName(),
                card.getCategory(),
                card.getDisplayOrder(),
                collection.getQuantity(),
                collection.getFirstDiscoveredAt(),
                collection.getUpdatedAt()
        );
    }

    private String displayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail();
    }
}
