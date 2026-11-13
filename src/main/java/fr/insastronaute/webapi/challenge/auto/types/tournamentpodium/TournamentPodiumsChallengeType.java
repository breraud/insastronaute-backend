package fr.insastronaute.webapi.challenge.auto.types.tournamentpodium;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.tournament.repository.TournamentPointAwardRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public final class TournamentPodiumsChallengeType implements AutoChallengeType<TournamentPodiumsParameters> {

    private final TournamentPointAwardRepository awardRepository;

    public TournamentPodiumsChallengeType(TournamentPointAwardRepository awardRepository) {
        this.awardRepository = awardRepository;
    }

    @Override
    public String key() {
        return "TOURNAMENT_PODIUMS";
    }

    @Override
    public String publicName() {
        return "Monter sur le podium";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a terminé un nombre donné de fois dans un palier de victoire.";
    }

    @Override
    public Class<TournamentPodiumsParameters> parametersType() {
        return TournamentPodiumsParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de podiums", 1, 100_000));
    }

    @Override
    public String phrase(TournamentPodiumsParameters parameters) {
        return parameters.count() == 1
                ? "Termine une fois sur le podium d'un tournoi"
                : "Termine {n} fois sur le podium d'un tournoi".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.TOURNAMENT_SETTLED);
    }

    @Override
    public AutoChallengeProgress progress(TournamentPodiumsParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(awardRepository.countPodiumsForUser(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(TournamentPodiumsParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = awardRepository.findUsersWithPodiums(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
