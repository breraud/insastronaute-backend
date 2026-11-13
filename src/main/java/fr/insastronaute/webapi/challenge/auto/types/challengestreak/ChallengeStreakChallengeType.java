package fr.insastronaute.webapi.challenge.auto.types.challengestreak;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * "Validate a challenge N days in a row". The longest run is computed in Java
 * rather than SQL: consecutive-day windows have no portable JPQL form, and the
 * date bucketing has to happen in the players' own timezone to mean anything.
 */
@Component
public final class ChallengeStreakChallengeType implements AutoChallengeType<ChallengeStreakParameters> {

    /** Same zone the clients display in: a "day" must be the player's day. */
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Paris");

    private final ChallengeSubmissionRepository submissionRepository;

    public ChallengeStreakChallengeType(ChallengeSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Override
    public String key() {
        return "CHALLENGE_STREAK";
    }

    @Override
    public String publicName() {
        return "Enchaîner des jours de défis";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a fait valider au moins un défi chaque jour, un nombre donné de jours d'affilée.";
    }

    @Override
    public Class<ChallengeStreakParameters> parametersType() {
        return ChallengeStreakParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("days", "Jours consécutifs", 2, 365));
    }

    @Override
    public String phrase(ChallengeStreakParameters parameters) {
        return "Fais valider un défi " + parameters.days() + " jours d'affilée";
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }

    @Override
    public AutoChallengeProgress progress(ChallengeStreakParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(longestStreak(userId), parameters.days());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(ChallengeStreakParameters parameters, AutoChallengeCandidateQuery query) {
        // Cheap prefilter first: a player needs at least as many validated
        // challenges as days to have any chance of the streak.
        List<UUID> page = submissionRepository.findUsersWithValidatedChallenges(
                parameters.days(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        // The cursor comes from the unfiltered page, otherwise dropping
        // non-qualifying players would end the scan early and skip the rest.
        UUID nextCursor = page.size() < query.limit() ? null : page.getLast();
        List<UUID> qualifying = page.stream()
                .filter(userId -> longestStreak(userId) >= parameters.days())
                .toList();
        return new AutoChallengeCandidatePage(qualifying, nextCursor);
    }

    private long longestStreak(UUID userId) {
        TreeSet<LocalDate> days = new TreeSet<>();
        for (Instant validatedAt : submissionRepository.findValidationInstantsForUser(userId)) {
            if (validatedAt != null) {
                days.add(validatedAt.atZone(APP_ZONE).toLocalDate());
            }
        }
        if (days.isEmpty()) {
            return 0;
        }

        long best = 1;
        long current = 1;
        LocalDate previous = null;
        for (LocalDate day : days) {
            if (previous != null) {
                current = day.equals(previous.plusDays(1)) ? current + 1 : 1;
            }
            best = Math.max(best, current);
            previous = day;
        }
        return best;
    }
}
