package fr.insastronaute.webapi.card.goofy;

import fr.insastronaute.webapi.card.domain.Card;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdminGoofyCardController lit le nom de la carte pour l'afficher, une fois la
 * lecture du dépôt terminée, donc hors session : un proxy paresseux y éclate —
 * LazyInitializationException sur GET /api/admin/cards/goofy, en production.
 *
 * <p>Tant qu'aucune carte n'était réglée, la référence était nulle et rien ne se
 * dépliait : la panne n'est apparue qu'au premier réglage. Ce test retient la
 * jointure.</p>
 */
@DataJpaTest
// La base par défaut du slice ignore le type jsonb que déclarent certaines tables.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GoofyCardSettingsFetchTest {

    @Autowired
    private GoofyCardSettingsRepository settingsRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void theSettingsComeBackWithTheirCardAlreadyLoaded() {
        settings(card("Carte goofy"));

        // Sans ce vidage, l'entité serait rendue depuis le cache de premier niveau,
        // déjà initialisée, et le test passerait sans rien prouver.
        entityManager.flush();
        entityManager.clear();

        Optional<GoofyCardSettings> found =
                settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID);

        assertThat(found).isPresent();
        assertThat(Hibernate.isInitialized(found.get().getCard()))
                .as("la carte doit arriver avec les réglages, pas sous forme de proxy")
                .isTrue();
        assertThat(found.get().getCard().getName()).isEqualTo("Carte goofy");
    }

    @Test
    void settingsWithoutACardStillComeBack() {
        // La jointure est une jointure externe : avant tout réglage, la ligne existe
        // sans carte, et la lire ne doit pas rendre vide.
        settings(null);

        entityManager.flush();
        entityManager.clear();

        Optional<GoofyCardSettings> found =
                settingsRepository.findWithCardById(GoofyCardSettings.SINGLETON_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getCard()).isNull();
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

    private void settings(Card card) {
        GoofyCardSettings settings = new GoofyCardSettings();
        settings.setId(GoofyCardSettings.SINGLETON_ID);
        settings.setCard(card);
        settings.setCode("ABCDE-FGHIJ-KLMNP");
        settings.setEnabled(card != null);
        entityManager.persist(settings);
    }
}
