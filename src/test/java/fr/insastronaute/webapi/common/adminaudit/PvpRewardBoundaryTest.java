package fr.insastronaute.webapi.common.adminaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.deck.repository.DeckRepository;
import fr.insastronaute.webapi.deck.repository.DropTierTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.card.service.CardImageService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.card.service.CollectionPointsCalculator;
import fr.insastronaute.webapi.card.service.CollectionRewardService;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * La frontiere admin, observee en base plutot que deduite du cablage.
 *
 * <p>StsPvpService recompense une partie en appelant
 * CardService.grantVirtualCardsByName, qui descend sur addVirtualCards -
 * exactement la methode que AdminUserService.addCards utilise pour un don
 * d'administrateur. Le registre ne parle que des gestes d'administration : une
 * victoire en PvP ne doit rien y ecrire.</p>
 *
 * <p>Ici le CardService est monte avec les vrais depots du slice et un vrai
 * AdminActionJournal, donc une ligne ecrite par ce chemin serait reellement
 * lisible dans admin_action_events. {@link AdminBoundaryTest} garde la meme
 * regle par une casse de compilation ; ce test-ci l'observe.</p>
 */
@DataJpaTest
// La base par defaut du slice ignore le type jsonb que declarent certaines tables.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PvpRewardBoundaryTest {

    @Autowired
    private AdminActionEventRepository events;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardCategoryRepository categoryRepository;

    @Autowired
    private CardTypeRepository typeRepository;

    @Autowired
    private CardCollectionRepository collectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CardTeamMemberRepository teamMemberRepository;

    @Autowired
    private EntityManager entityManager;

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardService(
                cardRepository,
                categoryRepository,
                typeRepository,
                collectionRepository,
                userRepository,
                mock(CardImageService.class),
                teamMemberRepository,
                mock(fr.insastronaute.webapi.cardteam.repository.CardTeamRepository.class),
                mock(AutoChallengeTriggerPublisher.class),
                mock(CollectionRewardService.class),
                mock(CollectionPointsCalculator.class),
                new AdminActionJournal(events, userRepository, new ObjectMapper()),
                mock(fr.insastronaute.webapi.card.service.CollectionTestModeService.class),
                mock(fr.insastronaute.webapi.card.goofy.GoofyCardService.class),
                mock(CardInstanceRepository.class),
                mock(CardPrintRunSlotRepository.class),
                mock(DeckRepository.class),
                mock(DropTierTemplateRepository.class)
        );
    }

    @Test
    void aPvpRewardWritesNoLineInTheRegister() {
        AppUser player = player();
        card("Carte Rare");
        entityManager.flush();

        cardService.grantVirtualCardsByName(player.getId(), List.of("Carte Rare"));
        entityManager.flush();

        // La recompense a bien eu lieu : sans cette assertion, le registre
        // vide ne prouverait rien de plus qu'un chemin qui n'a rien fait.
        List<CardCollection> owned = collectionRepository
                .findByUserIdAndCollectionType(player.getId(), CardCollectionType.VIRTUAL);
        assertThat(owned).hasSize(1);
        assertThat(owned.getFirst().getCard().getName()).isEqualTo("Carte Rare");

        assertThat(events.findAll())
                .as("une recompense de PvP n'est pas un geste d'administration")
                .isEmpty();
    }

    private AppUser player() {
        AppUser user = new AppUser();
        user.setKeycloakSub("sub-joueur-pvp");
        user.setEmail("joueur-pvp@example.test");
        user.setDisplayName("Joueur PvP");
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
}
