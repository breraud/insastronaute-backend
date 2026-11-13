package fr.insastronaute.webapi.challenge.auto.types.specificcard;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
 * « Obtiens cette carte-là » : la seule phrase de collection qui désigne sa cible.
 */
@ExtendWith(MockitoExtension.class)
class SpecificCardObtainedChallengeTypeTest {

    @Mock private CardCollectionRepository collectionRepository;
    @Mock private CardTeamMemberRepository teamMemberRepository;
    @Mock private CardRepository cardRepository;

    @InjectMocks private SpecificCardObtainedChallengeType type;

    private final UUID cardId = UUID.randomUUID();

    private SpecificCardObtainedParameters parameters(String collectionType) {
        return new SpecificCardObtainedParameters(cardId, collectionType);
    }

    @Test
    void aPhraseWithoutACardIsRefused() {
        // Sans cible, la phrase ne veut rien dire : mieux vaut un enregistrement
        // refuse qu'un defi que personne ne peut valider.
        assertThatThrownBy(() -> new SpecificCardObtainedParameters(null, "PHYSICAL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absentCollectionMeansPhysical() {
        assertThat(parameters(null).collectionType()).isEqualTo(CardCollectionType.PHYSICAL);
        assertThat(type.isTeamScoped(parameters(null))).isTrue();
    }

    @Test
    void thePhraseNamesTheCard() {
        Card card = new Card();
        card.setName("Goofy");
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThat(type.phrase(parameters("PHYSICAL"))).isEqualTo("Obtiens la carte Goofy");
        assertThat(type.phrase(parameters("VIRTUAL")))
                .isEqualTo("Obtiens la carte Goofy (collection virtuelle)");
    }

    @Test
    void aDeletedCardStillReadsAsAPhrase() {
        // Le defi doit rester lisible et modifiable dans le panneau, meme si la
        // carte visee a disparu du catalogue.
        when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThat(type.phrase(parameters("PHYSICAL"))).isEqualTo("Obtiens la carte inconnue");
    }

    @Test
    void oneHolderIsEnoughToComplete() {
        UUID userId = UUID.randomUUID();
        when(teamMemberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(collectionRepository.countHoldersOfCardAmongUsers(
                any(), eq(cardId), eq(CardCollectionType.PHYSICAL))).thenReturn(1L);

        AutoChallengeProgress progress = type.progress(parameters("PHYSICAL"), userId);

        assertThat(progress.target()).isEqualTo(1);
        assertThat(progress.completed()).isTrue();
    }

    @Test
    void nobodyHoldingItLeavesTheGoalOpen() {
        UUID userId = UUID.randomUUID();
        when(collectionRepository.countHoldersOfCardAmongUsers(
                eq(List.of(userId)), eq(cardId), eq(CardCollectionType.VIRTUAL))).thenReturn(0L);

        assertThat(type.progress(parameters("VIRTUAL"), userId).completed()).isFalse();
    }

    @Test
    void aVirtualGoalIsMeasuredOnThePlayerAlone() {
        UUID userId = UUID.randomUUID();
        SpecificCardObtainedParameters parameters = parameters("VIRTUAL");
        when(collectionRepository.countHoldersOfCardAmongUsers(
                eq(List.of(userId)), eq(cardId), eq(CardCollectionType.VIRTUAL))).thenReturn(1L);

        type.progress(parameters, userId);

        assertThat(type.isTeamScoped(parameters)).isFalse();
        // Pas meme une lecture de l'equipe : la collection virtuelle est personnelle.
        verify(teamMemberRepository, never()).findByUserId(userId);
    }

    @Test
    void aPhysicalGoalStillCountsTheWholeTeam() {
        UUID userId = UUID.randomUUID();
        when(teamMemberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(collectionRepository.countHoldersOfCardAmongUsers(
                any(), eq(cardId), eq(CardCollectionType.PHYSICAL))).thenReturn(0L);

        type.progress(parameters("PHYSICAL"), userId);

        verify(teamMemberRepository).findByUserId(userId);
    }

    @Test
    void aVirtualGoalNeverLooksForTeamsAmongCandidates() {
        when(collectionRepository.findUsersOwningCard(
                eq(cardId), eq(CardCollectionType.VIRTUAL), any(), any(), any()))
                .thenReturn(List.of());

        type.findCandidates(parameters("VIRTUAL"), new AutoChallengeCandidateQuery(UUID.randomUUID(), null, 20));

        verify(collectionRepository, never()).findTeamsOwningCard(any(), any(), any());
    }

    @Test
    void aPhysicalGoalLooksForTeamsFirst() {
        // Un joueur peut valider grace a la carte d'un coequipier : partir des
        // joueurs manquerait precisement ceux que l'equipe porte.
        UUID teamId = UUID.randomUUID();
        when(collectionRepository.findTeamsOwningCard(eq(cardId), any(), any())).thenReturn(List.of(teamId));
        when(teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId)).thenReturn(List.of());

        type.findCandidates(parameters("PHYSICAL"), new AutoChallengeCandidateQuery(UUID.randomUUID(), null, 20));

        verify(collectionRepository, never()).findUsersOwningCard(any(), any(), any(), any(), any());
    }
}
