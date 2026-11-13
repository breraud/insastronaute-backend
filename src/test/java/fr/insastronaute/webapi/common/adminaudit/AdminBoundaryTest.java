package fr.insastronaute.webapi.common.adminaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verrouille la frontiere entre un geste d'administrateur et une recompense
 * de jeu ordinaire.
 *
 * <p>CardService.addVirtualCards est partage : CardService.grantVirtualCardsByName
 * l'appelle, et StsPvpService appelle grantVirtualCardsByName pour les
 * recompenses de PvP. Toute la conception du registre repose sur le fait que
 * la journalisation vit chez l'appelant admin (AdminUserService) et jamais
 * dans CardService lui-meme.</p>
 *
 * <p>CardService connait desormais AdminActionJournal, pour le seul don
 * d'administration qu'il porte (grant, appele uniquement par
 * AdminCardController). Le journal cable ci-dessous est le vrai, construit
 * sur le depot events mocke : si un jour addVirtualCards ou
 * grantVirtualCardsByName se mettent a journaliser, l'appel atteint ce depot
 * et verify(events, never()).save(any()) tombe. L'assertion n'est donc plus
 * vraie par construction.</p>
 *
 * <p>Elle garde en plus sa vertu d'origine : le "new CardService(...)" a
 * arite fixe plus bas casse a la compilation des que le constructeur change.
 * Si tu es ici pour cette raison, cable events et journal - ne les remplace
 * pas par un mock(AdminActionJournal.class) frais rien que pour faire taire
 * le compilateur, ce serait rendre ce test vert et aveugle.</p>
 *
 * <p>Le meme chemin est verifie contre un vrai depot dans
 * {@link PvpRewardBoundaryTest} : ici la garde est structurelle, la-bas elle
 * est observee en base.</p>
 */
class AdminBoundaryTest {

    private CardRepository cardRepository;
    private CardCollectionRepository collectionRepository;
    private UserRepository userRepository;
    private AdminActionEventRepository events;
    private CardService cardService;
    private AppUser player;
    private Card card;

    @BeforeEach
    void setUp() {
        cardRepository = mock(CardRepository.class);
        collectionRepository = mock(CardCollectionRepository.class);
        userRepository = mock(UserRepository.class);
        events = mock(AdminActionEventRepository.class);

        // Le vrai journal, sur le depot mocke : toute ecriture depuis
        // CardService atterrit sur events, donc sous l'assertion du test.
        AdminActionJournal journal = new AdminActionJournal(events, userRepository, new ObjectMapper());

        cardService = new CardService(
                cardRepository,
                mock(CardCategoryRepository.class),
                mock(CardTypeRepository.class),
                collectionRepository,
                userRepository,
                mock(CardImageService.class),
                mock(CardTeamMemberRepository.class),
                mock(fr.insastronaute.webapi.cardteam.repository.CardTeamRepository.class),
                mock(AutoChallengeTriggerPublisher.class),
                mock(CollectionRewardService.class),
                mock(CollectionPointsCalculator.class),
                journal,
                mock(fr.insastronaute.webapi.card.service.CollectionTestModeService.class),
                mock(fr.insastronaute.webapi.card.goofy.GoofyCardService.class),
                mock(CardInstanceRepository.class),
                mock(CardPrintRunSlotRepository.class),
                mock(DeckRepository.class),
                mock(DropTierTemplateRepository.class)
        );

        player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub("sub-player");

        card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte Rare");

        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(cardRepository.findByNameIgnoreCase("Carte Rare")).thenReturn(Optional.of(card));
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(
                player.getId(), card.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(Optional.empty());
    }

    /**
     * Une recompense de jeu n'est pas un geste d'administrateur.
     *
     * <p>Le journal cable dans setUp() ecrit dans events : si
     * grantVirtualCardsByName - ou addVirtualCards sous lui - journalisait,
     * ce verify tomberait.</p>
     */
    @Test
    void aGameRewardWritesNothingInTheAdminJournal() {
        cardService.grantVirtualCardsByName(player.getId(), List.of("Carte Rare"));

        verify(events, never()).save(any());
    }
}
