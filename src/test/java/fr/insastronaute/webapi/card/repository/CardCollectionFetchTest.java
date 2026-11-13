package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le calcul des points de collection déréférence la carte de chaque ligne. Il est
 * appelé depuis UserController.getProfile, qui n'est pas transactionnel : la session
 * s'y ferme avec l'appel au dépôt, et un proxy paresseux éclate juste après —
 * LazyInitializationException sur /api/users/profile, en production.
 *
 * <p>La variante d'équipe portait déjà la jointure ; la variante solo, restée une
 * méthode dérivée, ne la portait pas. Ce test retient la symétrie.
 */
@DataJpaTest
// La base par défaut du slice ignore le type jsonb que déclarent certaines tables.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CardCollectionFetchTest {

    @Autowired
    private CardCollectionRepository collectionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aCollectionComesBackWithItsCardAlreadyLoaded() {
        AppUser owner = user("sub-collectionneur");
        Card card = card("Carte de test");
        collection(owner, card, CardCollectionType.VIRTUAL, 3);

        // Sans ce vidage, les entités seraient rendues depuis le cache de premier
        // niveau, déjà initialisées, et le test passerait sans rien prouver.
        entityManager.flush();
        entityManager.clear();

        List<CardCollection> found = collectionRepository
                .findByUserIdAndCollectionType(owner.getId(), CardCollectionType.VIRTUAL);

        assertThat(found).hasSize(1);
        assertThat(Hibernate.isInitialized(found.getFirst().getCard()))
                .as("la carte doit arriver avec la collection, pas sous forme de proxy")
                .isTrue();
        assertThat(found.getFirst().getCard().getName()).isEqualTo("Carte de test");
    }

    @Test
    void anotherCollectionTypeIsNotReturned() {
        AppUser owner = user("sub-collectionneur");
        collection(owner, card("Physique"), CardCollectionType.PHYSICAL, 1);

        entityManager.flush();
        entityManager.clear();

        assertThat(collectionRepository
                .findByUserIdAndCollectionType(owner.getId(), CardCollectionType.VIRTUAL))
                .isEmpty();
    }

    private AppUser user(String subject) {
        AppUser user = new AppUser();
        user.setKeycloakSub(subject);
        user.setEmail(subject + "@example.com");
        user.setDisplayName("Collectionneur");
        entityManager.persist(user);
        return user;
    }

    private Card card(String name) {
        Card card = new Card();
        card.setName(name);
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
