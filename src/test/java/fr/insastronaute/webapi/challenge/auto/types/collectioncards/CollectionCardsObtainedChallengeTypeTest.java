package fr.insastronaute.webapi.challenge.auto.types.collectioncards;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * « Obtiens N cartes », sans categorie : la phrase vise la collection entiere.
 */
@ExtendWith(MockitoExtension.class)
class CollectionCardsObtainedChallengeTypeTest {

    @Mock private CardCollectionRepository collectionRepository;
    @Mock private CardTeamMemberRepository teamMemberRepository;

    @InjectMocks private CollectionCardsObtainedChallengeType type;

    private CollectionCardsParameters parameters(int count, String collectionType) {
        return new CollectionCardsParameters(count, collectionType);
    }

    @Test
    void aPhraseWithoutCollectionMeansPhysical() {
        // Comme les phrases de categorie : le physique est le sens historique.
        assertThat(parameters(3, null).collectionType()).isEqualTo(CardCollectionType.PHYSICAL);
        assertThat(type.isTeamScoped(parameters(3, null))).isTrue();
    }

    @Test
    void itCountsWhatTheWholeTeamOwnsInPhysical() {
        UUID userId = UUID.randomUUID();
        when(teamMemberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(collectionRepository.countDistinctCardsForUsers(any(), eq(CardCollectionType.PHYSICAL)))
                .thenReturn(4L);

        var progress = type.progress(parameters(10, "PHYSICAL"), userId);

        assertThat(progress.current()).isEqualTo(4L);
        assertThat(progress.target()).isEqualTo(10L);
        verify(teamMemberRepository).findByUserId(userId);
    }

    @Test
    void aVirtualPhraseIsMeasuredOnThePlayerAlone() {
        UUID userId = UUID.randomUUID();
        when(collectionRepository.countDistinctCardsForUsers(
                eq(List.of(userId)), eq(CardCollectionType.VIRTUAL))).thenReturn(2L);

        type.progress(parameters(5, "VIRTUAL"), userId);

        assertThat(type.isTeamScoped(parameters(5, "VIRTUAL"))).isFalse();
        verify(teamMemberRepository, never()).findByUserId(userId);
    }

    @Test
    void aVirtualPhraseNeverLooksForTeamsAmongCandidates() {
        when(collectionRepository.findUsersOwningEnoughCards(
                eq(CardCollectionType.VIRTUAL), anyLong(), any(), any(), any())).thenReturn(List.of());

        type.findCandidates(parameters(5, "VIRTUAL"), new AutoChallengeCandidateQuery(UUID.randomUUID(), null, 20));

        verify(collectionRepository, never()).findTeamsOwningEnoughCards(anyLong(), any(), any());
    }

    @Test
    void thePhraseSaysWhichCollectionWhenItIsVirtual() {
        assertThat(type.phrase(parameters(5, "VIRTUAL"))).isEqualTo("Obtiens 5 cartes différentes (collection virtuelle)");
        assertThat(type.phrase(parameters(1, "PHYSICAL"))).isEqualTo("Obtiens une carte");
    }
}
