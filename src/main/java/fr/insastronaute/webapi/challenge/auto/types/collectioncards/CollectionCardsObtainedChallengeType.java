package fr.insastronaute.webapi.challenge.auto.types.collectioncards;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * « Obtiens N cartes », sans distinction de categorie.
 *
 * <p>Le pendant de {@code CARD_CATEGORY_OBTAINED} pour une phrase qui vise la
 * collection entiere : « avoir dix cartes » se dit sans nommer de rarete, et
 * demander une categorie pour l'ignorer ensuite obligerait a en choisir une au
 * hasard.</p>
 */
@Component
public final class CollectionCardsObtainedChallengeType implements AutoChallengeType<CollectionCardsParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardTeamMemberRepository teamMemberRepository;

    public CollectionCardsObtainedChallengeType(
            CardCollectionRepository collectionRepository,
            CardTeamMemberRepository teamMemberRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Override
    public String key() {
        return "COLLECTION_CARDS_OBTAINED";
    }

    @Override
    public String publicName() {
        return "Obtenir des cartes (toute la collection)";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède un nombre donné de cartes distinctes, "
                + "toutes catégories confondues.";
    }

    @Override
    public Class<CollectionCardsParameters> parametersType() {
        return CollectionCardsParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.integer("count", "Nombre de cartes distinctes", 1, 10_000),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CollectionCardsParameters parameters) {
        String phrase = parameters.count() == 1
                ? "Obtiens une carte"
                : "Obtiens " + parameters.count() + " cartes différentes";
        return phrase + CollectionScope.suffix(parameters.collectionType());
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CARD_OBTAINED);
    }

    /** La collection physique est mutualisee : l'objectif s'y mesure sur l'equipe. */
    @Override
    public boolean isTeamScoped() {
        return true;
    }

    @Override
    public boolean isTeamScoped(CollectionCardsParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    @Override
    public AutoChallengeProgress progress(CollectionCardsParameters parameters, UUID userId) {
        long owned = collectionRepository.countDistinctCardsForUsers(
                scopedUsers(parameters, userId), parameters.collectionType());
        return AutoChallengeProgress.of(owned, parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            CollectionCardsParameters parameters, AutoChallengeCandidateQuery query
    ) {
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningEnoughCards(
                        parameters.count(), query.afterUserId(), PageRequest.of(0, query.limit()))
                : List.of();
        if (!teamIds.isEmpty()) {
            List<UUID> members = teamIds.stream()
                    .flatMap(teamId -> teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream())
                    .map(member -> member.getUser().getId())
                    .toList();
            return new AutoChallengeCandidatePage(
                    members, teamIds.size() < query.limit() ? null : teamIds.getLast());
        }

        List<UUID> userIds = collectionRepository.findUsersOwningEnoughCards(
                parameters.collectionType(), parameters.count(), query.challengeId(), query.afterUserId(),
                PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }

    /** Les identifiants a mesurer : l'equipe pour le physique, soi seul sinon. */
    private List<UUID> scopedUsers(CollectionCardsParameters parameters, UUID userId) {
        if (!isTeamScoped(parameters)) {
            return List.of(userId);
        }
        return teamMemberRepository.findByUserId(userId)
                .map(membership -> teamMemberRepository
                        .findByTeamIdOrderByJoinedAt(membership.getTeam().getId())
                        .stream()
                        .map(member -> member.getUser().getId())
                        .toList())
                .orElseGet(() -> List.of(userId));
    }
}
