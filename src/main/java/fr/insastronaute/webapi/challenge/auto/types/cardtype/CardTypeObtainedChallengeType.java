package fr.insastronaute.webapi.challenge.auto.types.cardtype;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
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
 * « Obtenir N cartes de type X ».
 *
 * <p>Le pendant, sur l'autre axe du catalogue, de « obtenir des cartes d'une
 * categorie ». La categorie porte la rarete ; le type porte ce que la carte
 * represente -- un bar, un lieu, une personne. Aucune des deux ne dit ce que dit
 * l'autre, et « avoir des cartes bar » ne s'ecrivait avec ni l'une ni l'autre.</p>
 */
@Component
public final class CardTypeObtainedChallengeType implements AutoChallengeType<CardTypeObtainedParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardTeamMemberRepository teamMemberRepository;
    private final CardTypeRepository typeRepository;

    public CardTypeObtainedChallengeType(
            CardCollectionRepository collectionRepository,
            CardTeamMemberRepository teamMemberRepository,
            CardTypeRepository typeRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.typeRepository = typeRepository;
    }

    @Override
    public String key() {
        return "CARD_TYPE_OBTAINED";
    }

    @Override
    public String publicName() {
        return "Obtenir des cartes d'un type";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède un nombre donné de cartes distinctes d'un même type.";
    }

    @Override
    public Class<CardTypeObtainedParameters> parametersType() {
        return CardTypeObtainedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.cardType("typeId", "Type de carte"),
                AutoChallengeParameter.integer("count", "Nombre de cartes distinctes", 1, 10_000),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CardTypeObtainedParameters parameters) {
        String typeName = typeRepository.findById(parameters.typeId())
                .map(type -> type.getName())
                .orElse("inconnu");
        String phrase = parameters.count() == 1
                ? "Obtiens une carte " + typeName
                : "Obtiens " + parameters.count() + " cartes " + typeName;
        return phrase + CollectionScope.suffix(parameters.collectionType());
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CARD_OBTAINED);
    }

    /** La collection physique est mutualisee : l'objectif se mesure sur l'equipe. */
    @Override
    public boolean isTeamScoped() {
        return true;
    }

    /** La collection virtuelle reste personnelle : ni mesure ni recompense d'equipe. */
    @Override
    public boolean isTeamScoped(CardTypeObtainedParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    private List<UUID> scopedUsers(CardTypeObtainedParameters parameters, UUID userId) {
        return isTeamScoped(parameters) ? teamOrSelf(userId) : List.of(userId);
    }

    private List<UUID> teamOrSelf(UUID userId) {
        return teamMemberRepository.findByUserId(userId)
                .map(membership -> teamMemberRepository
                        .findByTeamIdOrderByJoinedAt(membership.getTeam().getId())
                        .stream()
                        .map(member -> member.getUser().getId())
                        .toList())
                .orElseGet(() -> List.of(userId));
    }

    @Override
    public AutoChallengeProgress progress(CardTypeObtainedParameters parameters, UUID userId) {
        long owned = collectionRepository.countDistinctCardsOfTypeForUsers(
                scopedUsers(parameters, userId), parameters.typeId(), parameters.collectionType());
        return AutoChallengeProgress.of(owned, parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            CardTypeObtainedParameters parameters, AutoChallengeCandidateQuery query
    ) {
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningCardsOfType(
                        parameters.typeId(), parameters.count(), query.afterUserId(),
                        PageRequest.of(0, query.limit()))
                : List.of();
        if (!teamIds.isEmpty()) {
            List<UUID> members = teamIds.stream()
                    .flatMap(teamId -> teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream())
                    .map(member -> member.getUser().getId())
                    .toList();
            return new AutoChallengeCandidatePage(
                    members, teamIds.size() < query.limit() ? null : teamIds.getLast());
        }

        List<UUID> userIds = collectionRepository.findUsersOwningCardsOfType(
                parameters.typeId(), parameters.collectionType(), parameters.count(),
                query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
