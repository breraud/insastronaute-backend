package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The PvP card unlocks a run has earned, granted by the transition that ended the run.
 *
 * <p>They used to be claimed through an endpoint, at a moment the client picked. These pin the two
 * things that arrangement got wrong: a run could be asked to pay out before it had earned anything,
 * and the asking consumed its one and only chance to pay out later.
 */
@ExtendWith(MockitoExtension.class)
class StsPvpRunEndUnlockTest {

    private static final String DATA_VERSION = "2026.7.0";
    private static final String CATALOG = """
            {"cards":[
              {"id":"katana","cardName":"Frappe au katana","favoredCharacter":"EP"},
              {"id":"frappe_thermique","cardName":"Frappe thermique","favoredCharacter":"EP"},
              {"id":"barriere","cardName":"Barrière d'énergie","favoredCharacter":"EP"},
              {"id":"revision","cardName":"Révision","favoredCharacter":"ITI"},
              {"id":"vieux_code","cardName":"Vieux code","favoredCharacter":"EP","collectionCardId":"legacy_card"},
              {"id":"sans_secret","cardName":"Sans","favoredCharacter":"EP","tags":["Secret"],
               "collectionCardId":"/iticbi1/cards/sans.jpg"},
              {"id":"adam_a","cardName":"Frappe secrète","favoredCharacter":"Aucun","tags":["Secret"],
               "collectionCardId":"/iticbi1/cards/adam.jpg"},
              {"id":"brehan_a","cardName":"Propagande Linux","favoredCharacter":"Aucun","pveOnly":true,
               "tags":["Secret"],"collectionCardId":"/iticbi1/cards/brehan.jpg"},
              {"id":"neutre","cardName":"Carte neutre","favoredCharacter":"Aucun"}
            ]}""";

    private final ObjectMapper json = new ObjectMapper();

    @Mock private CardService cardService;
    @Mock private StsGameDataService gameDataService;
    @Mock private StsPvpProfileRepository profileRepository;
    // Injecté par type : sans ce mock, le champ reste nul et l'appel casse.
    @Mock private fr.insastronaute.webapi.card.service.CollectionTestModeService collectionTestMode;
    // Réel plutôt que mocké : createDefaultProfile en attend un qui construise vraiment des
    // nœuds JSON, pas une réponse programmée.
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private StsPvpService service;

    // Every path that actually grants something reads and writes the profile's unlock record.
    private void hasNoUnlocksYet() {
        when(profileRepository.findByUserKeycloakSubForUpdate(anyString())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void exemptCategories(String... categoryNames) {
        when(gameDataService.latestVersion()).thenReturn(DATA_VERSION);
        ObjectNode rules = json.createObjectNode();
        ArrayNode array = rules.putArray("runEndUnlockableCategories");
        for (String categoryName : categoryNames) {
            array.add(categoryName);
        }
        when(gameDataService.pvpDeckRules(DATA_VERSION)).thenReturn(rules);
    }

    @Test
    void retreatUnlocksACollectionLinkedCardInAnExemptCategory() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setRunInventory(deckOf("vieux_code"));
        catalogueIsReadable();
        hasNoUnlocksYet();
        exemptCategories("Retiré");
        ownsNothingYet();
        when(cardService.categoryNamesByCardName()).thenReturn(java.util.Map.of("legacy_card", "Retiré"));

        service.grantRunEndUnlocks(run);

        assertThat(granted()).containsExactly("legacy_card");
    }

    @Test
    void retreatSkipsACollectionLinkedCardOutsideAnExemptCategory() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setRunInventory(deckOf("vieux_code"));
        catalogueIsReadable();
        hasNoUnlocksYet();
        exemptCategories("Une autre catégorie");
        ownsNothingYet();
        when(cardService.categoryNamesByCardName()).thenReturn(java.util.Map.of("legacy_card", "Retiré"));

        service.grantRunEndUnlocks(run);

        verify(cardService, never()).grantVirtualCardsByName(any(), anyList());
        assertThat(run.isPvpUnlockClaimed())
                .as("a card that still has to be legitimately obtained must not burn the run's one-shot flag")
                .isFalse();
    }

    /**
     * Une carte secrete illustree par un chemin se deverrouille comme une carte sans lien.
     *
     * <p>Les cartes secretes empruntent les images troll du theme iticbi1 : leur
     * {@code collectionCardId} est un chemin de fichier, pas une carte de collection. Rien ne se
     * gagne a cet endroit-la, donc rien n'a a y etre gagne d'abord -- exactement comme une carte
     * dont la categorie est declaree jamais obtenable.
     *
     * <p>Aucune categorie exemptee n'est configuree ici, et c'est le point : le lien n'est pas
     * exempte, il n'existe pas. Sans cela ces cartes seraient injouables en duel pour toujours,
     * en silence, puisqu'un lien de collection introuvable les ecartait du tirage.
     */
    @Test
    void aSecretCardIllustratedByAPathUnlocksLikeAnUnobtainableOne() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setRunInventory(deckOf("sans_secret"));
        catalogueIsReadable();
        hasNoUnlocksYet();
        ownsNothingYet();

