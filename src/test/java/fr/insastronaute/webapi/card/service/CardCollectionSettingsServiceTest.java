package fr.insastronaute.webapi.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.CardCollectionSettingsRequest;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionSettingsRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les reglages globaux sont les regles qui decident de ce que vaut une
 * collection : les changer deplace le score de tous les detenteurs concernes.
 */
@ExtendWith(MockitoExtension.class)
class CardCollectionSettingsServiceTest {

    private static final String ADMIN_SUB = "sub-admin";

    @Mock
    private CardCollectionSettingsRepository settingsRepository;

    @Mock
    private CardCollectionRepository collectionRepository;

    @Mock
    private AdminActionEventRepository events;

    @Mock
    private UserRepository users;

    private CardCollectionSettingsService settingsService;

    /** Le vrai journal sur un depot mocke : toute ecriture devient observable. */
    @BeforeEach
    void setUp() {
        AppUser admin = new AppUser();
        admin.setId(UUID.randomUUID());
        admin.setKeycloakSub(ADMIN_SUB);
        lenient().when(users.findByKeycloakSub(ADMIN_SUB)).thenReturn(Optional.of(admin));

        settingsService = new CardCollectionSettingsService(
                settingsRepository,
                collectionRepository,
                new AdminActionJournal(events, users, new ObjectMapper())
        );
    }

    @Test
    void changingTheGlobalSettingsWritesOneGlobalRowNamingTheirScope() {
        CardCollectionSettings settings = settings(2, false, 1);
        when(settingsRepository.findById(CardCollectionSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);
        when(collectionRepository.countHoldersOfCollectionType(CardCollectionType.PHYSICAL)).thenReturn(120L);

        settingsService.update(new CardCollectionSettingsRequest(3, true, 2), ADMIN_SUB);

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(events).save(captor.capture());
        AdminActionEvent saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AdminActionType.COLLECTION_SCALE_CHANGED);
        assertThat(saved.getTarget()).isNull();
        assertThat(saved.getDetailsJson())
                .contains("\"scope\":\"COLLECTION_SETTINGS\"")
                .contains("\"fromPhysicalFirstAcquisitionMultiplier\":2")
                .contains("\"toPhysicalFirstAcquisitionMultiplier\":3")
                .contains("\"fromPhysicalDuplicatesEnabled\":false")
                .contains("\"toPhysicalDuplicatesEnabled\":true")
                .contains("\"fromPayoutPerPoint\":1")
                .contains("\"toPayoutPerPoint\":2")
                .contains("\"affectedPlayers\":120");
    }

    /**
     * Le paiement par point n'est lu qu'au moment du gain : le changer ne
     * deplace aucun score deja acquis, donc il ne touche personne.
     *
     * <p>La table est en ajout seul : une ligne qui annoncerait ici quelques
     * centaines de joueurs touches le dirait pour toujours, sans rien qui
     * puisse la corriger. Zero, et pas de comptage du tout.</p>
     */
    @Test
    void changingOnlyThePayoutRecordsNobodyAffectedAndDoesNotCount() {
        CardCollectionSettings settings = settings(2, true, 1);
        when(settingsRepository.findById(CardCollectionSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);
        // Un compte non nul disponible : sans la garde, la ligne l'ecrirait, et
        // les deux moities de ce test tomberaient au lieu d'une seule.
        lenient().when(collectionRepository.countHoldersOfCollectionType(any())).thenReturn(120L);

        settingsService.update(new CardCollectionSettingsRequest(2, true, 5), ADMIN_SUB);

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getDetailsJson())
                .contains("\"fromPayoutPerPoint\":1")
                .contains("\"toPayoutPerPoint\":5")
                .contains("\"affectedPlayers\":0");
        verify(collectionRepository, never()).countHoldersOfCollectionType(any());
    }

    /**
     * Le multiplicateur physique, lui, deplace un score deja acquis : le
     * comptage a lieu, meme quand le paiement par point n'a pas bouge.
     */
    @Test
    void changingAPhysicalScoringKnobStillCountsTheHolders() {
        CardCollectionSettings settings = settings(2, true, 1);
        when(settingsRepository.findById(CardCollectionSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);
        when(collectionRepository.countHoldersOfCollectionType(CardCollectionType.PHYSICAL)).thenReturn(42L);

        settingsService.update(new CardCollectionSettingsRequest(3, true, 1), ADMIN_SUB);

        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getDetailsJson()).contains("\"affectedPlayers\":42");
    }

    /** Un enregistrement qui ne deplace rien ne laisse pas de ligne. */
    @Test
    void reSavingTheSameSettingsWritesNothing() {
        CardCollectionSettings settings = settings(2, true, 1);
        when(settingsRepository.findById(CardCollectionSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        settingsService.update(new CardCollectionSettingsRequest(2, true, 1), ADMIN_SUB);

        verify(events, never()).save(any());
        verify(collectionRepository, never()).countHoldersOfCollectionType(any());
    }

    private static CardCollectionSettings settings(int multiplier, boolean duplicatesEnabled, int payoutPerPoint) {
        CardCollectionSettings settings = new CardCollectionSettings();
        settings.setPhysicalFirstAcquisitionMultiplier(multiplier);
        settings.setPhysicalDuplicatesEnabled(duplicatesEnabled);
        settings.setTokensPerPoint(payoutPerPoint);
        return settings;
    }
}
