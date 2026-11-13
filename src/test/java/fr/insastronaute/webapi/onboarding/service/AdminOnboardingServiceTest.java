package fr.insastronaute.webapi.onboarding.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.auth.sessionreset.UserSessionRevocationService;
import fr.insastronaute.webapi.user.service.RealmRoleDirectory;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.onboarding.dto.PendingOnboardingDto;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.domain.Gender;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOnboardingServiceTest {

    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock KeycloakRealmRoleGateway roleGateway;
    @Mock AdminActionJournal journal;
    @Mock UserSessionRevocationService sessionRevocationService;

    private AdminOnboardingService service;

    private static AppUser player(OnboardingStatus status) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("kc-" + UUID.randomUUID());
        user.setDisplayName("Alice Martin");
        user.setEmail("alice@example.test");
        user.setOnboardingStatus(status);
        return user;
    }

    private static AppUser decider() {
        AppUser respo = new AppUser();
        respo.setId(UUID.randomUUID());
        respo.setDisplayName("Respo Defis");
        respo.setEmail("respo@example.test");
        return respo;
    }

    @BeforeEach
    void setUp() {
        service = new AdminOnboardingService(
                userRepository,
                departmentRepository,
                roleGateway,
                journal,
                sessionRevocationService,
                transactionManager()
        );
    }

    // Un vrai gestionnaire de transaction sans base derriere, sur le meme
    // modele que PlaceConfigServiceTest : commit/rollback ne font rien, mais
    // il honore reellement le contrat de PlatformTransactionManager plutot
    // qu'un mock dont il faudrait deviner les methodes a stubber.
    private static PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
    }

    @Test
    void validationAssignsTheRoleBeforeWritingTheDecision() {
        AppUser target = player(OnboardingStatus.PENDING);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.validate(target.getId(), decider);

        // L'ordre est la garantie : trois verify independants passeraient tout aussi
        // bien si l'ecriture precedait l'appel Keycloak.
        InOrder order = inOrder(roleGateway, userRepository);
        order.verify(roleGateway).assignRealmRole(target.getKeycloakSub(), "STUDENT");
        order.verify(userRepository).save(target);
    }

    /**
     * La decision d'accueil entre au registre, apres Keycloak.
     *
     * <p>C'est le geste qui donne l'acces au jeu, et la fiche du joueur ne garde que la
     * derniere decision : un compte valide, refuse, puis revalide n'y laisse qu'une ligne.
     * L'etat precedent voyage dans les details, parce que c'est lui qui distingue une
     * premiere admission d'un retour en arriere.</p>
     */
    @Test
    void validationEntersTheAdminRegistryAfterKeycloak() {
        AppUser target = player(OnboardingStatus.REJECTED);
        AppUser decider = decider();
        decider.setKeycloakSub("respo-sub");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.validate(target.getId(), decider);

        InOrder order = inOrder(roleGateway, journal);
        order.verify(roleGateway).assignRealmRole(target.getKeycloakSub(), "STUDENT");
        order.verify(journal).record(
                org.mockito.ArgumentMatchers.eq("respo-sub"),
                org.mockito.ArgumentMatchers.eq(target.getId()),
                org.mockito.ArgumentMatchers.eq(AdminActionType.ONBOARDING_VALIDATED),
                org.mockito.ArgumentMatchers.argThat(details -> "REJECTED".equals(details.get("previousStatus"))));
    }

    /** Le refus s'ecrit aussi : un registre ou manquent les retraits ne dit rien. */
    @Test
    void rejectionEntersTheRegistryWithItsMotive() {
        AppUser target = player(OnboardingStatus.PENDING);
        AppUser decider = decider();
        decider.setKeycloakSub("respo-sub");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.reject(target.getId(), decider, "  Photo illisible  ");

        verify(journal).record(
                org.mockito.ArgumentMatchers.eq("respo-sub"),
                org.mockito.ArgumentMatchers.eq(target.getId()),
                org.mockito.ArgumentMatchers.eq(AdminActionType.ONBOARDING_REJECTED),
                org.mockito.ArgumentMatchers.argThat(details ->
                        "Photo illisible".equals(details.get("reason"))
                                && "PENDING".equals(details.get("previousStatus"))));
    }

    /** Keycloak refuse : rien n'est ecrit, et le registre n'affirme donc aucun acces. */
    @Test
    void nothingEntersTheRegistryWhenKeycloakRefuses() {
        AppUser target = player(OnboardingStatus.PENDING);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        doThrow(new RuntimeException("keycloak down"))
                .when(roleGateway).assignRealmRole(anyString(), anyString());

        try {
            service.validate(target.getId(), decider);
        } catch (RuntimeException expected) {
            // L'echec est le sujet du test : ce qui compte est ce qui n'a pas ete ecrit.
        }

        verifyNoInteractions(journal);
    }

    @Test
    void nothingIsWrittenWhenKeycloakRefusesTheAssignment() {
        AppUser target = player(OnboardingStatus.PENDING);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        doThrow(new RuntimeException("keycloak down"))
                .when(roleGateway).assignRealmRole(anyString(), anyString());

        assertThatThrownBy(() -> service.validate(target.getId(), decider))
                .isInstanceOf(RuntimeException.class);

        assertThat(target.getOnboardingStatus()).isEqualTo(OnboardingStatus.PENDING);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectionRemovesTheRoleSoAValidatedAccountLosesAccess() {
        AppUser target = player(OnboardingStatus.VALIDATED);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.cancel(target.getId(), decider, "Compte suspect");

        InOrder order = inOrder(roleGateway, sessionRevocationService, userRepository);
        order.verify(roleGateway).removeRealmRole(target.getKeycloakSub(), "STUDENT");
        order.verify(sessionRevocationService).revokeAll(target.getKeycloakSub());
        order.verify(userRepository).save(target);
        assertThat(target.getOnboardingStatus()).isEqualTo(OnboardingStatus.REJECTED);
        assertThat(target.getOnboardingRejectionReason()).isEqualTo("Compte suspect");
        assertThat(target.getOnboardingDecidedBy()).isEqualTo(decider);
    }

    @Test
    void theRegularRejectEndpointCannotCancelAValidatedAccount() {
        AppUser target = player(OnboardingStatus.VALIDATED);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.reject(target.getId(), decider(), "Compte suspect"))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode())
                        .isEqualTo("ONBOARDING_CANCELLATION_REQUIRED"));

        verifyNoInteractions(roleGateway, sessionRevocationService);
    }

    @Test
    void anEmptyReasonIsRefused() {
        AppUser target = player(OnboardingStatus.PENDING);
        AppUser decider = decider();

        assertThatThrownBy(() -> service.reject(target.getId(), decider, "   "))
                .isInstanceOf(CodedBadRequestException.class)
                .satisfies(thrown -> assertThat(((CodedBadRequestException) thrown).getCode())
                        .isEqualTo("ONBOARDING_REASON_REQUIRED"));

        verifyNoInteractions(roleGateway);
    }

    @Test
    void theQueueIsOldestFirst() {
        service.pending();

        verify(userRepository).findByOnboardingStatusOrderByOnboardingSubmittedAtAsc(OnboardingStatus.PENDING);
    }

    @Test
    void theRejectedListIsMostRecentDecisionFirst() {
        // Le recours humain de la spec 2.4 n'a de sens que si un respo retrouve
        // facilement le refus le plus recent, pas s'il doit remonter toute la
        // liste : c'est la date de decision qui ordonne, pas la soumission.
        service.rejected();

        verify(userRepository).findByOnboardingStatusOrderByOnboardingDecidedAtDesc(OnboardingStatus.REJECTED);
    }

    @Test
    void rejectedMapsTheMotiveForThePanel() {
        AppUser rejected = player(OnboardingStatus.REJECTED);
        rejected.setOnboardingRejectionReason("Adresse mail hors de l'INSA");
        when(userRepository.findByOnboardingStatusOrderByOnboardingDecidedAtDesc(OnboardingStatus.REJECTED))
                .thenReturn(List.of(rejected));

        List<PendingOnboardingDto> queue = service.rejected();

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).rejectionReason()).isEqualTo("Adresse mail hors de l'INSA");
    }

    @Test
    void pendingLeavesTheMotiveNull() {
        // Le meme DTO sert aux deux files : un compte encore en attente n'a
        // jamais ete refuse, le champ doit donc rester nul plutot que de
        // fuiter un motif d'un tour precedent.
        AppUser waiting = player(OnboardingStatus.PENDING);
        when(userRepository.findByOnboardingStatusOrderByOnboardingSubmittedAtAsc(OnboardingStatus.PENDING))
                .thenReturn(List.of(waiting));

        List<PendingOnboardingDto> queue = service.pending();

        assertThat(queue.get(0).rejectionReason()).isNull();
    }

    @Test
    void backfillAssignsTheRoleAndMarksEveryoneValidated() {
        AppUser first = player(OnboardingStatus.NOT_STARTED);
        AppUser second = player(OnboardingStatus.NOT_STARTED);
        when(userRepository.findAll()).thenReturn(List.of(first, second));

        int processed = service.backfill();

        assertThat(processed).isEqualTo(2);
        verify(roleGateway).assignRealmRole(first.getKeycloakSub(), "STUDENT");
        verify(roleGateway).assignRealmRole(second.getKeycloakSub(), "STUDENT");
        assertThat(first.getOnboardingStatus()).isEqualTo(OnboardingStatus.VALIDATED);
        assertThat(second.getOnboardingStatus()).isEqualTo(OnboardingStatus.VALIDATED);
    }

    @Test
    void backfillCommitsEachAccountIndependentlyRatherThanTheWholeSweep() {
        // Le coeur du Fix 4 : une transaction par compte, pas une seule pour tout
        // le balayage. Si Keycloak echoue sur le second compte, le premier doit
        // deja etre ecrit — une relance completera le reste au lieu de repartir
        // de zero apres un timeout cote client sur une longue transaction unique.
        AppUser first = player(OnboardingStatus.NOT_STARTED);
        AppUser second = player(OnboardingStatus.NOT_STARTED);
        when(userRepository.findAll()).thenReturn(List.of(first, second));
        // Stub large d'abord, specifique ensuite : sous stubbing strict, une
        // invocation avec des arguments qui ne correspondent a aucun stub declare
        // pour cette methode leve PotentialStubbingProblem plutot que de retomber
        // sur un comportement par defaut — il faut donc couvrir explicitement
        // l'appel pour `first` qui, lui, doit reussir.
        doNothing().when(roleGateway).assignRealmRole(anyString(), anyString());
        doThrow(new RuntimeException("keycloak down"))
                .when(roleGateway).assignRealmRole(second.getKeycloakSub(), "STUDENT");

        assertThatThrownBy(() -> service.backfill())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("keycloak down");

        assertThat(first.getOnboardingStatus()).isEqualTo(OnboardingStatus.VALIDATED);
        verify(userRepository).save(first);
        assertThat(second.getOnboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
        verify(userRepository, never()).save(second);
    }

    @Test
    void backfillLeavesARejectedAccountUntouched() {
        // Un compte REJECTED porte une decision humaine deliberee : un rattrapage
        // en masse ne doit jamais l'annuler, ni au premier passage ni sur une
        // relance ulterieure en plein inte.
        AppUser rejected = player(OnboardingStatus.REJECTED);
        rejected.setOnboardingRejectionReason("Compte suspect");
        when(userRepository.findAll()).thenReturn(List.of(rejected));

        int processed = service.backfill();

        assertThat(processed).isEqualTo(0);
        assertThat(rejected.getOnboardingStatus()).isEqualTo(OnboardingStatus.REJECTED);
        assertThat(rejected.getOnboardingRejectionReason()).isEqualTo("Compte suspect");
        verifyNoInteractions(roleGateway);
        verify(userRepository, never()).save(any());
    }

    @Test
    void backfillLeavesAPendingAccountUntouched() {
        // Un compte PENDING est une soumission arrivee apres la mise en place de
        // la porte : le valider en masse court-circuiterait l'examen d'un respo,
        // qui est exactement ce que la file existe pour garantir.
        AppUser pending = player(OnboardingStatus.PENDING);
        when(userRepository.findAll()).thenReturn(List.of(pending));

        int processed = service.backfill();

        assertThat(processed).isEqualTo(0);
        assertThat(pending.getOnboardingStatus()).isEqualTo(OnboardingStatus.PENDING);
        verifyNoInteractions(roleGateway);
        verify(userRepository, never()).save(any());
    }

    @Test
    void pendingMapsEveryFieldForThePanel() {
        AppUser waiting = player(OnboardingStatus.PENDING);
        waiting.setGender(Gender.FEMALE);
        waiting.setOnboardingSubmittedAt(Instant.parse("2026-08-28T09:00:00Z"));
        UUID departmentId = UUID.randomUUID();
        waiting.setDepartment(departmentId);

        Department department = new Department();
        department.setShortName("INFO");
        when(userRepository.findByOnboardingStatusOrderByOnboardingSubmittedAtAsc(OnboardingStatus.PENDING))
                .thenReturn(List.of(waiting));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        List<PendingOnboardingDto> queue = service.pending();

        assertThat(queue).hasSize(1);
        PendingOnboardingDto dto = queue.get(0);
        assertThat(dto.userId()).isEqualTo(waiting.getId());
        assertThat(dto.fullName()).isEqualTo("Alice Martin");
        assertThat(dto.email()).isEqualTo("alice@example.test");
        assertThat(dto.department()).isEqualTo("INFO");
        assertThat(dto.gender()).isEqualTo(Gender.FEMALE);
        assertThat(dto.submittedAt()).isEqualTo(Instant.parse("2026-08-28T09:00:00Z"));
    }

    @Test
    void validateRevokesSessionsWithoutKeycloakLogout() {
        AppUser target = player(OnboardingStatus.REJECTED);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.validate(target.getId(), decider);

        verify(roleGateway, never()).logoutUser(any());
        verify(sessionRevocationService).revokeAll(target.getKeycloakSub());
    }

    @Test
    void validateLimitedAssignsRoleAndRevokesSessionsWithoutKeycloakLogout() {
        AppUser target = player(OnboardingStatus.REJECTED);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.validateLimited(target.getId(), decider);

        verify(roleGateway).assignRealmRole(target.getKeycloakSub(), RealmRoleDirectory.STUDENT_LIMITED_ROLE);
        verify(roleGateway).removeRealmRole(target.getKeycloakSub(), RealmRoleDirectory.STUDENT_ROLE);
        verify(roleGateway, never()).logoutUser(any());
        verify(sessionRevocationService).revokeAll(target.getKeycloakSub());
        assertThat(target.getHiddenFromLeaderboards()).isTrue();
        assertThat(target.getOnboardingStatus()).isEqualTo(OnboardingStatus.VALIDATED);
    }

    @Test
    void rejectLogsOutUserFromKeycloakAndRevokesSessions() {
        AppUser target = player(OnboardingStatus.PENDING);
        AppUser decider = decider();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.reject(target.getId(), decider, "Email invalide");

        verify(roleGateway).logoutUser(target.getKeycloakSub());
        verify(sessionRevocationService).revokeAll(target.getKeycloakSub());
    }
}

