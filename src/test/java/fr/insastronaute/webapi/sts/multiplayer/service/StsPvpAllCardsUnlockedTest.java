package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDeckCategoryDto;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.card.service.CollectionTestModeService;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le PvP lit la collection servie, pas la collection réelle — sauf quand il
 * décide d'une récompense.
 *
 * C'est toute la distinction du mode de test : il ouvre l'usage sans rien
 * donner. Les confondre ferait croire à l'attribution de fin de run que tout est
 * acquis, et elle ne donnerait plus rien, en silence.
 */
class StsPvpAllCardsUnlockedTest {

    private final StsPvpProfileRepository profileRepository = mock(StsPvpProfileRepository.class);
    private final CardService cardService = mock(CardService.class);
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);
    private final CollectionTestModeService collectionTestMode = mock(CollectionTestModeService.class);

    private final StsPvpService service = new StsPvpService(
            profileRepository,
            mock(StsPvpDeckRepository.class),
            mock(StsPvpQueueEntryRepository.class),
            mock(StsPvpChallengeRepository.class),
            mock(StsPvpBattleRepository.class),
            mock(StsPvpNotificationRepository.class),
            mock(UserRepository.class),
            mock(fr.insastronaute.webapi.friend.repository.FriendshipRepository.class),
            mock(DepartmentRepository.class),
            cardService,
            gameDataService,
            mock(StsRunRepository.class),
            new ObjectMapper(),
            mock(StsPvpCombatService.class),
            mock(StsPvpCombatBroadcaster.class),
            collectionTestMode);

    @Test
    void buildingADeckReadsTheCollectionAsItIsServed() {
        // Servie ouverte par le mode de test, « Comète » devient jouable sans
        // que le PvP ait à connaître la règle.
        when(cardService.getDeck(eq("player-sub"), eq(CardCollectionType.VIRTUAL)))
                .thenReturn(deck(card("Fusée", 1), card("Comète", 1)));
        when(collectionTestMode.allCardsOwned()).thenReturn(true);
        when(gameDataService.latestVersion()).thenReturn("v1");
        when(gameDataService.cards("v1")).thenReturn(new ObjectMapper().createObjectNode());

        var collection = service.getCollection(player());

        assertThat(collection.ownedCardIds()).contains("Fusée", "Comète");
        // Le drapeau accompagne la liste : sans lui, le joueur croirait la
        // collection acquise.
        assertThat(collection.allCardsUnlocked()).isTrue();
        verify(cardService).getDeck("player-sub", CardCollectionType.VIRTUAL);
    }

    @Test
    void aClosedModeChangesNothingToWhatIsServed() {
        when(cardService.getDeck(eq("player-sub"), eq(CardCollectionType.VIRTUAL)))
                .thenReturn(deck(card("Fusée", 1), card("Comète", 0)));
        when(collectionTestMode.allCardsOwned()).thenReturn(false);
        when(gameDataService.latestVersion()).thenReturn("v1");
        when(gameDataService.cards("v1")).thenReturn(new ObjectMapper().createObjectNode());

        var collection = service.getCollection(player());

        assertThat(collection.ownedCardIds()).contains("Fusée").doesNotContain("Comète");
        assertThat(collection.allCardsUnlocked()).isFalse();
    }

    private AppUser player() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        StsPvpProfile profile = new StsPvpProfile();
        profile.setUser(user);
        profile.setSelectedCharacter("IRONCLAD");
        when(profileRepository.findByUser_KeycloakSub("player-sub")).thenReturn(Optional.of(profile));
        return user;
    }

    private static CardDeckDto deck(CardDeckCardDto... cards) {
        return new CardDeckDto(
                1, cards.length,
                List.of(new CardDeckCategoryDto(UUID.randomUUID(), "Commune", null, null, 1, cards.length, List.of(cards))),
                fr.insastronaute.webapi.card.domain.CardCollectionScope.PERSONAL,
                null, null
        );
    }

    private static CardDeckCardDto card(String name, int quantity) {
        return new CardDeckCardDto(
                UUID.randomUUID(), name, UUID.randomUUID(), "Commune", false, quantity, 0,
                "https://s3.example.com/" + name + ".jpg", "https://s3.example.com/" + name + "-thumb.jpg"
        );
    }
}
