package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.CardCollectionSettingsDto;
import fr.insastronaute.webapi.card.dto.CardCollectionSettingsRequest;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionSettingsRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Les reglages globaux qui decident de ce que vaut une collection.
 *
 * <p>La logique vivait dans {@code AdminCardCollectionSettingsController} ;
 * elle descend ici pour la meme raison que celle des tokens etait descendue
 * dans {@code AdminUserService} : le registre s'ecrit dans la transaction du
 * geste, et un geste qui touche N joueurs se relit mieux la ou il est ecrit
 * qu'au bord de l'API. Le comportement est deplace, pas reecrit.</p>
 */
@Service
public class CardCollectionSettingsService {

    private final CardCollectionSettingsRepository repository;
    private final CardCollectionRepository collectionRepository;
    private final AdminActionJournal journal;

    public CardCollectionSettingsService(
            CardCollectionSettingsRepository repository,
            CardCollectionRepository collectionRepository,
            AdminActionJournal journal
    ) {
        this.repository = repository;
        this.collectionRepository = collectionRepository;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public CardCollectionSettingsDto get() {
        return toDto(current());
    }

    /**
     * Ces trois reglages sont les regles qui decident de ce que vaut une
     * collection : les changer deplace ce que vaut la collection de tous les
     * detenteurs concernes d'un seul geste, d'ou une ligne de portee globale.
     *
     * <p>Un enregistrement qui ne change aucune des trois valeurs n'ecrit
     * rien. Et quand seul le paiement par point a bouge, la ligne est ecrite
     * avec {@code affectedPlayers} a zero : ce reglage ne fixe que le prix des
     * gains a venir, il ne deplace aucun score deja acquis.</p>
     */
    @Transactional
    public CardCollectionSettingsDto update(CardCollectionSettingsRequest request, String actorKeycloakSub) {
        CardCollectionSettings settings = current();
        Integer previousMultiplier = settings.getPhysicalFirstAcquisitionMultiplier();
        Boolean previousDuplicatesEnabled = settings.getPhysicalDuplicatesEnabled();
        Integer previousPayoutPerPoint = settings.getTokensPerPoint();

        settings.setPhysicalFirstAcquisitionMultiplier(request.physicalFirstAcquisitionMultiplier());
        settings.setPhysicalDuplicatesEnabled(request.physicalDuplicatesEnabled());
        settings.setTokensPerPoint(request.tokensPerPoint());
        CardCollectionSettingsDto dto = toDto(repository.save(settings));

        journalScaleChange(actorKeycloakSub, settings, previousMultiplier, previousDuplicatesEnabled, previousPayoutPerPoint);
        return dto;
    }

    private void journalScaleChange(
            String actorKeycloakSub,
            CardCollectionSettings settings,
            Integer previousMultiplier,
            Boolean previousDuplicatesEnabled,
            Integer previousPayoutPerPoint
    ) {
        // Les deux seuls reglages qui deplacent un score deja acquis. Le
        // paiement par point, lui, n'est lu qu'au moment du gain par
        // CollectionPointsCalculator.tokensFor : le changer ne bouge rien de
        // ce qui est deja au compteur.
        boolean physicalScoringChanged =
                !Objects.equals(previousMultiplier, settings.getPhysicalFirstAcquisitionMultiplier())
                        || !Objects.equals(previousDuplicatesEnabled, settings.getPhysicalDuplicatesEnabled());
        boolean changed = physicalScoringChanged
                || !Objects.equals(previousPayoutPerPoint, settings.getTokensPerPoint());
        if (!changed) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "COLLECTION_SETTINGS");
        details.put("fromPhysicalFirstAcquisitionMultiplier", previousMultiplier);
        details.put("toPhysicalFirstAcquisitionMultiplier", settings.getPhysicalFirstAcquisitionMultiplier());
        details.put("fromPhysicalDuplicatesEnabled", previousDuplicatesEnabled);
        details.put("toPhysicalDuplicatesEnabled", settings.getPhysicalDuplicatesEnabled());
        // Le reglage s'appelle tokensPerPoint dans le domaine, mais la garde du
        // journal refuse toute cle contenant "token" - AdminUserService ecrit
        // deja "delta" et "balance" pour la meme raison. La cle dit ce qu'elle
        // porte : ce qu'un point de collection paie.
        details.put("fromPayoutPerPoint", previousPayoutPerPoint);
        details.put("toPayoutPerPoint", settings.getTokensPerPoint());
        // Le multiplicateur physique et les doublons physiques ne deplacent que
        // le score physique : la portee du geste, ce sont les detenteurs de
        // cartes physiques. Quand seul le paiement par point a bouge, aucun
        // score deja acquis ne suit, et la table etant en ajout seul, un nombre
        // faux ecrit ici le reste pour toujours : la reponse honnete est zero,
        // et le comptage n'a pas lieu.
        details.put("affectedPlayers", physicalScoringChanged
                ? collectionRepository.countHoldersOfCollectionType(CardCollectionType.PHYSICAL)
                : 0L);
        journal.recordGlobal(actorKeycloakSub, AdminActionType.COLLECTION_SCALE_CHANGED, details);
    }

    /** The row is seeded by migration; recreating it keeps a fresh database usable. */
    private CardCollectionSettings current() {
        return repository.findById(CardCollectionSettings.SINGLETON_ID)
                .orElseGet(CardCollectionSettings::new);
    }

    private CardCollectionSettingsDto toDto(CardCollectionSettings settings) {
        return new CardCollectionSettingsDto(
                settings.getPhysicalFirstAcquisitionMultiplier(),
                Boolean.TRUE.equals(settings.getPhysicalDuplicatesEnabled()),
                settings.getTokensPerPoint(),
                settings.getUpdatedAt()
        );
    }
}
