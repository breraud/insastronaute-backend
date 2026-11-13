package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.service.CollectionRewardService;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.dto.AdminCardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardInstanceCreateRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardInstanceUpdateRequest;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardInstanceServiceTest {

    @Mock
    private CardInstanceRepository instanceRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardCollectionRepository collectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CardPrintRunSlotRepository printRunSlotRepository;

    @Mock
    private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    @Mock
    private CollectionRewardService collectionRewardService;

    @Mock
    private AdminActionJournal journal;

    @Mock
    private GoofyCardService goofyCardService;

    @Mock
    private fr.insastronaute.webapi.card.repository.CardInstanceRewardRepository rewardRepository;

    @InjectMocks
    private CardInstanceService cardInstanceService;

    // ── Fabriquer des cartes physiques laisse une trace ──────────────────────

    /**
     * Une ligne par lot, pas mille par clic.
     *
     * <p>`createBatch` fabrique jusqu'a mille exemplaires d'un coup. Une ligne par
     * exemplaire rendrait le registre illisible le jour ou l'on en aurait besoin ; une
     * ligne par lot, portant la plage de codes, se relit.</p>
     */
    @Test
    void aBatchOfPhysicalCardsIsRecordedAsOneLine() {
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(instanceRepository.existsByPublicId(anyString())).thenReturn(false);
        when(instanceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        cardInstanceService.createBatch(cardId, 3, "sub-admin");

        ArgumentCaptor<java.util.Map<String, Object>> details = ArgumentCaptor.forClass(java.util.Map.class);
        verify(journal).recordGlobal(
                eq("sub-admin"),
                eq(AdminActionType.PHYSICAL_CARDS_CREATED),
                details.capture());
        assertThat(details.getValue())
                .containsEntry("quantity", 3)
                .containsEntry("cardName", card.getName())
                .containsKeys("firstPublicId", "lastPublicId");
    }

    /**
     * Une serie d'impression melange les cartes : la ligne compte, elle ne nomme pas.
     *
     * <p>Les nommer toutes ferait une ligne illisible ; n'en nommer qu'une mentirait sur
     * ce qui a ete produit.</p>
     */
    @Test
    void aMixedPrintRunCountsTheCardsRatherThanNamingOne() {
        Card first = card(UUID.randomUUID());
        Card second = card(UUID.randomUUID());
        second.setName("Une autre carte");
        when(instanceRepository.existsByPublicId(anyString())).thenReturn(false);
        when(instanceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        cardInstanceService.createUnassignedInstances(java.util.List.of(first, second), "sub-admin");

        ArgumentCaptor<java.util.Map<String, Object>> details = ArgumentCaptor.forClass(java.util.Map.class);
        verify(journal).recordGlobal(any(), eq(AdminActionType.PHYSICAL_CARDS_CREATED), details.capture());
        assertThat(details.getValue())
                .containsEntry("cardCount", 2)
                .doesNotContainKey("cardName");
    }

    @Test
    void createsInstanceWithReadablePublicId() {
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(instanceRepository.existsByPublicId(anyString())).thenReturn(false);
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardInstanceDto instance = cardInstanceService.create(cardId, "sub-admin");

        assertThat(instance.cardId()).isEqualTo(cardId);
        assertThat(instance.publicId()).matches("[A-NP-Z1-9]{5}-[A-NP-Z1-9]{5}-[A-NP-Z1-9]{5}");
        assertThat(instance.publicId()).doesNotContain("0", "O");
    }

    @Test
    void adminCreateCanSetPublicIdAndOwner() {
        AppUser owner = user(UUID.randomUUID(), "owner-sub");
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId);

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(instanceRepository.existsByPublicId("12345-6789A-BCDEF")).thenReturn(false);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(owner.getId(), cardId, CardCollectionType.PHYSICAL))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCardInstanceDto created = cardInstanceService.createAdmin(
                new CardInstanceCreateRequest(card.getId(), "12345 6789A BCDEF", owner.getId()), "admin-sub"
        );

        ArgumentCaptor<CardCollection> collectionCaptor = ArgumentCaptor.forClass(CardCollection.class);
        verify(collectionRepository).save(collectionCaptor.capture());
        assertThat(created.cardId()).isEqualTo(card.getId());
        assertThat(created.publicId()).isEqualTo("12345-6789A-BCDEF");
        assertThat(created.ownerUserId()).isEqualTo(owner.getId());
        assertThat(created.assignedAt()).isNotNull();
        assertThat(collectionCaptor.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void claimAssignsInstanceAndIncrementsPhysicalCollection() {
        AppUser user = user(UUID.randomUUID(), "player-sub");
        Card card = card(UUID.randomUUID());
        CardInstance instance = instance(card, null);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(instanceRepository.findByPublicIdForUpdate("ABCDE-FGHJK-MNPQR")).thenReturn(Optional.of(instance));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(user.getId(), card.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.empty());
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardInstanceDto claimed = cardInstanceService.claim("player-sub", "abcde-fghjk-mnpqr");

        ArgumentCaptor<CardCollection> collectionCaptor = ArgumentCaptor.forClass(CardCollection.class);
        verify(collectionRepository).save(collectionCaptor.capture());
        assertThat(claimed.ownerUserId()).isEqualTo(user.getId());
        assertThat(claimed.assignedAt()).isNotNull();
        assertThat(collectionCaptor.getValue().getCollectionType()).isEqualTo(CardCollectionType.PHYSICAL);
        assertThat(collectionCaptor.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void claimRejectsInstanceOwnedByAnotherUser() {
        AppUser user = user(UUID.randomUUID(), "player-sub");
        AppUser owner = user(UUID.randomUUID(), "other-sub");
        CardInstance instance = instance(card(UUID.randomUUID()), owner);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(instanceRepository.findByPublicIdForUpdate("ABCDE-FGHJK-MNPQR")).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> cardInstanceService.claim("player-sub", "ABCDE-FGHJK-MNPQR"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cette carte physique est déjà attribuée.");
    }

    @Test
    void releaseRemovesLastPhysicalCollectionEntry() {
        AppUser owner = user(UUID.randomUUID(), "player-sub");
        Card card = card(UUID.randomUUID());
        CardInstance instance = instance(card, owner);
        CardCollection collection = collection(owner, card);

        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(owner.getId(), card.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.of(collection));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardInstanceDto released = cardInstanceService.release(instance.getId(), "admin-sub");

        verify(collectionRepository).delete(collection);
        assertThat(released.ownerUserId()).isNull();
        assertThat(released.assignedAt()).isNull();
    }

    @Test
    void assignsAndReleasesSelectedPhysicalInstancesForOneUser() {
        AppUser user = user(UUID.randomUUID(), "player-sub");
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId);
        CardInstance instance = instance(card, null);
        CardCollection collection = collection(user, card);
        AtomicInteger collectionLookups = new AtomicInteger();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(user.getId(), cardId, CardCollectionType.PHYSICAL))
                .thenAnswer(ignored -> collectionLookups.getAndIncrement() == 0 ? Optional.empty() : Optional.of(collection));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cardInstanceService.assignAvailableToUser(user.getId(), List.of(instance.getId()));
        cardInstanceService.releaseOwnedByUser(user.getId(), List.of(instance.getId()));

        assertThat(instance.getOwner()).isNull();
        assertThat(instance.getAssignedAt()).isNull();
        verify(collectionRepository).delete(collection);
    }

    /**
     * Une selection re-soumise peut contenir des cartes que le joueur detient
     * deja : elles sont passees sans erreur, et le retour ne doit citer que
     * celles qui ont reellement change de main - c'est lui qui decide des
     * lignes ecrites au registre.
     */
    @Test
    void assignReturnsOnlyTheInstancesItActuallyMoved() {
        AppUser user = user(UUID.randomUUID(), "player-sub");
        Card card = card(UUID.randomUUID());
        CardInstance alreadyHeld = instance(card, user);
        CardInstance available = instance(card, null);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(instanceRepository.findByIdForUpdate(alreadyHeld.getId())).thenReturn(Optional.of(alreadyHeld));
        when(instanceRepository.findByIdForUpdate(available.getId())).thenReturn(Optional.of(available));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(
                user.getId(), card.getId(), CardCollectionType.PHYSICAL)).thenReturn(Optional.empty());
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CardInstanceService.MovedInstance> moved = cardInstanceService.assignAvailableToUser(
                user.getId(), List.of(alreadyHeld.getId(), available.getId()));

        assertThat(moved).containsExactly(
                new CardInstanceService.MovedInstance(available.getId(), "Carte"));
    }

    /**
     * Le retrait n'a pas la meme forme : rien n'y est passe en silence, une
     * instance qui n'appartient pas au joueur fait echouer tout le geste. Le
     * retour vaut donc toujours la selection entiere.
     */
    @Test
    void releaseReturnsEveryInstanceOfTheSelection() {
        AppUser user = user(UUID.randomUUID(), "player-sub");
        Card card = card(UUID.randomUUID());
        CardInstance first = instance(card, user);
        CardInstance second = instance(card, user);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(instanceRepository.findByIdForUpdate(first.getId())).thenReturn(Optional.of(first));
        when(instanceRepository.findByIdForUpdate(second.getId())).thenReturn(Optional.of(second));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(
                user.getId(), card.getId(), CardCollectionType.PHYSICAL)).thenReturn(Optional.empty());
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CardInstanceService.MovedInstance> moved = cardInstanceService.releaseOwnedByUser(
                user.getId(), List.of(first.getId(), second.getId()));

        assertThat(moved).containsExactly(
                new CardInstanceService.MovedInstance(first.getId(), "Carte"),
                new CardInstanceService.MovedInstance(second.getId(), "Carte"));
    }

    @Test
    void adminUpdateCanReassignAndChangeCardWhileKeepingPhysicalCollectionConsistent() {
        AppUser previousOwner = user(UUID.randomUUID(), "previous-sub");
        AppUser nextOwner = user(UUID.randomUUID(), "next-sub");
        Card previousCard = card(UUID.randomUUID());
        UUID nextCardId = UUID.randomUUID();
        Card nextCard = card(nextCardId);
        nextCard.setName("Carte 2");
        nextCard.setDisplayOrder(7);
        CardInstance instance = instance(previousCard, previousOwner);
        CardCollection previousCollection = collection(previousOwner, previousCard);

        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(cardRepository.findById(nextCardId)).thenReturn(Optional.of(nextCard));
        when(userRepository.findById(nextOwner.getId())).thenReturn(Optional.of(nextOwner));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(previousOwner.getId(), previousCard.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(Optional.of(previousCollection));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(nextOwner.getId(), nextCardId, CardCollectionType.PHYSICAL))
                .thenReturn(Optional.empty());
        when(instanceRepository.existsByPublicIdAndIdNot("12345-6789A-BCDEF", instance.getId())).thenReturn(false);
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        AdminCardInstanceDto updated = cardInstanceService.updateAdmin(
                instance.getId(),
                new CardInstanceUpdateRequest(nextCardId, "12345-6789A-BCDEF", nextOwner.getId(), false),
                "admin-sub"
        );

        verify(collectionRepository).delete(previousCollection);
        ArgumentCaptor<CardCollection> collectionCaptor = ArgumentCaptor.forClass(CardCollection.class);
        verify(collectionRepository).save(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getUser()).isEqualTo(nextOwner);
        assertThat(collectionCaptor.getValue().getCard()).isEqualTo(nextCard);
        assertThat(updated.cardId()).isEqualTo(nextCardId);
        assertThat(updated.number()).isEqualTo(7);
        assertThat(updated.publicId()).isEqualTo("12345-6789A-BCDEF");
        assertThat(updated.ownerUserId()).isEqualTo(nextOwner.getId());
    }

    @Test
    void refuseCreationManuelleAvecCodeGoofy() {
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId);
        String goofyCode = "12345-6789A-BCDEF";

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(goofyCardService.matchesCode(goofyCode)).thenReturn(true);

        assertThatThrownBy(() -> cardInstanceService.createAdmin(
                new CardInstanceCreateRequest(cardId, goofyCode, null), "admin-sub"
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cet identifiant est réservé pour la carte goofy.");
    }

    @Test
    void refuseModificationManuelleAvecCodeGoofy() {
        Card card = card(UUID.randomUUID());
        CardInstance instance = instance(card, null);
        String goofyCode = "12345-6789A-BCDEF";

        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(goofyCardService.matchesCode(goofyCode)).thenReturn(true);

        assertThatThrownBy(() -> cardInstanceService.updateAdmin(
                instance.getId(),
                new CardInstanceUpdateRequest(null, goofyCode, null, false),
                "admin-sub"
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cet identifiant est réservé pour la carte goofy.");
    }

    @Test
    void generationAutomatiqueIgnoreCodeGoofy() {
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(instanceRepository.existsByPublicId(anyString())).thenReturn(false);
        when(goofyCardService.matchesCode(anyString())).thenReturn(true, false);
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCardInstanceDto created = cardInstanceService.createAdmin(
                new CardInstanceCreateRequest(cardId, null, null), "admin-sub"
        );

        assertThat(created.publicId()).isNotNull();
        verify(goofyCardService, org.mockito.Mockito.atLeast(2)).matchesCode(anyString());
    }

    private Card card(UUID id) {
        Card card = new Card();
        card.setId(id);
        card.setName("Carte");
        card.setCategory("Rare");
        card.setDefaultCard(false);
        return card;
    }

    private AppUser user(UUID id, String keycloakSub) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        user.setEmail(keycloakSub + "@example.com");
        return user;
    }

    private CardCollection collection(AppUser owner, Card card) {
        CardCollection collection = new CardCollection();
        collection.setUser(owner);
        collection.setCard(card);
        collection.setCollectionType(CardCollectionType.PHYSICAL);
        collection.setQuantity(1);
        return collection;
    }

    private CardInstance instance(Card card, AppUser owner) {
        CardInstance instance = new CardInstance();
        instance.setId(UUID.randomUUID());
        instance.setCard(card);
        instance.setPublicId("ABCDE-FGHJK-MNPQR");
        instance.setOwner(owner);
        if (owner != null) {
            instance.setAssignedAt(java.time.Instant.now());
        }
        return instance;
    }
}
