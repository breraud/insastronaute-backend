package fr.insastronaute.webapi.challenge.auto.types.cardcategory;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
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
 * Le choix entre collection physique et virtuelle.
 *
 * <p>Seules les cartes physiques sont mutualisées : un objectif sur la collection
 * virtuelle ne peut ni se mesurer sur l'équipe ni partager sa récompense.</p>
 */
@ExtendWith(MockitoExtension.class)
class CardCategoryObtainedCollectionScopeTest {

    @Mock private CardCollectionRepository collectionRepository;
    @Mock private CardTeamMemberRepository teamMemberRepository;
    @Mock private CardCategoryRepository categoryRepository;

    @InjectMocks private CardCategoryObtainedChallengeType type;

    private CardCategoryObtainedParameters parameters(String collectionType) {
        return new CardCategoryObtainedParameters(UUID.randomUUID(), 3, collectionType);
    }

    @Test
    void absentCollectionMeansPhysical() {
        // Les phrases écrites avant ce choix portaient sur le physique : elles ne
        // doivent pas changer de sens parce qu'un champ est apparu.
        assertThat(parameters(null).collectionType()).isEqualTo(CardCollectionType.PHYSICAL);
        assertThat(type.isTeamScoped(parameters(null))).isTrue();
    }

    @Test
    void aVirtualGoalIsMeasuredOnThePlayerAlone() {
        UUID userId = UUID.randomUUID();
        CardCategoryObtainedParameters parameters = parameters("VIRTUAL");
        when(collectionRepository.countDistinctCardsInCategoryForUsers(
                eq(List.of(userId)), any(), eq(CardCollectionType.VIRTUAL))).thenReturn(1L);

        type.progress(parameters, userId);

        assertThat(type.isTeamScoped(parameters)).isFalse();
        // Pas même une lecture de l'équipe : la collection virtuelle est personnelle.
        verify(teamMemberRepository, never()).findByUserId(userId);
    }

    @Test
    void aPhysicalGoalStillCountsTheWholeTeam() {
        UUID userId = UUID.randomUUID();
        CardCategoryObtainedParameters parameters = parameters("PHYSICAL");
        when(teamMemberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(collectionRepository.countDistinctCardsInCategoryForUsers(
                any(), any(), eq(CardCollectionType.PHYSICAL))).thenReturn(2L);

        type.progress(parameters, userId);

        verify(teamMemberRepository).findByUserId(userId);
    }

    @Test
    void aVirtualGoalNeverLooksForTeamsAmongCandidates() {
        CardCategoryObtainedParameters parameters = parameters("VIRTUAL");
        when(collectionRepository.findUsersOwningCardsInCategory(
                any(), eq(CardCollectionType.VIRTUAL), anyLong(), any(), any(), any()))
                .thenReturn(List.of());

        type.findCandidates(parameters, new AutoChallengeCandidateQuery(UUID.randomUUID(), null, 20));

        verify(collectionRepository, never()).findTeamsOwningCardsInCategory(any(), anyLong(), any(), any());
    }
}
