package fr.insastronaute.webapi.challenge.auto.types.eventregistrations;

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
public final class EventRegistrationsChallengeType implements AutoChallengeType<EventRegistrationsParameters> {

    private final EventRegistrationRepository registrationRepository;

    public EventRegistrationsChallengeType(EventRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public String key() {
        return "EVENT_REGISTRATIONS";
    }

    @Override
    public String publicName() {
        return "S'inscrire à des événements";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur s'est inscrit à un nombre donné d'événements, présence non requise.";
    }

    @Override
    public Class<EventRegistrationsParameters> parametersType() {
        return EventRegistrationsParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre d'inscriptions", 1, 1_000_000));
    }

    @Override
    public String phrase(EventRegistrationsParameters parameters) {
        return parameters.count() == 1
                ? "Inscris-toi à un événement"
                : "Inscris-toi à {n} événements".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.EVENT_REGISTERED);
    }

    @Override
    public AutoChallengeProgress progress(EventRegistrationsParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(registrationRepository.countRegistrationsByUser(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(EventRegistrationsParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = registrationRepository.findUsersWithEnoughRegistrations(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
