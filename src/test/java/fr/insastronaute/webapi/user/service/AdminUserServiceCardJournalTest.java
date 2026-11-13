package fr.insastronaute.webapi.user.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.service.AdminCardTeamService;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.dto.AdminUserCardChangeRequest;
import fr.insastronaute.webapi.user.dto.AdminUserCardRemovalRequest;
import fr.insastronaute.webapi.auth.keycloak.KeycloakAccountGateway;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifie que AdminUserService, et non CardService ni CardInstanceService, ecrit
 * dans le registre pour les gestes de la fiche joueur qui touchent les cartes.
 *
 * <p>La journalisation vit chez l'appelant admin parce que CardService.addVirtualCards
 * sert aussi les recompenses de partie PvP, et CardInstanceService.claim sert la
 * reclamation d'une carte par le joueur lui-meme : journaliser plus bas attribuerait
 * ces gestes a un administrateur qui n'a rien fait.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceCardJournalTest {

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

    private AdminUserService service;
    private AppUser player;
    private Card card;
    private UUID firstInstanceId;
    private UUID secondInstanceId;

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

        player = new AppUser();
        player.setId(UUID.randomUUID());

        card = new Card();
        card.setId(UUID.randomUUID());

        firstInstanceId = UUID.randomUUID();
        secondInstanceId = UUID.randomUUID();
    }

    /**
     * La ligne doit nommer la carte, pas seulement la quantite : un UUID de
     * carte ne se relit plus une fois la definition supprimee, et le registre
     * est en ajout seul - ce qui manque a l'ecriture manque pour toujours.
     */
    @Test
    void grantingVirtualCardsWritesOneEventWithTheCardAndTheQuantity() {
        when(cardService.addVirtualCards(player.getId(), card.getId(), 3)).thenReturn("Carte Rare");

        service.addCards(player.getId(),
                new AdminUserCardChangeRequest(card.getId(), CardCollectionType.VIRTUAL, 3, List.of()),
                "sub-admin");

        verify(journal).record("sub-admin", player.getId(), AdminActionType.VIRTUAL_CARDS_GRANTED,
                Map.of("cardId", card.getId().toString(), "cardName", "Carte Rare", "quantity", 3));
    }

    @Test
    void takingVirtualCardsBackWritesARevokedEvent() {
        // Attention : les deux records n'ont pas le meme ordre de champs.
        // AdminUserCardRemovalRequest commence par le type de collection.
        when(cardService.removeVirtualCards(player.getId(), card.getId(), 2)).thenReturn("Carte Rare");

        service.removeCards(player.getId(),
                new AdminUserCardRemovalRequest(CardCollectionType.VIRTUAL, card.getId(), 2, List.of()),
                "sub-admin");

        verify(journal).record("sub-admin", player.getId(), AdminActionType.VIRTUAL_CARDS_REVOKED,
                Map.of("cardId", card.getId().toString(), "cardName", "Carte Rare", "quantity", 2));
    }

    /** Deux instances attribuees, deux lignes - et pas deux fois la meme. */
    @Test
    void assigningPhysicalInstancesWritesOneEventPerInstance() {
        List<UUID> instanceIds = List.of(firstInstanceId, secondInstanceId);
        when(cardInstanceService.assignAvailableToUser(player.getId(), instanceIds)).thenReturn(List.of(
                new CardInstanceService.MovedInstance(firstInstanceId, "Carte Rare"),
                new CardInstanceService.MovedInstance(secondInstanceId, "Carte Commune")));

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.addCards(player.getId(),
                new AdminUserCardChangeRequest(null, CardCollectionType.PHYSICAL, null, instanceIds),
                "sub-admin");

        verify(journal, times(2)).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED), details.capture());
        assertThat(details.getAllValues()).containsExactly(
                Map.of("instanceId", firstInstanceId.toString(), "cardName", "Carte Rare"),
                Map.of("instanceId", secondInstanceId.toString(), "cardName", "Carte Commune"));
    }

    /**
     * Une instance deja detenue est passee en silence par le service de
     * cartes : la journaliser inventerait un geste qui n'a pas eu lieu, dans
     * une table que personne ne peut corriger.
     */
    @Test
    void anInstanceAlreadyHeldByThePlayerWritesNoEvent() {
        List<UUID> instanceIds = List.of(firstInstanceId, secondInstanceId);
        when(cardInstanceService.assignAvailableToUser(player.getId(), instanceIds)).thenReturn(List.of(
                new CardInstanceService.MovedInstance(secondInstanceId, "Carte Commune")));

        service.addCards(player.getId(),
                new AdminUserCardChangeRequest(null, CardCollectionType.PHYSICAL, null, instanceIds),
                "sub-admin");

        verify(journal).record("sub-admin", player.getId(), AdminActionType.PHYSICAL_CARD_ASSIGNED,
                Map.of("instanceId", secondInstanceId.toString(), "cardName", "Carte Commune"));
        verify(journal, times(1)).record(any(), any(), any(), any());
    }

    @Test
    void releasingPhysicalInstancesWritesOneEventPerInstance() {
        List<UUID> instanceIds = List.of(firstInstanceId, secondInstanceId);
        when(cardInstanceService.releaseOwnedByUser(player.getId(), instanceIds)).thenReturn(List.of(
                new CardInstanceService.MovedInstance(firstInstanceId, "Carte Rare"),
                new CardInstanceService.MovedInstance(secondInstanceId, "Carte Commune")));

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.removeCards(player.getId(),
                new AdminUserCardRemovalRequest(CardCollectionType.PHYSICAL, null, null, instanceIds),
                "sub-admin");

        verify(journal, times(2)).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED), details.capture());
        assertThat(details.getAllValues()).containsExactly(
                Map.of("instanceId", firstInstanceId.toString(), "cardName", "Carte Rare"),
                Map.of("instanceId", secondInstanceId.toString(), "cardName", "Carte Commune"));
    }

    @Test
    void aFailingVirtualCardGrantPropagatesAndWritesNoJournalEntry() {
        UUID cardId = card.getId();
        RuntimeException failure = new RuntimeException("addVirtualCards a echoue");
        org.mockito.Mockito.doThrow(failure).when(cardService).addVirtualCards(player.getId(), cardId, 3);

        assertThatThrownBy(() -> service.addCards(player.getId(),
                new AdminUserCardChangeRequest(cardId, CardCollectionType.VIRTUAL, 3, List.of()),
                "sub-admin"))
                .isSameAs(failure);

        verifyNoInteractions(journal);
    }

    @Test
    void aFailingPhysicalAssignmentPropagatesAndWritesNoJournalEntry() {
        List<UUID> instanceIds = List.of(firstInstanceId, secondInstanceId);
        RuntimeException failure = new RuntimeException("assignAvailableToUser a echoue");
        org.mockito.Mockito.doThrow(failure).when(cardInstanceService).assignAvailableToUser(player.getId(), instanceIds);

        assertThatThrownBy(() -> service.addCards(player.getId(),
                new AdminUserCardChangeRequest(null, CardCollectionType.PHYSICAL, null, instanceIds),
                "sub-admin"))
                .isSameAs(failure);

        verifyNoInteractions(journal);
    }
}
