package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionSettingsRepository;
import org.springframework.stereotype.Component;

/**
 * The single definition of what a stack of cards is worth in collection points.
 *
 * <p>Everything that shows, ranks, or pays PC goes through here. Duplicating the
 * formula is how a leaderboard and a token balance start disagreeing.
 */
@Component
public class CollectionPointsCalculator {

    private final CardCollectionSettingsRepository settingsRepository;

    public CollectionPointsCalculator(CardCollectionSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public CardCollectionSettings settings() {
        return settingsRepository.findById(CardCollectionSettings.SINGLETON_ID)
                .orElseGet(CardCollectionSettings::new);
    }

    public long points(CardCollection collection) {
        return collection == null ? 0L : points(collection.getCard(), collection.getCollectionType(),
                collection.getQuantity() == null ? 0 : collection.getQuantity(), settings());
    }

    public long points(CardCollection collection, CardCollectionSettings settings) {
        return collection == null ? 0L : points(collection.getCard(), collection.getCollectionType(),
                collection.getQuantity() == null ? 0 : collection.getQuantity(), settings);
    }

    /**
     * @param quantity total copies held, first one included
     */
    public long points(Card card, CardCollectionType type, int quantity, CardCollectionSettings settings) {
        if (card == null || quantity <= 0) {
            return 0L;
        }
        // A caller passing null settings (or a mocked calculator in a test) gets
        // the defaults rather than an NPE deep in a scoring loop.
        CardCollectionSettings resolved = settings == null ? new CardCollectionSettings() : settings;

        boolean physical = type == CardCollectionType.PHYSICAL;
        long firstAcquisition = card.getEffectiveFirstAcquisitionPoints();
        if (physical) {
            firstAcquisition *= resolved.getPhysicalFirstAcquisitionMultiplier();
        }

        // A physical duplicate pays nothing by default: owning the object is the
        // reward, and stacking printed copies is not a collection achievement.
        if (physical && !Boolean.TRUE.equals(resolved.getPhysicalDuplicatesEnabled())) {
            return firstAcquisition;
        }

        int perPoint = Math.max(1, card.getEffectiveDuplicatesPerPoint());
        // Integer division on purpose: nine spare commons are worth nothing until
        // the tenth completes the batch.
        long batches = (long) (quantity - 1) / perPoint;
        return firstAcquisition + batches * card.getEffectiveDuplicatePoints();
    }

    /** Tokens owed for a move from one point total to another. Never negative. */
    public long tokensFor(long pointsGained, CardCollectionSettings settings) {
        CardCollectionSettings resolved = settings == null ? new CardCollectionSettings() : settings;
        return pointsGained <= 0 ? 0L : pointsGained * resolved.getTokensPerPoint();
    }
}
