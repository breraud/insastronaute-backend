package fr.insastronaute.webapi.challenge.auto.types.tournamentattended;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public final class TournamentsAttendedChallengeType implements AutoChallengeType<TournamentsAttendedParameters> {

    private final EventRegistrationRepository registrationRepository;

    public TournamentsAttendedChallengeType(EventRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public String key() {
        return "TOURNAMENTS_ATTENDED";
    }

    @Override
    public String publicName() {
        return "Être présent à des tournois";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a été pointé présent à un nombre donné de tournois.";
    }

    @Override
    public Class<TournamentsAttendedParameters> parametersType() {
        return TournamentsAttendedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de tournois", 1, 100_000));
    }

    @Override
    public String phrase(TournamentsAttendedParameters parameters) {
        return parameters.count() == 1
                ? "Sois présent à un tournoi"
                : "Sois présent à {n} tournois".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.TOURNAMENT_SETTLED);
    }

    @Override
    public AutoChallengeProgress progress(TournamentsAttendedParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(registrationRepository.countTournamentsAttendedByUser(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(TournamentsAttendedParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = registrationRepository.findUsersAttendingTournaments(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
