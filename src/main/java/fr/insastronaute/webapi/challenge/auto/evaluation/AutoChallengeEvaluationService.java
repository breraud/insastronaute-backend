package fr.insastronaute.webapi.challenge.auto.evaluation;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.award.AutoChallengeAwardService;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.auto.configuration.AutoChallengeProperties;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AutoChallengeEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoChallengeEvaluationService.class);

    private final ChallengeRepository challengeRepository;
    private final AutoChallengeTypeRegistry registry;
    private final AutoChallengeAwardService awardService;
    private final ChallengeService challengeService;
    private final AutoChallengeProperties properties;

    public AutoChallengeEvaluationService(
            ChallengeRepository challengeRepository,
            AutoChallengeTypeRegistry registry,
            AutoChallengeAwardService awardService,
            ChallengeService challengeService,
            AutoChallengeProperties properties
    ) {
        this.challengeRepository = challengeRepository;
        this.registry = registry;
        this.awardService = awardService;
        this.challengeService = challengeService;
        this.properties = properties;
    }

    public AutoChallengeEvaluationSummary evaluateAll() {
        Instant startedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        Instant cutoff = startedAt.minus(Duration.ofHours(3));
        List<Challenge> challenges = challengeRepository.findActiveAutomaticChallenges(startedAt, cutoff);
        int awardedUsers = 0;
        int skippedChallenges = 0;
        int errors = 0;

        for (Challenge challenge : challenges) {
            ParsedChallenge parsed;
            try {
                AutoChallengeType<?> type = registry.require(challenge.getAutoChallengeType());
                Object parameters = registry.parseAndValidate(
                        challenge.getAutoChallengeType(), challenge.getAutoChallengeParameters()
                );
                parsed = new ParsedChallenge(type, parameters);
            } catch (BadRequestException | IllegalArgumentException exception) {
                skippedChallenges++;
                logFailure("Skipping invalid automatic challenge configuration", challenge, exception);
                continue;
            }

            EvaluationResult result = evaluate(challenge, parsed);
            awardedUsers += result.awardedUsers();
            errors += result.failed() ? 1 : 0;
        }

        return new AutoChallengeEvaluationSummary(
                challenges.size(), awardedUsers, skippedChallenges, errors,
                Duration.between(startedAt, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow())
        );
    }

    /**
     * Rattrape un défi qui vient d'être écrit.
     *
     * <p>Le balayage passe toutes les heures et les déclencheurs ne parlent que du
     * moment présent : une phrase publiée sur une condition déjà remplie — « avoir
     * un ami » quand on en a deux — n'attrapait personne avant le prochain passage.
     * Une heure de silence après avoir créé un défi ressemble à un défi cassé.</p>
     *
     * <p>Silencieux en cas d'échec : le défi est enregistré, et une erreur ici ne
     * doit pas faire passer cet enregistrement pour un échec. Le balayage horaire
     * repassera.</p>
     */
    public int evaluateOne(UUID challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null || !"AUTO".equals(challenge.getValidationMode())) {
            return 0;
        }
        // Un défi enregistré n'est pas un défi ouvert : programmé pour demain, ou
        // encore brouillon, il n'a rien à rattraper. Le portail d'attribution le
        // refuserait de toute façon ; inutile de parcourir tous les joueurs pour ça.
        boolean open;
        try {
            open = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.withRealTime(
                    () -> challengeService.isOpenForAutomaticAward(challenge));
        } catch (RuntimeException exception) {
            logFailure("Immediate automatic challenge evaluation failed", challenge, exception);
            return 0;
        }
        if (!open) {
            return 0;
        }
        try {
            AutoChallengeType<?> type = registry.require(challenge.getAutoChallengeType());
            Object parameters = registry.parseAndValidate(
                    challenge.getAutoChallengeType(), challenge.getAutoChallengeParameters()
            );
            return evaluate(challenge, new ParsedChallenge(type, parameters)).awardedUsers();
        } catch (RuntimeException exception) {
            logFailure("Immediate automatic challenge evaluation failed", challenge, exception);
            return 0;
        }
    }

    private EvaluationResult evaluate(Challenge challenge, ParsedChallenge parsed) {
        int awardedUsers = 0;
        boolean failed = false;
        UUID cursor = null;
        try {
            do {
                AutoChallengeCandidatePage page = findCandidates(parsed.type(), parsed.parameters(), challenge.getId(), cursor);
                UUID nextCursor = page.nextCursor();
                if (nextCursor != null && cursor != null && nextCursor.compareTo(cursor) <= 0) {
                    throw new IllegalStateException("Automatic challenge type returned a non-advancing cursor");
                }
                if (!page.userIds().isEmpty()) {
                    awardedUsers += awardService.awardBatch(challenge.getId(), page.userIds());
                }
                cursor = nextCursor;
            } while (cursor != null);
        } catch (RuntimeException exception) {
            logFailure("Automatic challenge evaluation failed", challenge, exception);
            failed = true;
        }
        if (awardedUsers > 0) {
            try {
                challengeService.recalculateChallengePoints();
            } catch (RuntimeException exception) {
                logFailure("Automatic challenge point recalculation failed", challenge, exception);
                failed = true;
            }
        }
        return new EvaluationResult(awardedUsers, failed);
    }

    @SuppressWarnings("unchecked")
    private <P> AutoChallengeCandidatePage findCandidates(
            AutoChallengeType<?> type, Object parameters, UUID challengeId, UUID cursor
    ) {
        return ((AutoChallengeType<P>) type).findCandidates(
                (P) parameters, new AutoChallengeCandidateQuery(challengeId, cursor, properties.getBatchSize())
        );
    }

    private void logFailure(String message, Challenge challenge, RuntimeException exception) {
        LOGGER.error(
                "{}: challengeId={}, type={}",
                message, challenge.getId(), challenge.getAutoChallengeType(), exception
        );
    }

    private record ParsedChallenge(AutoChallengeType<?> type, Object parameters) {
    }

    private record EvaluationResult(int awardedUsers, boolean failed) {
    }
}