        service.grantRunEndUnlocks(run);

        // Nommee par son nom de carte, pas par le chemin de son image : « /iticbi1/cards/sans.jpg »
        // inscrit dans le profil ne se rapprocherait d'aucune carte et ne s'afficherait nulle part.
        assertThat(granted()).containsExactly("Sans");
    }

    /**
     * Une carte secrete telle qu'elle est reellement ecrite : neutre, illustree par un chemin.
     *
     * <p>Les cartes secretes sont favorisees par « Aucun » -- elles se jouent avec n'importe quel
     * personnage, et l'ecran de deck PvP les propose a tous. Le tirage de fin de run exigeait
     * pourtant l'egalite stricte avec le personnage de la run, si bien qu'aucune d'elles ne
     * pouvait sortir : retirer une run avec des cartes secretes en main ne deverrouillait rien.
     */
    @Test
    void retreatUnlocksTheNeutralSecretCardsTheRunCarried() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setRunInventory(deckOf("adam_a"));
        catalogueIsReadable();
        hasNoUnlocksYet();
        ownsNothingYet();

        service.grantRunEndUnlocks(run);

        assertThat(granted()).containsExactly("Frappe secrète");
    }

    /** Une carte PvE seulement n'entre pas dans un deck de duel, secrete ou non. */
    @Test
    void aPveOnlySecretCardIsStillNeverUnlocked() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setRunInventory(deckOf("brehan_a"));
        catalogueIsReadable();
        hasNoUnlocksYet();
        ownsNothingYet();

        service.grantRunEndUnlocks(run);

        verify(cardService, never()).grantVirtualCardsByName(any(), anyList());
    }

    /**
     * Neutre veut dire « favorisee par personne », pas « favorisee par tous les autres ».
     *
     * <p>La carte d'un autre personnage reste ecartee : c'est ce que la regle d'origine voulait
     * dire, et l'ouvrir aux cartes neutres ne doit pas l'ouvrir a celles-la.
     */
    @Test
    void aCardFavoredByAnotherCharacterIsStillNeverUnlocked() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setRunInventory(deckOf("revision", "neutre"));
        catalogueIsReadable();
        hasNoUnlocksYet();
        ownsNothingYet();

        service.grantRunEndUnlocks(run);

        // « Révision » est ITI et la run est EP.
        assertThat(granted()).containsExactly("Carte neutre");
    }

    /** Le chemin se distingue du nom par sa forme seule, sans drapeau a maintenir. */
    @Test
    void anArtworkPathIsToldApartFromACollectionCardName() {
        assertThat(StsPvpService.isArtworkPath("/iticbi1/cards/sans.jpg")).isTrue();
        assertThat(StsPvpService.isArtworkPath("iticbi1/cards/sans.jpg")).isTrue();
        assertThat(StsPvpService.isArtworkPath("sans.jpg")).isTrue();
        assertThat(StsPvpService.isArtworkPath("bulldog.jpeg")).isTrue();
        assertThat(StsPvpService.isArtworkPath("https://cdn.example.com/sans.jpg")).isTrue();

        assertThat(StsPvpService.isArtworkPath("legacy_card")).isFalse();
        assertThat(StsPvpService.isArtworkPath("Adam")).isFalse();
        assertThat(StsPvpService.isArtworkPath("Phillipe Echebest")).isFalse();
        assertThat(StsPvpService.isArtworkPath(null)).isFalse();
        assertThat(StsPvpService.isArtworkPath("   ")).isFalse();
    }

    @Test
    void retreatGrantsTheWholeDeck() {
        StsRun run = run(StsRunStatus.Retired, 1);
        catalogueIsReadable();
        ownsNothingYet();
        hasNoUnlocksYet();

        service.grantRunEndUnlocks(run);

        // Only 3 EP cards sit in this run's deck: the whole deck, not a formula-limited draw.
        assertThat(granted()).hasSize(3);
        assertThat(run.isPvpUnlockClaimed()).isTrue();
    }

    @Test
    void abandoningPastTheFirstActGrantsASingleCard() {
        StsRun run = run(StsRunStatus.Abandoned, 1);
        catalogueIsReadable();
        ownsNothingYet();
        hasNoUnlocksYet();

        service.grantRunEndUnlocks(run);

        assertThat(granted()).hasSize(1);
        assertThat(run.isPvpUnlockClaimed()).isTrue();
    }

    /**
     * A game over hands over the whole deck, exactly like a retreat.
     *
     * <p>It used to draw a single card, which made collecting a character's cards a matter of luck
     * spread over many deaths.
     */
    @Test
    void defeatGrantsTheWholeDeckLikeARetreat() {
        StsRun run = run(StsRunStatus.Failed, 2);
        catalogueIsReadable();
        ownsNothingYet();
        hasNoUnlocksYet();

        service.grantRunEndUnlocks(run);

        assertThat(granted())
                .containsExactlyInAnyOrder("Frappe au katana", "Frappe thermique", "Barrière d'énergie");
        assertThat(run.isPvpUnlockClaimed()).isTrue();
    }

    /** Dying before crossing an act used to grant nothing; a game over no longer depends on depth. */
    @Test
    void defeatDuringTheFirstActStillGrantsTheWholeDeck() {
        StsRun run = run(StsRunStatus.Failed, 0);
        catalogueIsReadable();
        ownsNothingYet();
        hasNoUnlocksYet();

        service.grantRunEndUnlocks(run);

        assertThat(granted()).hasSize(3);
    }

    @Test
    void whatIsGrantedIsDrawnFromTheRunsOwnDeckAndMatchesItsCharacter() {
        StsRun run = run(StsRunStatus.Failed, 2);
        catalogueIsReadable();
        ownsNothingYet();
        hasNoUnlocksYet();

        service.grantRunEndUnlocks(run);

        // "Révision" is an ITI card and the run is EP: it must never come out of the draw.
        assertThat(granted())
                .isSubsetOf("Frappe au katana", "Frappe thermique", "Barrière d'énergie");
    }

    @Test
    void aRunStillBeingPlayedGrantsNothingAndKeepsItsChance() {
        StsRun run = run(StsRunStatus.Active, 3);

        service.grantRunEndUnlocks(run);

        verify(cardService, never()).grantVirtualCardsByName(any(), anyList());
        assertThat(run.isPvpUnlockClaimed())
                .as("an unearned payout must not burn the run's one-shot flag")
                .isFalse();
    }

    @Test
    void abandoningDuringTheFirstActGrantsNothingAndKeepsItsChance() {
        StsRun run = run(StsRunStatus.Abandoned, 0);

        service.grantRunEndUnlocks(run);

        verify(cardService, never()).grantVirtualCardsByName(any(), anyList());
        assertThat(run.isPvpUnlockClaimed()).isFalse();
    }

    @Test
    void aRunThatAlreadyPaidOutNeverPaysTwice() {
        StsRun run = run(StsRunStatus.Retired, 3);
        run.setPvpUnlockClaimed(true);

        service.grantRunEndUnlocks(run);

        verify(cardService, never()).grantVirtualCardsByName(any(), anyList());
    }

    @Test
    void aRunWithoutAnOwnerIsIgnoredRatherThanThrowing() {
        StsRun run = run(StsRunStatus.Retired, 1);
        run.setOwner(null);

        service.grantRunEndUnlocks(run);

        verify(cardService, never()).grantVirtualCardsByName(any(), anyList());
    }

    private void catalogueIsReadable() {
        when(gameDataService.cards(DATA_VERSION)).thenReturn(readCatalog());
    }

    private void ownsNothingYet() {
        // L'attribution lit la collection RÉELLE, pas celle que le mode de test
        // ouvre : autrement elle croirait tout acquis et ne donnerait plus rien.
        when(cardService.getOwnedDeck(anyString(), any())).thenReturn(new CardDeckDto(0, 0, List.of()));
    }

    @SuppressWarnings("unchecked")
    private List<String> granted() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardService).grantVirtualCardsByName(any(), captor.capture());
        return captor.getValue();
    }

    private JsonNode readCatalog() {
        try {
            return json.readTree(CATALOG);
        } catch (Exception unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private StsRun run(StsRunStatus status, int act) {
        AppUser owner = new AppUser();
        owner.setId(UUID.randomUUID());
        owner.setKeycloakSub("sub-" + UUID.randomUUID());

        StsRun run = new StsRun();
        run.setOwner(owner);
        run.setStatus(status);
        run.setAct(act);
        run.setDataVersion(DATA_VERSION);
        run.setSelectedCharacter("EP");
        run.setRunInventory(deckOf("katana", "frappe_thermique", "barriere", "revision"));
        return run;
    }

    private JsonNode deckOf(String... cardIds) {
        var inventory = json.createObjectNode();
        var deck = inventory.putArray("deck");
        for (String cardId : cardIds) {
            deck.addObject().put("cardId", cardId);
        }
        return inventory;
    }
}
