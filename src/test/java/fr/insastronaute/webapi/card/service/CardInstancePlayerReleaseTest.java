package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.domain.CardInstanceReward;
import fr.insastronaute.webapi.card.dto.OwnedCardInstanceDto;
import fr.insastronaute.webapi.card.dto.ReleasedCardInstanceDto;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRewardRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un joueur rend a la circulation un exemplaire papier qu'il a en double, pour le
 * donner a quelqu'un qui le scannera a son tour.
 */
@ExtendWith(MockitoExtension.class)
class CardInstancePlayerReleaseTest {
    private static final String CODE = "K7M2Q-HX4PA-9RT3W";
    private static final String NOT_YOURS = "Cet exemplaire n'est pas dans ta collection.";

    @Mock private CardInstanceRepository instanceRepository;
    @Mock private CardRepository cardRepository;
    @Mock private CardCollectionRepository collectionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardPrintRunSlotRepository printRunSlotRepository;
    @Mock private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    @Mock private CollectionRewardService collectionRewardService;
    @Mock private AdminActionJournal journal;
    @Mock private GoofyCardService goofyCardService;
    @Mock private CardInstanceRewardRepository rewardRepository;

    @InjectMocks private CardInstanceService service;

    private final AppUser player = user("player-sub");
    private final Card card = card();

    @Test
    void releasesADuplicateSoTheNextScannerGetsIt() {
        CardInstance copy = instance(card, player, CODE);
        CardCollection collection = physicalCollection(player, card, 2);
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(copy));
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(instanceRepository.countByOwnerIdAndCardId(player.getId(), card.getId())).thenReturn(2L);
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(player.getId(), card.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.of(collection));

        // Le joueur recopie le code imprime : casse et espaces ne doivent pas compter.
        ReleasedCardInstanceDto released = service.releaseMine("player-sub", "  k7m2q-hx4pa-9rt3w ");

