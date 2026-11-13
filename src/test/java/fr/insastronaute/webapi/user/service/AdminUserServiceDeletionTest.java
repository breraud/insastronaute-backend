package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.auth.keycloak.KeycloakAccountGateway;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryUserReferences;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.dto.AdminPlayerDeletionDto;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La suppression d'un joueur : ce qu'elle emporte, ce qu'elle laisse, et ce qu'elle
 * refuse.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceDeletionTest {

    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ChallengeSubmissionRepository submissionRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock CardCollectionRepository cardCollectionRepository;
    @Mock CardInstanceService cardInstanceService;
    @Mock CardService cardService;
    @Mock ProofStorageService proofStorageService;
    @Mock ChallengeService challengeService;
    @Mock AdminCardTeamService adminCardTeamService;
    @Mock AdminActionJournal journal;
    @Mock KeycloakAccountGateway accountGateway;
    @Mock AdminActionEventRepository adminActionEventRepository;
    @Mock CardTeamRepository cardTeamRepository;
    @Mock MediaLibraryUserReferences mediaLibraryReferences;

    private AdminUserService service;
    private AppUser player;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
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
                cardTeamRepository
        );
        service.setMediaLibraryReferences(mediaLibraryReferences);

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub("sub-joueur");
        player.setDisplayName("Alice Martin");
        player.setEmail("alice@example.test");
    }

    private void found() {
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
    }

    // ── Ce qui part vraiment ─────────────────────────────────────────────────────

    /**
     * Sans rien qui la retienne, la ligne disparait pour de bon.
     *
     * <p>Les trente cascades emportent alors cartes, progression et soumissions : rien de
     * tout cela ne se voit chez d'autres joueurs, donc rien ne justifie de le garder.</p>
     */
    @Test
    void aPlayerNothingHoldsBackIsRemovedOutright() {
        found();
        when(adminActionEventRepository.isReferenced(player.getId())).thenReturn(false);
        when(cardTeamRepository.existsByCreatorId(player.getId())).thenReturn(false);

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.purged()).isTrue();
        assertThat(result.displayName()).isEqualTo("Alice Martin");
        verify(userRepository).delete(player);
        verify(userRepository, never()).save(any());
    }

    /** Le compte d'authentification part avant toute ecriture. */
    @Test
    void theAuthenticationAccountIsDeletedFirst() {
        found();

        service.deletePlayer("sub-admin", player.getId());

        verify(accountGateway).deleteAccount("sub-joueur");
    }

    /**
     * Un compte deja absent de Keycloak n'arrete rien.
     *
     * <p>C'est le cas d'une ligne orpheline -- compte efface ailleurs -- et celui d'une
     * reprise apres un echec survenu plus loin dans la suppression. Refuser ici rendrait
     * ces lignes impossibles a nettoyer.</p>
     */
    @Test
    void aMissingKeycloakAccountStillAllowsTheWholeDeletion() {
        found();
        when(accountGateway.deleteAccount("sub-joueur")).thenReturn(true);

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.keycloakAccountWasMissing()).isTrue();
        assertThat(result.purged()).isTrue();
        verify(userRepository).delete(player);
    }

    /** Une ligne sans identifiant Keycloak n'a aucun compte a fermer. */
    @Test
    void aRowWithoutAnyKeycloakIdentifierIsDeletedAllTheSame() {
        player.setKeycloakSub(null);
        found();

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.keycloakAccountWasMissing()).isTrue();
        verify(accountGateway, never()).deleteAccount(any());
        verify(userRepository).delete(player);
    }

    // ── Ce que le registre retient ───────────────────────────────────────────────

    /**
     * Nomme au registre, le joueur garde sa ligne -- et son nom.
     *
     * <p>Les deux colonnes du registre sont en {@code ON DELETE RESTRICT} : une trace de
     * moderation qui disparait avec son sujet ne trace rien. Le nom doit rester lisible,
     * c'est tout l'objet de la ligne conservee.</p>
     */
    @Test
    void aPlayerNamedInTheRegistryKeepsTheirRowAndTheirName() {
        found();
        when(adminActionEventRepository.isReferenced(player.getId())).thenReturn(true);

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.purged()).isFalse();
        assertThat(result.retainedBecause()).contains("registre");
        assertThat(player.getDeletedAt()).isNotNull();
        assertThat(player.getDisplayName()).isEqualTo("Alice Martin");
        // Le classement sait deja exclure sur ce drapeau.
        assertThat(player.getHiddenFromLeaderboards()).isTrue();
        verify(userRepository).save(player);
        verify(userRepository, never()).delete(any(AppUser.class));
    }

    /** Une equipe qu'il a fondee et qui vit encore retient aussi sa ligne. */
    @Test
    void aTeamTheyFoundedAlsoHoldsTheRow() {
        found();
        when(adminActionEventRepository.isReferenced(player.getId())).thenReturn(false);
        when(cardTeamRepository.existsByCreatorId(player.getId())).thenReturn(true);

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.purged()).isFalse();
        assertThat(result.retainedBecause()).contains("equipe");
        verify(userRepository).save(player);
    }

    /**
     * Un membre du staff qui a accorde un acces, retenu un media ou fige un export garde
     * sa ligne : ces colonnes de la Mediatheque n'ont pas d'ON DELETE. Sans le savoir
     * d'avance, la suppression echouait sur la cle etrangere - apres que le compte
     * Keycloak, lui, etait deja parti.
     */
    @Test
    void aStaffMemberNamedByTheMediaLibraryKeepsTheirRow() {
        found();
        when(adminActionEventRepository.isReferenced(player.getId())).thenReturn(false);
        when(cardTeamRepository.existsByCreatorId(player.getId())).thenReturn(false);
        when(mediaLibraryReferences.isReferenced(player.getId())).thenReturn(true);

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.purged()).isFalse();
        assertThat(result.retainedBecause()).contains("Médiathèque");
        assertThat(player.getDeletedAt()).isNotNull();
        verify(userRepository).save(player);
        verify(userRepository, never()).delete(any(AppUser.class));
    }

    // ── Ce qui disparait chez les autres ─────────────────────────────────────────

    /**
     * Le joueur quitte l'equipe et les amities des autres.
     *
     * <p>Un membre fantome dans une equipe est un bug pour ses coequipiers, et une demande
     * d'ami en attente proposerait de repondre a quelqu'un qui n'est plus la.</p>
     */
    @Test
    void theyLeaveOtherPlayersTeamsAndFriendships() {
        found();
        List<Friendship> friendships = List.of(new Friendship(), new Friendship());
        when(friendshipRepository.findAllInvolving(player.getId())).thenReturn(friendships);

        service.deletePlayer("sub-admin", player.getId());

        verify(adminCardTeamService).removeMemberIfPresent(player.getId());
        verify(friendshipRepository).deleteAll(friendships);
    }

    /**
     * N'appartenir a aucune equipe n'est pas une erreur, et surtout ne doit rien empoisonner.
     *
     * <p>Le retrait passait par {@code removeMember}, dont le {@link NotFoundException}
     * etait attrape ici. Mais cette methode est transactionnelle : Spring marquait la
     * transaction <em>rollback-only</em> avant de relancer, et le commit echouait ensuite
     * en {@code UnexpectedRollbackException} -- loin de la cause. Un mock n'a pas de
     * semantique transactionnelle, donc l'ancien test passait au vert sur du code casse.
     * D'ou l'appel idempotent, qui ne leve rien.</p>
     */
    @Test
    void havingNoTeamNeverPoisonsTheTransaction() {
        found();
        when(adminCardTeamService.removeMemberIfPresent(player.getId())).thenReturn(false);

        AdminPlayerDeletionDto result = service.deletePlayer("sub-admin", player.getId());

        assertThat(result.purged()).isTrue();
        verify(adminCardTeamService, never()).removeMember(any());
    }

    // ── Le registre ─────────────────────────────────────────────────────────────

    /**
     * Le geste s'ecrit sans cible, avec le nom dans les details.
     *
     * <p>Une cible en {@code ON DELETE RESTRICT} empecherait la suppression que cette
     * ligne enregistre : la trace interdirait ce qu'elle trace. Le nom porte dans les
     * details la garde lisible meme quand la ligne du joueur n'existe plus.</p>
     */
    @Test
    void theRegistryEntryNamesThePlayerWithoutPointingAtTheirRow() {
        found();

        service.deletePlayer("sub-admin", player.getId());

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(journal).recordGlobal(eq("sub-admin"), eq(AdminActionType.PLAYER_DELETED), details.capture());
        assertThat(details.getValue())
                .containsEntry("displayName", "Alice Martin")
                .containsEntry("email", "alice@example.test");
        verify(journal, never()).record(any(), any(), any(), any());
    }

    // ── Ce qui est refuse ───────────────────────────────────────────────────────

    @Test
    void anAdminCannotDeleteTheirOwnAccount() {
        found();

        assertThatThrownBy(() -> service.deletePlayer("sub-joueur", player.getId()))
                .isInstanceOf(BadRequestException.class);

        verify(accountGateway, never()).deleteAccount(any());
        verify(userRepository, never()).delete(any(AppUser.class));
    }

    /** Deux clics sur le meme bouton ne doivent pas rejouer le geste. */
    @Test
    void anAlreadyDeletedPlayerIsRefused() {
        player.setDeletedAt(Instant.parse("2026-09-01T10:00:00Z"));
        found();

        assertThatThrownBy(() -> service.deletePlayer("sub-admin", player.getId()))
                .isInstanceOf(ConflictException.class);

        verify(accountGateway, never()).deleteAccount(any());
        verify(journal, never()).recordGlobal(any(), any(), any());
    }

    @Test
    void anUnknownPlayerIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePlayer("sub-admin", unknown))
                .isInstanceOf(NotFoundException.class);
    }
}
