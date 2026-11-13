package fr.insastronaute.webapi.challenge.auto.types;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.types.cardtype.CardTypeObtainedChallengeType;
import fr.insastronaute.webapi.challenge.auto.types.cardtype.CardTypeObtainedParameters;
import fr.insastronaute.webapi.challenge.auto.types.cardtypecompletion.CardTypeCompletionChallengeType;
import fr.insastronaute.webapi.challenge.auto.types.cardtypecompletion.CardTypeCompletionParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * « Avoir toutes les cartes bar » : le type de la carte, pas sa catégorie.
 *
 * <p>Deux axes indépendants dans le catalogue. La catégorie porte la rareté, le
 * type porte ce que la carte représente. Aucun des six défis automatiques liés aux
 * cartes ne visait le second, donc cette phrase ne s'écrivait pas.</p>
 */
class CardTypeChallengeTypesTest {

    private final CardCollectionRepository collections = mock(CardCollectionRepository.class);
    private final CardRepository cards = mock(CardRepository.class);
    private final CardTeamMemberRepository teams = mock(CardTeamMemberRepository.class);
    private final CardTypeRepository types = mock(CardTypeRepository.class);

    private final UUID typeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private CardType cardType(String name) {
        CardType type = new CardType();
        type.setName(name);
        return type;
    }

    /** La phrase est ce que le joueur lit : elle doit nommer le type, pas son identifiant. */
    @Test
    void theSentenceNamesTheTypeAndSaysWhenItIsTheWholeOfIt() {
        when(types.findById(typeId)).thenReturn(Optional.of(cardType("bar")));
        CardTypeCompletionChallengeType completion =
                new CardTypeCompletionChallengeType(collections, cards, teams, types);

        assertThat(completion.phrase(new CardTypeCompletionParameters(typeId, 100, CardCollectionType.PHYSICAL)))
                .isEqualTo("Obtiens toutes les cartes bar");
        assertThat(completion.phrase(new CardTypeCompletionParameters(typeId, 50, CardCollectionType.PHYSICAL)))
                .isEqualTo("Obtiens 50 % des cartes bar");
        // La collection virtuelle se dit : c'est elle qui surprendrait.
        assertThat(completion.phrase(new CardTypeCompletionParameters(typeId, 100, CardCollectionType.VIRTUAL)))
                .isEqualTo("Obtiens toutes les cartes bar (collection virtuelle)");
    }

    /**
     * Le dénominateur suit le catalogue, et l'arrondi est au supérieur : on ne
     * valide pas une complétion en restant sous la moitié honnête.
     */
    @Test
    void completionCountsAgainstTheCatalogueAndRoundsUp() {
        CardTypeCompletionChallengeType completion =
                new CardTypeCompletionChallengeType(collections, cards, teams, types);
        when(cards.countCollectionCardsOfType(typeId)).thenReturn(5L);
        when(teams.findByUserId(userId)).thenReturn(Optional.empty());
        when(collections.countDistinctCardsOfTypeForUsers(any(), eq(typeId), any())).thenReturn(3L);

        var progress = completion.progress(
                new CardTypeCompletionParameters(typeId, 50, CardCollectionType.PHYSICAL), userId);

        // 50 % de cinq cartes en demande trois, pas deux.
        assertThat(progress.target()).isEqualTo(3L);
        assertThat(progress.current()).isEqualTo(3L);
    }

    /** Un type vide serait sinon complété par tout le monde, sans rien faire. */
    @Test
    void anEmptyTypeIsNeverComplete() {
        CardTypeCompletionChallengeType completion =
                new CardTypeCompletionChallengeType(collections, cards, teams, types);
        when(cards.countCollectionCardsOfType(typeId)).thenReturn(0L);
        when(teams.findByUserId(userId)).thenReturn(Optional.empty());
        when(collections.countDistinctCardsOfTypeForUsers(any(), eq(typeId), any())).thenReturn(12L);

        var progress = completion.progress(
                new CardTypeCompletionParameters(typeId, 100, CardCollectionType.PHYSICAL), userId);

        assertThat(progress.target()).isEqualTo(Long.MAX_VALUE);
    }

    /**
     * Seule la collection physique est mise en commun dans une équipe : un objectif
     * sur la virtuelle se mesure joueur par joueur, et sa récompense ne se partage pas.
     */
    @Test
    void onlyThePhysicalCollectionIsMeasuredOnTheTeam() {
        CardTypeObtainedChallengeType obtained =
                new CardTypeObtainedChallengeType(collections, teams, types);

        assertThat(obtained.isTeamScoped(
                new CardTypeObtainedParameters(typeId, 3, CardCollectionType.PHYSICAL))).isTrue();
        assertThat(obtained.isTeamScoped(
                new CardTypeObtainedParameters(typeId, 3, CardCollectionType.VIRTUAL))).isFalse();
    }

    /** Le panneau dessine un sélecteur à partir de ce champ : personne ne connaît l'identifiant. */
    @Test
    void bothTypesAskForTheCardTypeThroughASelector() {
        CardTypeObtainedChallengeType obtained = new CardTypeObtainedChallengeType(collections, teams, types);
        CardTypeCompletionChallengeType completion =
                new CardTypeCompletionChallengeType(collections, cards, teams, types);

        for (List<fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter> parameters :
                List.of(obtained.parameters(), completion.parameters())) {
            assertThat(parameters)
                    .anySatisfy(parameter -> {
                        assertThat(parameter.name()).isEqualTo("typeId");
                        assertThat(parameter.type()).isEqualTo(AutoChallengeParameterType.CARD_TYPE);
                    });
            // Et la collection se choisit, comme sur les autres objectifs de cartes.
            assertThat(parameters).anySatisfy(parameter ->
                    assertThat(parameter.name()).isEqualTo("collectionType"));
        }
    }

    /** Un objectif sans type ne vise rien : mieux vaut le refuser que le publier. */
    @Test
    void aGoalWithoutATypeIsRefused() {
        assertThatThrownBy(() -> new CardTypeObtainedParameters(null, 3, "PHYSICAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardTypeCompletionParameters(null, 100, "PHYSICAL"))
                .isInstanceOf(IllegalArgumentException.class);
        // Sans pourcentage, « toutes » : c'est la phrase qu'on écrit le plus.
        assertThat(new CardTypeCompletionParameters(typeId, null, null).percentage()).isEqualTo(100);
    }
}
