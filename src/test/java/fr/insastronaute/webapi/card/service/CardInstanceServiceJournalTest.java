package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.dto.CardInstanceCreateRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceUpdateRequest;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardInstanceServiceJournalTest {

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
    private CardInstanceService service;

    private Card card;
    private AppUser player;
    private CardInstance instance;

    @BeforeEach
    void setUp() {
        card = card(UUID.randomUUID());
        player = user(UUID.randomUUID(), "player-sub");
        instance = instance(card, null);
    }

    @Test
    void assigningAnInstanceWritesAnAssignedEventWithItsPublicId() {
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.assign(instance.getId(), player.getId(), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED), details.capture());
        assertThat(details.getValue()).isEqualTo(
                Map.of("publicId", instance.getPublicId(), "cardName", card.getName()));
    }

    @Test
    void assigningToTheCurrentOwnerWritesNothing() {
        instance.setOwner(player);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));

        service.assign(instance.getId(), player.getId(), "sub-admin");

        verify(journal, never()).record(any(), any(), any(), any());
    }

    @Test
    void assigningToANewOwnerAlsoWritesAReleasedEventForThePreviousOwner() {
        AppUser previousOwner = user(UUID.randomUUID(), "previous-sub");
        instance.setOwner(previousOwner);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.assign(instance.getId(), player.getId(), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(previousOwner.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
    }

    @Test
    void releasingAnInstanceWritesAReleasedEventNamingTheFormerOwner() {
        instance.setOwner(player);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.release(instance.getId(), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
    }

    @Test
    void releasingAnUnassignedInstanceWritesNothing() {
        instance.setOwner(null);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));

        service.release(instance.getId(), "sub-admin");

        verify(journal, never()).record(any(), any(), any(), any());
    }

    @Test
    void updateAdminAssigningANewOwnerWritesAnAssignedEvent() {
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(null, null, player.getId(), false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
        verify(journal, never()).record(any(), any(), eq(AdminActionType.PHYSICAL_CARD_RELEASED), any());
    }

    @Test
    void updateAdminRemovingTheOwnerWritesAReleasedEvent() {
        instance.setOwner(player);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(null, null, null, true), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
        verify(journal, never()).record(any(), any(), eq(AdminActionType.PHYSICAL_CARD_ASSIGNED), any());
    }

    /**
     * Repointer une instance detenue vers une autre carte n'est ni une
     * attribution ni un retrait, mais le proprietaire perd bien une carte et
     * en gagne une autre. fromCardPoints/toCardPoints/cardPointsDelta portent
     * les valeurs nominales des deux cartes, pas le mouvement reel du score
     * de collection du proprietaire.
     */
    @Test
    void updateAdminChangingTheCardWithoutChangingTheOwnerWritesASwappedEvent() {
        instance.setOwner(player);
        card.setFirstAcquisitionPoints(3);
        Card nextCard = card(UUID.randomUUID());
        nextCard.setName("Carte suivante");
        nextCard.setFirstAcquisitionPoints(10);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(cardRepository.findById(nextCard.getId())).thenReturn(Optional.of(nextCard));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(nextCard.getId(), null, null, false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_SWAPPED), details.capture());
        assertThat(details.getValue())
                .containsEntry("publicId", instance.getPublicId())
                .containsEntry("fromCardId", card.getId().toString())
                .containsEntry("toCardId", nextCard.getId().toString())
                .containsEntry("fromCardName", "Carte")
                .containsEntry("toCardName", "Carte suivante")
                .containsEntry("fromCardPoints", 3)
                .containsEntry("toCardPoints", 10)
                .containsEntry("cardPointsDelta", 7);
        // Le type est une relation qui peut manquer : la cle reste, sa valeur
        // est nulle, et rien ne doit exploser pour autant.
        assertThat(details.getValue()).containsKeys("fromType", "toType");
        verify(journal, never()).record(any(), any(), eq(AdminActionType.PHYSICAL_CARD_ASSIGNED), any());
        verify(journal, never()).record(any(), any(), eq(AdminActionType.PHYSICAL_CARD_RELEASED), any());
    }

    /**
     * Une carte de jeu ne vaut jamais de points de premiere acquisition, quel
     * que soit ce que porte la carte elle-meme :
     * {@link Card#getEffectiveFirstAcquisitionPoints()} rend 0 avant meme de
     * regarder la valeur propre a la carte. Le repointage doit donc porter des
     * zeros des deux cotes plutot que les valeurs nominales.
     */
    @Test
    void updateAdminSwappingToAndFromGameCardsWritesZeroPoints() {
        instance.setOwner(player);
        card.setCategoryRef(gameCategory());
        card.setFirstAcquisitionPoints(3);
        Card nextCard = card(UUID.randomUUID());
        nextCard.setName("Carte suivante");
        nextCard.setCategoryRef(gameCategory());
        nextCard.setFirstAcquisitionPoints(10);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(cardRepository.findById(nextCard.getId())).thenReturn(Optional.of(nextCard));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(nextCard.getId(), null, null, false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_SWAPPED), details.capture());
        assertThat(details.getValue())
                .containsEntry("fromCardPoints", 0)
                .containsEntry("toCardPoints", 0)
                .containsEntry("cardPointsDelta", 0);
    }

    /**
     * Le type de carte tel qu'il est reellement expose : une relation vers
     * CardType, dont on garde le nom.
     */
    @Test
    void aSwapNamesTheCardTypesWhenTheCardsCarryOne() {
        instance.setOwner(player);
        card.setTypeRef(cardType("Holographique"));
        Card nextCard = card(UUID.randomUUID());
        nextCard.setTypeRef(cardType("Mat"));
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(cardRepository.findById(nextCard.getId())).thenReturn(Optional.of(nextCard));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(nextCard.getId(), null, null, false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_SWAPPED), details.capture());
        assertThat(details.getValue())
                .containsEntry("fromType", "Holographique")
                .containsEntry("toType", "Mat");
    }

    /**
     * Une instance libre qui change de carte ne touche personne : il n'y a
     * aucun joueur a nommer, donc rien a ecrire.
     */
    @Test
    void updateAdminChangingTheCardOfAnUnownedInstanceWritesNothing() {
        instance.setOwner(null);
        Card nextCard = card(UUID.randomUUID());
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(cardRepository.findById(nextCard.getId())).thenReturn(Optional.of(nextCard));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(nextCard.getId(), null, null, false), "sub-admin");

        verify(journal, never()).record(any(), any(), any(), any());
    }

    /**
     * Changer la carte et le proprietaire d'un meme geste reste un transfert :
     * les deux cotes s'ecrivent, et rien ne s'ajoute pour l'echange.
     */
    @Test
    void updateAdminChangingBothTheCardAndTheOwnerWritesNoSwappedEvent() {
        AppUser previousOwner = user(UUID.randomUUID(), "previous-sub");
        instance.setOwner(previousOwner);
        Card nextCard = card(UUID.randomUUID());
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(cardRepository.findById(nextCard.getId())).thenReturn(Optional.of(nextCard));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(nextCard.getId(), null, player.getId(), false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(previousOwner.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED), any());
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED), any());
        verify(journal, never()).record(any(), any(), eq(AdminActionType.PHYSICAL_CARD_SWAPPED), any());
    }

    @Test
    void creatingAnInstanceWithoutAnOwnerWritesNothing() {
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(instanceRepository.existsByPublicId(anyString())).thenReturn(false);
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAdmin(new CardInstanceCreateRequest(card.getId(), null, null), "sub-admin");

        verify(journal, never()).record(any(), any(), any(), any());
    }

    @Test
    void creatingAnInstanceWithAnOwnerWritesAnAssignedEvent() {
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(instanceRepository.existsByPublicId(anyString())).thenReturn(false);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<CardInstance> savedInstance = ArgumentCaptor.forClass(CardInstance.class);
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);

        service.createAdmin(new CardInstanceCreateRequest(card.getId(), null, player.getId()), "sub-admin");

        verify(instanceRepository).save(savedInstance.capture());
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED), details.capture());
        assertThat(details.getValue()).isEqualTo(
                Map.of("publicId", savedInstance.getValue().getPublicId(), "cardName", card.getName()));
    }

    /**
     * Transfert de proprietaire a proprietaire depuis l'ecran instances.
     *
     * <p>assign a deja son test ; updateAdmin est l'autre chemin de transfert,
     * et il doit ecrire les deux lignes sur deux cibles distinctes - une seule
     * ligne laisserait un cote du transfert invisible.</p>
     */
    @Test
    void updateAdminTransferringFromOneOwnerToAnotherWritesBothSides() {
        AppUser previousOwner = user(UUID.randomUUID(), "previous-sub");
        instance.setOwner(previousOwner);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(null, null, player.getId(), false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(previousOwner.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
    }

    /**
     * Renommer et transferer d'un meme geste : l'ancien proprietaire n'a
     * jamais tenu le nouveau code, sa ligne doit nommer l'ancien.
     */
    @Test
    void updateAdminRenamingWhileTransferringNamesTheCodeTheFormerOwnerHeld() {
        AppUser previousOwner = user(UUID.randomUUID(), "previous-sub");
        instance.setOwner(previousOwner);
        String heldPublicId = instance.getPublicId();
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));
        when(instanceRepository.existsByPublicIdAndIdNot("ZZZZZ-YYYYY-XXXXX", instance.getId())).thenReturn(false);
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(instanceRepository.save(any(CardInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printRunSlotRepository.findByCardInstanceId(instance.getId())).thenReturn(Optional.empty());

        service.updateAdmin(instance.getId(),
                new CardInstanceUpdateRequest(null, "ZZZZZ-YYYYY-XXXXX", player.getId(), false), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(previousOwner.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED),
                eq(Map.of("publicId", heldPublicId, "cardName", card.getName())));
        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_ASSIGNED),
                eq(Map.of("publicId", "ZZZZZ-YYYYY-XXXXX", "cardName", card.getName())));
    }

    /**
     * Supprimer une instance detenue retire la carte a son proprietaire :
     * c'est un retrait, et il s'ecrit avant que la ligne ne disparaisse.
     */
    @Test
    void deletingAnOwnedInstanceWritesAReleasedEvent() {
        instance.setOwner(player);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));

        service.delete(instance.getId(), "sub-admin");

        verify(journal).record(eq("sub-admin"), eq(player.getId()),
                eq(AdminActionType.PHYSICAL_CARD_RELEASED),
                eq(Map.of("publicId", instance.getPublicId(), "cardName", card.getName())));
        verify(instanceRepository).delete(instance);
    }

    @Test
    void deletingAnUnassignedInstanceWritesNothing() {
        instance.setOwner(null);
        when(instanceRepository.findByIdForUpdate(instance.getId())).thenReturn(Optional.of(instance));

        service.delete(instance.getId(), "sub-admin");

        verify(journal, never()).record(any(), any(), any(), any());
        verify(instanceRepository).delete(instance);
    }

    private Card card(UUID id) {
        Card card = new Card();
        card.setId(id);
        card.setName("Carte");
        card.setCategory("Rare");
        card.setDefaultCard(false);
        return card;
    }

    private fr.insastronaute.webapi.card.domain.CardType cardType(String name) {
        fr.insastronaute.webapi.card.domain.CardType type = new fr.insastronaute.webapi.card.domain.CardType();
        type.setName(name);
        return type;
    }

    private CardCategory gameCategory() {
        CardCategory category = new CardCategory();
        category.setName("Jeu");
        category.setGame(true);
        return category;
    }

    private AppUser user(UUID id, String keycloakSub) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        user.setEmail(keycloakSub + "@example.com");
        return user;
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
