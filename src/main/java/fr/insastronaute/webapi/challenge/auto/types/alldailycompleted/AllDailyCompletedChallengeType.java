package fr.insastronaute.webapi.challenge.auto.types.alldailycompleted;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeParameterValidationException;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Le bilan d'une période : tous ses défis du jour réalisés.
 *
 * <p>Évalué à partir du dernier jour de la période, pas du lendemain. Le portail
 * d'attribution refuse un défi fermé, si bien qu'un bilan qui attendait le
 * lendemain de sa période ne tombait jamais sur un défi daté de cette période.
 * Pas plus tôt non plus : les défis du jour se publient la veille, et avant le
 * dernier jour il en manque encore — qui aurait fait ceux du lundi toucherait le
 * bilan de la semaine.</p>
 */
@Component
public class AllDailyCompletedChallengeType implements AutoChallengeType<AllDailyCompletedParameters> {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Paris");
    private final ChallengeRepository challengeRepository;
    private final Supplier<Instant> now;

    @Autowired
    public AllDailyCompletedChallengeType(ChallengeRepository challengeRepository) {
        this(challengeRepository, Instant::now);
    }

    AllDailyCompletedChallengeType(
            ChallengeRepository challengeRepository,
            Supplier<Instant> now
    ) {
        this.challengeRepository = challengeRepository;
        this.now = now;
    }

    @Override public String key() { return "ALL_DAILY_CHALLENGES_COMPLETED"; }

    @Override public String publicName() { return "Tous les défis du jour réalisés"; }

    @Override public String description() {
        return "Valide le défi bilan lorsque tous les défis DAILY accessibles ont été réalisés.";
    }

    @Override public Class<AllDailyCompletedParameters> parametersType() {
        return AllDailyCompletedParameters.class;
    }

    @Override public List<AutoChallengeParameter> parameters() {
        return List.of(
                new AutoChallengeParameter(
                        "startDate", "Date de début", AutoChallengeParameterType.DATE,
                        true, JsonNodeFactory.instance.textNode("2026-01-01"), null, null, List.of()),
                new AutoChallengeParameter(
                        "endDate", "Date de fin", AutoChallengeParameterType.DATE,
                        true, JsonNodeFactory.instance.textNode("2026-12-31"), null, null, List.of())
        );
    }

    @Override public void validate(AllDailyCompletedParameters parameters) {
        if (parameters == null || parameters.startDate() == null || parameters.endDate() == null) {
            throw new AutoChallengeParameterValidationException("startDate and endDate are required.");
        }
        if (parameters.startDate().isAfter(parameters.endDate())) {
            throw new AutoChallengeParameterValidationException("startDate must be on or before endDate.");
        }
    }

    @Override public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }

    @Override
    public AutoChallengeProgress progress(AllDailyCompletedParameters parameters, UUID userId) {
        Period period = Period.of(parameters);
        if (!period.open(now.get())) {
            return null;
        }
        long due = challengeRepository.countDailyChallengesDue(
                userId, period.startInclusive(), period.endExclusive(), key());
        if (due == 0) {
            return null;
        }
        long completed = challengeRepository.countDailyChallengesCompleted(
                userId, period.startInclusive(), period.endExclusive(), key());
        return AutoChallengeProgress.of(completed, due);
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            AllDailyCompletedParameters parameters,
            AutoChallengeCandidateQuery query
    ) {
        validate(parameters);
        Period period = Period.of(parameters);
        if (!period.open(now.get())) {
            return new AutoChallengeCandidatePage(List.of(), null);
        }

        List<UUID> candidates = challengeRepository.findUsersCompletingAllDailyChallenges(
                period.startInclusive(), period.endExclusive(), key(),
                query.challengeId(), query.afterUserId(), Pageable.ofSize(query.limit() + 1));
        boolean hasNextPage = candidates.size() > query.limit();
        List<UUID> userIds = hasNextPage ? List.copyOf(candidates.subList(0, query.limit())) : List.copyOf(candidates);
        UUID nextCursor = hasNextPage ? userIds.getLast() : null;
        return new AutoChallengeCandidatePage(userIds, nextCursor);
    }

    /** La période en instants de Paris, et le moment où son dernier jour commence. */
    private record Period(Instant startInclusive, Instant lastDayStart, Instant endExclusive) {

        static Period of(AllDailyCompletedParameters parameters) {
            return new Period(
                    parameters.startDate().atStartOfDay(BUSINESS_ZONE).toInstant(),
                    parameters.endDate().atStartOfDay(BUSINESS_ZONE).toInstant(),
                    parameters.endDate().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        }

        boolean open(Instant now) {
            return !now.isBefore(lastDayStart);
        }
    }
}
