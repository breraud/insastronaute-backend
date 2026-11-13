package fr.insastronaute.webapi.challenge.auto.types.collectioncompletion;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** « Complète X % de la collection », toutes catégories confondues. */
@ExtendWith(MockitoExtension.class)
class CollectionCompletionChallengeTypeTest {

    @Mock private CardCollectionRepository collectionRepository;
    @Mock private CardRepository cardRepository;
    @Mock private CardTeamMemberRepository teamMemberRepository;

    @InjectMocks private CollectionCompletionChallengeType type;

    @Test
    void aPhysicalGoalIsMeasuredOnWhatTheTeamSees() {
        // La collection physique se mesure sur l'équipe, dont la vue écarte les
        // extensions : viser le catalogue entier laisserait l'objectif ouvert sur
        // des cartes que cet écran ne montre pas.
        UUID userId = UUID.randomUUID();
        when(teamMemberRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());
        when(cardRepository.countTeamCollectionCards()).thenReturn(20L);
        when(collectionRepository.countDistinctCardsForUsers(any(), eq(CardCollectionType.PHYSICAL)))
                .thenReturn(5L);

        var progress = type.progress(new CollectionCompletionParameters(50, "PHYSICAL"), userId);

        assertThat(progress.target()).isEqualTo(10L);
        assertThat(progress.current()).isEqualTo(5L);
        verify(cardRepository, never()).countPersonalCollectionCards();
    }

    @Test
    void theTargetFollowsTheCatalogue() {
        // Le seuil se recalcule : une carte ajoutée en cours d'intégration déplace la
        // cible, ce qui est le sens même d'un pourcentage.
        UUID userId = UUID.randomUUID();
        when(cardRepository.countPersonalCollectionCards()).thenReturn(40L);
        when(collectionRepository.countDistinctCardsForUsers(eq(List.of(userId)), eq(CardCollectionType.VIRTUAL)))
                .thenReturn(9L);

        var progress = type.progress(new CollectionCompletionParameters(25, "VIRTUAL"), userId);

        assertThat(progress.target()).isEqualTo(10L);
        assertThat(progress.current()).isEqualTo(9L);
        assertThat(progress.completed()).isFalse();
    }

    @Test
    void aPercentageNeverRoundsDownToNothing() {
        // 1 % de trois cartes fait zéro à l'arrondi, et la phrase se validerait pour
        // qui ne possède rien.
        UUID userId = UUID.randomUUID();
        when(cardRepository.countPersonalCollectionCards()).thenReturn(3L);
        when(collectionRepository.countDistinctCardsForUsers(any(), any())).thenReturn(0L);

        var progress = type.progress(new CollectionCompletionParameters(1, "VIRTUAL"), userId);

        assertThat(progress.target()).isEqualTo(1L);
        assertThat(progress.completed()).isFalse();
    }

    @Test
    void anEmptyCatalogueCompletesNothing() {
        // Sans carte, « 100 % » serait atteint par tout le monde, y compris par qui
        // n'a jamais rien obtenu.
        UUID userId = UUID.randomUUID();
        when(cardRepository.countPersonalCollectionCards()).thenReturn(0L);
        when(collectionRepository.countDistinctCardsForUsers(any(), any())).thenReturn(0L);

        var progress = type.progress(new CollectionCompletionParameters(100, "VIRTUAL"), userId);

        assertThat(progress.completed()).isFalse();
    }

    // ---- Qui est candidat, sur un objectif d'equipe -----------------------------

    @Test
    void aTeamGoalDoesNotRewardWhatOnePlayerHoldsAlone() {
        // La collection physique est mise en commun : l'objectif porte sur ce que
        // l'equipe possede. Sans ce garde-fou, la recherche retombait sur la requete
        // personnelle des qu'aucune equipe n'atteignait le seuil, et recompensait un
        // joueur pour sa seule collection -- alors que sa barre d'avancement, elle,
        // comptait bien celle de l'equipe.
        UUID challengeId = UUID.randomUUID();
        UUID teamPlayer = UUID.randomUUID();
        CollectionCompletionParameters parameters = new CollectionCompletionParameters(25, "PHYSICAL");

        when(cardRepository.countTeamCollectionCards()).thenReturn(100L);
        when(collectionRepository.findTeamsOwningEnoughCards(eq(25L), any(), any()))
                .thenReturn(List.of());
        when(collectionRepository.findUsersOwningEnoughCards(
                eq(CardCollectionType.PHYSICAL), eq(25L), eq(challengeId), any(), any()))
                .thenReturn(List.of(teamPlayer));
        when(teamMemberRepository.findByUserId(teamPlayer))
                .thenReturn(Optional.of(new CardTeamMember()));

        var page = type.findCandidates(parameters, new AutoChallengeCandidateQuery(challengeId, null, 50));

        assertThat(page.userIds()).isEmpty();
    }

