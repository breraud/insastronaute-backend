package fr.insastronaute.webapi.challenge.auto.types.tokensbalance;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Hold N tokens at once". Declares no trigger on purpose: a balance moves from
 * half a dozen places, and unlocking an achievement grants tokens itself, which
 * would make the hot path re-enter. The hourly sweep covers it safely.
 */
@Component
public final class TokensBalanceChallengeType implements AutoChallengeType<TokensBalanceParameters> {

    private final UserRepository userRepository;

    public TokensBalanceChallengeType(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String key() {
        return "TOKENS_BALANCE";
    }

    @Override
    public String publicName() {
        return "Atteindre un solde de tokens";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur détient un nombre donné de tokens. Évalué au balayage horaire, pas à la seconde.";
    }

    @Override
    public Class<TokensBalanceParameters> parametersType() {
        return TokensBalanceParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("amount", "Solde de tokens", 1, 10_000_000));
    }

    @Override
    public String phrase(TokensBalanceParameters parameters) {
        return "Détiens " + parameters.amount() + " tokens en même temps";
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of();
    }

    @Override
    public AutoChallengeProgress progress(TokensBalanceParameters parameters, UUID userId) {
        long tokens = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."))
                .getTokens();
        return AutoChallengeProgress.of(tokens, parameters.amount());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(TokensBalanceParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = userRepository.findUsersWithTokenBalance(
                parameters.amount(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
