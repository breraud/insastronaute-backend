package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La carte bonus n'appartient à aucune collection : l'obtenir ne rapporte donc
 * aucun point de collection, et aucun jeton.
 */
@ExtendWith(MockitoExtension.class)
class CollectionRewardServiceTest {

    @Mock private CollectionPointsCalculator calculator;
    @Mock private UserRepository userRepository;
    @Mock private GoofyCardService goofyCardService;

    @InjectMocks private CollectionRewardService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void theBonusCardPaysNothing() {
        Card bonus = card();
        when(goofyCardService.configuredCardId()).thenReturn(Optional.of(bonus.getId()));

        assertThat(service.credit(userId, bonus, CardCollectionType.PHYSICAL, 0, 1)).isZero();

        // Pas même une lecture du solde : rien n'est dû.
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void anOrdinaryCardStillPays() {
        Card ordinary = card();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setTokens(0L);
        CardCollectionSettings settings = new CardCollectionSettings();
        when(goofyCardService.configuredCardId()).thenReturn(Optional.empty());
        when(calculator.settings()).thenReturn(settings);
        when(calculator.points(ordinary, CardCollectionType.PHYSICAL, 1, settings)).thenReturn(10L);
        when(calculator.points(ordinary, CardCollectionType.PHYSICAL, 0, settings)).thenReturn(0L);
        when(calculator.tokensFor(10L, settings)).thenReturn(5L);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThat(service.credit(userId, ordinary, CardCollectionType.PHYSICAL, 0, 1)).isEqualTo(5L);
        assertThat(user.getTokens()).isEqualTo(5L);
    }

    private Card card() {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte");
        return card;
    }
}