        assertThat(copy.getOwner()).isNull();
        assertThat(copy.getAssignedAt()).isNull();
        verify(instanceRepository).save(copy);
        assertThat(collection.getQuantity()).isEqualTo(1);
        assertThat(released).isEqualTo(new ReleasedCardInstanceDto(card.getId(), card.getName(), CODE, 1));
    }

    @Test
    void keepsTheLastCopy() {
        CardInstance copy = instance(card, player, CODE);
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(copy));
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(instanceRepository.countByOwnerIdAndCardId(player.getId(), card.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.releaseMine("player-sub", CODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tu ne peux libérer qu'un doublon : garde au moins un exemplaire de cette carte.");
        assertThat(copy.getOwner()).isSameAs(player);
        verify(instanceRepository, never()).save(any());
        verify(collectionRepository, never()).save(any());
        verify(collectionRepository, never()).delete(any());
    }

    @Test
    void answersAnotherPlayersCopyLikeAnUnknownCode() {
        // Distinguer « ce code existe mais n'est pas a toi » de « ce code n'existe pas »
        // laisserait sonder les codes imprimes des autres.
        CardInstance someoneElses = instance(card, user("other-sub"), CODE);
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(someoneElses));
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> service.releaseMine("player-sub", CODE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(NOT_YOURS);
        assertThat(someoneElses.getOwner()).isNotNull();
        verify(instanceRepository, never()).save(any());
    }

    @Test
    void answersAnUnknownCodeLikeAnotherPlayersCopy() {
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.empty());
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> service.releaseMine("player-sub", CODE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(NOT_YOURS);
    }

    @Test
    void answersAFreeCopyLikeAnUnknownCode() {
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(instance(card, null, CODE)));
        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> service.releaseMine("player-sub", CODE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(NOT_YOURS);
    }

    @Test
    void listsThePlayersOwnCopiesOfACard() {
        Instant first = Instant.parse("2026-09-01T10:00:00Z");
        Instant second = Instant.parse("2026-09-15T10:00:00Z");
        CardInstance older = instance(card, player, "AAAAA-BBBBB-CCCCC");
        older.setAssignedAt(first);
        CardInstance newer = instance(card, player, CODE);
        newer.setAssignedAt(second);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(instanceRepository.findByOwnerIdAndCardIdOrderByAssignedAtAsc(player.getId(), card.getId()))
                .thenReturn(List.of(older, newer));

        assertThat(service.findMine("player-sub", card.getId())).containsExactly(
                new OwnedCardInstanceDto("AAAAA-BBBBB-CCCCC", first),
                new OwnedCardInstanceDto(CODE, second));
    }

    @Test
    void paysTheFirstScanOfACopy() {
        CardInstance copy = instance(card, null, CODE);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(copy));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(player.getId(), card.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(copy.getId(), player.getId(), card.getId()))
                .thenReturn(false);

        service.claim("player-sub", CODE);

        verify(collectionRewardService).credit(player.getId(), card, CardCollectionType.PHYSICAL, 0, 1);
        ArgumentCaptor<CardInstanceReward> reward = ArgumentCaptor.forClass(CardInstanceReward.class);
        verify(rewardRepository).save(reward.capture());
        assertThat(reward.getValue().getCardInstanceId()).isEqualTo(copy.getId());
        assertThat(reward.getValue().getUserId()).isEqualTo(player.getId());
        assertThat(reward.getValue().getCardId()).isEqualTo(card.getId());
    }

    @Test
    void doesNotPayTwiceForTheSameCopy() {
        // Liberer puis rescanner son propre doublon, ou se renvoyer une carte a deux,
        // ne doit pas devenir une pompe a jetons : la carte revient, pas le paiement.
        CardInstance copy = instance(card, null, CODE);
        CardCollection collection = physicalCollection(player, card, 1);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(copy));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(player.getId(), card.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.of(collection));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(copy.getId(), player.getId(), card.getId()))
                .thenReturn(true);

        service.claim("player-sub", CODE);

        assertThat(copy.getOwner()).isSameAs(player);
        assertThat(collection.getQuantity()).isEqualTo(2);
        verify(collectionRewardService, never()).credit(any(), any(), any(), anyInt(), anyInt());
        verify(rewardRepository, never()).save(any());
    }

    @Test
    void stillCountsTheCardWhenTheCopyWasAlreadyPaid() {
        CardInstance copy = instance(card, null, CODE);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(instanceRepository.findByPublicIdForUpdate(CODE)).thenReturn(Optional.of(copy));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(player.getId(), card.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(copy.getId(), player.getId(), card.getId()))
                .thenReturn(true);

        service.claim("player-sub", CODE);

        ArgumentCaptor<CardCollection> saved = ArgumentCaptor.forClass(CardCollection.class);
        verify(collectionRepository).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(1);
        verify(collectionRewardService, never()).credit(any(), any(), any(), anyInt(), anyInt());
        verify(autoChallengeTriggerPublisher).publish(eq(player.getId()), any());
    }

    private static Card card() {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte");
        card.setCategory("Rare");
        card.setDefaultCard(false);
        return card;
    }

    private static AppUser user(String keycloakSub) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub(keycloakSub);
        user.setEmail(keycloakSub + "@example.com");
        return user;
    }

    private static CardInstance instance(Card card, AppUser owner, String publicId) {
        CardInstance instance = new CardInstance();
        instance.setId(UUID.randomUUID());
        instance.setCard(card);
        instance.setPublicId(publicId);
        instance.setOwner(owner);
        if (owner != null) {
            instance.setAssignedAt(Instant.now());
        }
        return instance;
    }

    private static CardCollection physicalCollection(AppUser owner, Card card, int quantity) {
        CardCollection collection = new CardCollection();
        collection.setUser(owner);
        collection.setCard(card);
        collection.setCollectionType(CardCollectionType.PHYSICAL);
        collection.setQuantity(quantity);
        return collection;
    }
}
