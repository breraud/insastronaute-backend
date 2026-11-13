package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.goofy.GoofyCardSettings;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que « toute la collection » veut dire, des deux côtés du pourcentage.
 *
 * <p>Le dénominateur comptait jusqu'ici toutes les cartes activées : la carte par
 * défaut que tout le monde a d'office, les catégories cachées et de jeu qui ne se
 * collectionnent pas, et la carte bonus du code goofy, qui n'appartient à aucune
 * collection. Un défi à 100 % était donc inatteignable, et l'écran du joueur
 * affichait un autre total que le défi.</p>
 *
 * <p>Numérateur et dénominateur doivent bouger ensemble : ce test les mesure sur
 * le même catalogue.</p>
 */
@DataJpaTest
// La base par défaut du slice ignore le type jsonb que déclarent certaines tables.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CollectionMembershipCountTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardCollectionRepository collectionRepository;

    @Autowired
    private EntityManager entityManager;

    private CardCategory base;
    private Card baseCard;
    private Card extensionCard;
    private Card bonusCard;
    private AppUser collector;

    @BeforeEach
    void catalogue() {
        base = category("Commune", false, false, false);
        CardCategory extension = category("Extension", false, false, true);
        CardCategory hidden = category("Cachée", true, false, false);
        CardCategory game = category("Jeu", false, true, false);

        baseCard = card("Alpha", base);
        extensionCard = card("Beta", extension);
        card("Gamma", hidden);
        card("Delta", game);
        Card defaultCard = card("Dos de carte", base);
        defaultCard.setDefaultCard(true);
        Card disabled = card("Retirée", base);
        disabled.setEnabled(false);

        bonusCard = card("Goofy", base);
        GoofyCardSettings settings = new GoofyCardSettings();
        settings.setId(GoofyCardSettings.SINGLETON_ID);
        settings.setCard(bonusCard);
        settings.setCode("GOOFY-GOOFY-GOOFY");
        settings.setEnabled(true);
        entityManager.persist(settings);

        collector = user("sub-collectionneur");
    }

    @Test
    void thePersonalTotalKeepsTheExtensionsAndDropsTheRest() {
        flush();

        // Alpha et Beta. Ni la cachée, ni celle de jeu, ni la carte par défaut, ni
        // la retirée, ni la carte bonus.
        assertThat(cardRepository.countPersonalCollectionCards()).isEqualTo(2);
    }

    @Test
    void theTeamTotalDropsTheExtensionsToo() {
        flush();

        // La vue d'équipe n'affiche pas les extensions : un objectif mesuré sur
        // l'équipe ne doit pas rester ouvert sur des cartes qu'elle ne voit pas.
        assertThat(cardRepository.countTeamCollectionCards()).isEqualTo(1);
    }

    @Test
    void aCategoryTotalIgnoresTheBonusAndTheDefaultCard() {
        flush();

        // La catégorie « Commune » porte Alpha, le dos de carte, la retirée et la
        // carte bonus : seule Alpha se collectionne.
        assertThat(cardRepository.countCollectionCardsInCategory(base.getId())).isEqualTo(1);
    }

    @Test
    void ownedBonusCardsDoNotCountTowardsTheCollection() {
        collection(collector, baseCard, CardCollectionType.VIRTUAL, 1);
        collection(collector, bonusCard, CardCollectionType.VIRTUAL, 1);
        flush();

        assertThat(collectionRepository.countDistinctCardsForUsers(
                List.of(collector.getId()), CardCollectionType.VIRTUAL)).isEqualTo(1);
        assertThat(collectionRepository.countOwnedEnabledCards(
                collector.getId(), CardCollectionType.VIRTUAL)).isEqualTo(1);
    }

    @Test
    void theOwnedCountFollowsTheSameScopeAsTheTotal() {
        // Une extension possédée compte dans la collection personnelle et pas dans
        // celle de l'équipe, exactement comme dans le dénominateur : sinon le joueur
        // dépasse 100 % d'un côté ou n'y arrive jamais de l'autre.
        collection(collector, baseCard, CardCollectionType.PHYSICAL, 1);
        collection(collector, extensionCard, CardCollectionType.PHYSICAL, 1);
        collection(collector, baseCard, CardCollectionType.VIRTUAL, 1);
        collection(collector, extensionCard, CardCollectionType.VIRTUAL, 1);
        flush();

        assertThat(collectionRepository.countDistinctCardsForUsers(
                List.of(collector.getId()), CardCollectionType.VIRTUAL)).isEqualTo(2);
        assertThat(collectionRepository.countDistinctCardsForUsers(
                List.of(collector.getId()), CardCollectionType.PHYSICAL)).isEqualTo(1);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private CardCategory category(String name, boolean hidden, boolean game, boolean extension) {
        CardCategory category = new CardCategory();
        category.setName(name);
        category.setDisplayOrder(1);
        category.setHidden(hidden);
        category.setGame(game);
        category.setExtension(extension);
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
