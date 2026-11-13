package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pays the tokens a card acquisition earned.
 *
 * <p>The amount is the <em>difference</em> in collection points before and after
 * the stack grew, not the points of the stack: crediting the total every time
 * would pay a collector again for every card they already had. The difference
 * also makes the batch rule fall out for free — nine spare commons move the
 * total by nothing, the tenth moves it by one.
 *
 * <p>Nothing is backfilled. Collection points are computed on read, so existing
 * collections already count on the leaderboard; only acquisitions from here on
 * pay tokens.
 */
@Service
public class CollectionRewardService {

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionRewardService.class);

    private final CollectionPointsCalculator calculator;
    private final UserRepository userRepository;
    private final GoofyCardService goofyCardService;

    public CollectionRewardService(
            CollectionPointsCalculator calculator,
            UserRepository userRepository,
            GoofyCardService goofyCardService
    ) {
        this.calculator = calculator;
        this.userRepository = userRepository;
        this.goofyCardService = goofyCardService;
    }

    /**
     * @param quantityBefore copies held before this acquisition, 0 for a discovery
     * @param quantityAfter  copies held after it
     * @return tokens credited, 0 when the acquisition crossed no point threshold
     */
    public long credit(UUID userId, Card card, CardCollectionType type, int quantityBefore, int quantityAfter) {
        if (userId == null || card == null || quantityAfter <= quantityBefore) {
            return 0L;
        }

        // La carte bonus n'appartient a aucune collection : elle ne vaut donc aucun
        // point, et ne paie aucun jeton. Le joueur la recoit pour ce qu'elle est.
        if (goofyCardService.configuredCardId().filter(card.getId()::equals).isPresent()) {
            return 0L;
        }

        CardCollectionSettings settings = calculator.settings();
        long gained = calculator.points(card, type, quantityAfter, settings)
                - calculator.points(card, type, quantityBefore, settings);
        long tokens = calculator.tokensFor(gained, settings);
        if (tokens <= 0) {
            return 0L;
        }

        try {
            AppUser user = userRepository.findByIdForUpdate(userId).orElse(null);
            if (user == null) {
                return 0L;
            }
            user.setTokens(Math.addExact(user.getTokens(), tokens));
            long rewardEventPoints = user.creditEventPointsFor(fr.insastronaute.webapi.economy.domain.CurrencySource.COLLECTION_REWARD, tokens);
            currencyLedger.record(user, fr.insastronaute.webapi.economy.domain.CurrencySource.COLLECTION_REWARD, tokens, rewardEventPoints,
                    card == null ? null : card.getId());
            userRepository.save(user);
            return tokens;
        } catch (RuntimeException exception) {
            // The card is already in the collection; failing to pay for it must
            // not undo the acquisition itself.
            LOGGER.warn("Could not credit collection tokens to user '{}' for card '{}'.", userId, card.getId(), exception);
            return 0L;
        }
    }
}