    @Test
    void aPlayerWithoutATeamStillQualifiesAlone() {
        // Personne ne le porte : sans cette porte, un joueur sans equipe ne pourrait
        // jamais valider un objectif sur la collection physique.
        UUID challengeId = UUID.randomUUID();
        UUID soloPlayer = UUID.randomUUID();
        CollectionCompletionParameters parameters = new CollectionCompletionParameters(25, "PHYSICAL");

        when(cardRepository.countTeamCollectionCards()).thenReturn(100L);
        when(collectionRepository.findTeamsOwningEnoughCards(eq(25L), any(), any()))
                .thenReturn(List.of());
        when(collectionRepository.findUsersOwningEnoughCards(
                eq(CardCollectionType.PHYSICAL), eq(25L), eq(challengeId), any(), any()))
                .thenReturn(List.of(soloPlayer));
        when(teamMemberRepository.findByUserId(soloPlayer)).thenReturn(Optional.empty());

        var page = type.findCandidates(parameters, new AutoChallengeCandidateQuery(challengeId, null, 50));

        assertThat(page.userIds()).containsExactly(soloPlayer);
    }

    @Test
    void aFilteredPageStillAdvancesTheScan() {
        // Une page pleine dont le filtre ne laisse qu'un joueur reste une page pleine.
        // Conclure d'apres la liste filtree arreterait le balayage la, et les joueurs
        // suivants attendraient le passage d'apres pour rien.
        UUID challengeId = UUID.randomUUID();
        UUID solo = UUID.randomUUID();
        UUID inTeam = UUID.randomUUID();
        List<UUID> page = java.util.stream.Stream.of(solo, inTeam).sorted().toList();
        CollectionCompletionParameters parameters = new CollectionCompletionParameters(25, "PHYSICAL");

        when(cardRepository.countTeamCollectionCards()).thenReturn(100L);
        when(collectionRepository.findTeamsOwningEnoughCards(eq(25L), any(), any())).thenReturn(List.of());
        when(collectionRepository.findUsersOwningEnoughCards(
                eq(CardCollectionType.PHYSICAL), eq(25L), eq(challengeId), any(), any()))
                .thenReturn(page);
        when(teamMemberRepository.findByUserId(solo)).thenReturn(Optional.empty());
        when(teamMemberRepository.findByUserId(inTeam)).thenReturn(Optional.of(new CardTeamMember()));

        var result = type.findCandidates(parameters, new AutoChallengeCandidateQuery(challengeId, null, 2));

        assertThat(result.userIds()).containsExactly(solo);
        assertThat(result.nextCursor()).isEqualTo(page.getLast());
    }

    @Test
    void aVirtualGoalIgnoresTeamsEntirely() {
        // Les cartes virtuelles ne sont pas mutualisees : chacun compte pour soi,
        // membre d'une equipe ou non, et l'appartenance n'a pas a etre interrogee.
        UUID challengeId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CollectionCompletionParameters parameters = new CollectionCompletionParameters(25, "VIRTUAL");

        when(cardRepository.countPersonalCollectionCards()).thenReturn(100L);
        when(collectionRepository.findUsersOwningEnoughCards(
                eq(CardCollectionType.VIRTUAL), eq(25L), eq(challengeId), any(), any()))
                .thenReturn(List.of(player));

        var page = type.findCandidates(parameters, new AutoChallengeCandidateQuery(challengeId, null, 50));

        assertThat(page.userIds()).containsExactly(player);
        verify(teamMemberRepository, never()).findByUserId(player);
    }

    @Test
    void thePhraseSaysTheCollectionOnlyWhenItIsVirtual() {
        assertThat(type.phrase(new CollectionCompletionParameters(100, "VIRTUAL")))
                .isEqualTo("Complète toute la collection (collection virtuelle)");
        assertThat(type.phrase(new CollectionCompletionParameters(50, "PHYSICAL")))
                .isEqualTo("Atteins 50 % de la collection");
    }
}
