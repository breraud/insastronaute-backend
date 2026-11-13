package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que compte la portee d'un changement de bareme.
 *
 * <p>Une ligne du registre qui dit "PC de la rarete Legendaire : 50 -> 80"
 * sans dire si le geste a touche trois joueurs ou trois cents ne sert a rien.
 * Le nombre porte doit donc etre celui qu'un humain compterait : les joueurs
 * qui detiennent au moins une carte de la rarete, chacun une fois, quel que
 * soit le nombre de cartes ou d'exemplaires.</p>
 */
@DataJpaTest
// La base par defaut du slice ignore le type jsonb que declarent certaines tables.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CardCollectionScopeCountTest {

    @Autowired
    private CardCollectionRepository collectionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aHolderOfSeveralCardsOfTheRarityCountsOnce() {
        CardCategory rarity = category("Legendaire");
        CardCategory other = category("Commune");
        AppUser twoCards = user("sub-deux-cartes");
        AppUser oneCardTwice = user("sub-un-exemplaire-double");
        AppUser bothTypes = user("sub-virtuel-et-physique");
        AppUser elsewhere = user("sub-autre-rarete");

        collection(twoCards, card("Alpha", rarity), CardCollectionType.VIRTUAL, 1);
        collection(twoCards, card("Beta", rarity), CardCollectionType.VIRTUAL, 1);
        collection(oneCardTwice, card("Gamma", rarity), CardCollectionType.VIRTUAL, 4);
        Card shared = card("Delta", rarity);
        collection(bothTypes, shared, CardCollectionType.VIRTUAL, 1);
        collection(bothTypes, shared, CardCollectionType.PHYSICAL, 1);
        collection(elsewhere, card("Epsilon", other), CardCollectionType.VIRTUAL, 2);

        entityManager.flush();
        entityManager.clear();

        assertThat(collectionRepository.countHoldersOfCategory(rarity.getId())).isEqualTo(3);
        assertThat(collectionRepository.countHoldersOfCategory(other.getId())).isEqualTo(1);
    }

    /**
     * La portee d'un changement des reglages globaux : le multiplicateur et
     * les doublons physiques ne deplacent que le score physique.
     */
    @Test
    void onlyPhysicalHoldersCountForThePhysicalKnobs() {
        CardCategory rarity = category("Rare");
        AppUser physical = user("sub-physique");
        AppUser virtual = user("sub-virtuel");
        Card card = card("Zeta", rarity);

        collection(physical, card, CardCollectionType.PHYSICAL, 1);
        collection(virtual, card, CardCollectionType.VIRTUAL, 3);

        entityManager.flush();
        entityManager.clear();

        assertThat(collectionRepository.countHoldersOfCollectionType(CardCollectionType.PHYSICAL)).isEqualTo(1);
    }

    /**
     * Une ligne de collection a zero exemplaire n'est pas une detention : le
     * joueur n'a plus la carte, et un changement de bareme ne lui prend ni ne
     * lui rend rien. C'est ce que dit la clause {@code quantity > 0} des deux
     * requetes ; sans elle il serait compte comme touche, et la table du
     * registre etant en ajout seul, ce nombre faux resterait.
     *
     * <p>La ligne est ecrite en SQL natif, deliberement hors du domaine :
     * {@code CardCollection.setQuantity} ramene toute valeur inferieure a 1 a
     * 1, et Postgres porte en plus
     * {@code ck_card_collections_quantity_positive}. La clause est donc une
     * garde contre un etat que le schema interdit aujourd'hui - ce test dit ce
     * qu'elle ferait si cette garde tombait, il ne pretend pas qu'un tel etat
     * se produise.</p>
     */
    @Test
    void aRowWithNoCopiesLeftIsNotAHolder() {
        CardCategory rarity = category("Epique");
        AppUser stillHolding = user("sub-detient-encore");
        AppUser emptied = user("sub-plus-rien");
        Card card = card("Eta", rarity);
        collection(stillHolding, card, CardCollectionType.PHYSICAL, 1);

        entityManager.flush();
        emptyCollectionRow(emptied, card, CardCollectionType.PHYSICAL);
        entityManager.clear();

        assertThat(collectionRepository.countHoldersOfCategory(rarity.getId())).isEqualTo(1);
        assertThat(collectionRepository.countHoldersOfCollectionType(CardCollectionType.PHYSICAL)).isEqualTo(1);
    }

    /** Le seul chemin vers une quantite nulle : ni le setter ni Postgres ne l'acceptent. */
    private void emptyCollectionRow(AppUser owner, Card card, CardCollectionType type) {
        entityManager.createNativeQuery(
                        "insert into card_collections"
                                + " (id, user_id, card_id, collection_type, quantity, first_discovered_at, updated_at)"
                                + " values (?, ?, ?, ?, 0, current_timestamp, current_timestamp)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, owner.getId())
                .setParameter(3, card.getId())
                .setParameter(4, type.name())
                .executeUpdate();
    }

    private CardCategory category(String name) {
        CardCategory category = new CardCategory();
        category.setName(name);
        category.setDisplayOrder(1);
        entityManager.persist(category);
        return category;
    }

    private AppUser user(String subject) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName("Collectionneur");
        entityManager.persist(user);
        return user;
    }

    private Card card(String name, CardCategory category) {
        Card card = new Card();
        card.setName(name);
        card.setCategoryRef(category);
        card.setImageObjectKey("cards/" + name + ".png");
        card.setThumbnailObjectKey("cards/" + name + "-thumb.png");
        card.setImageUrl("https://example.test/" + name + ".png");
        card.setThumbnailUrl("https://example.test/" + name + "-thumb.png");
        card.setContentType("image/png");
        entityManager.persist(card);
        return card;
    }

    private void collection(AppUser owner, Card card, CardCollectionType type, int quantity) {
        CardCollection collection = new CardCollection();
        collection.setUser(owner);
        collection.setCard(card);
        collection.setCollectionType(type);
        collection.setQuantity(quantity);
        entityManager.persist(collection);
    }
}
