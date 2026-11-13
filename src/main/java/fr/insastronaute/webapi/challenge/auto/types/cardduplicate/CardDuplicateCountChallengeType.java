package fr.insastronaute.webapi.challenge.auto.types.cardduplicate;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Obtain the same card N times". Progress reports the player's best pile, so a
 * 47/100 bar tracks whichever card they are closest on.
 */
@Component
public final class CardDuplicateCountChallengeType implements AutoChallengeType<CardDuplicateCountParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardTeamMemberRepository teamMemberRepository;

    public CardDuplicateCountChallengeType(
            CardCollectionRepository collectionRepository,
            CardTeamMemberRepository teamMemberRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Override
    public String key() {
        return "CARD_DUPLICATE_COUNT";
    }

    @Override
    public String publicName() {
        return "Accumuler des exemplaires d'une même carte";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède un nombre donné d'exemplaires d'une seule et même carte.";
    }

    @Override
    public Class<CardDuplicateCountParameters> parametersType() {
        return CardDuplicateCountParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.integer("copies", "Nombre d'exemplaires", 2, 100_000),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CardDuplicateCountParameters parameters) {
        return "Obtiens " + parameters.copies() + " fois une même carte"
                + CollectionScope.suffix(parameters.collectionType());
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CARD_OBTAINED);
    }

    /**
     * Les doubles comptent dans la collection commune -- c'est meme leur seul usage.
     * L'objectif se mesure donc sur l'equipe.
     */
    @Override
    public boolean isTeamScoped() {
        return true;
    }

    /** La collection virtuelle reste personnelle : ni mesure ni recompense d'equipe. */
    @Override
    public boolean isTeamScoped(CardDuplicateCountParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    /** Les identifiants a mesurer : l'equipe pour le physique, soi seul sinon. */
    private List<UUID> scopedUsers(CardDuplicateCountParameters parameters, UUID userId) {
        return isTeamScoped(parameters) ? teamOrSelf(userId) : List.of(userId);
    }

    /** Les identifiants des membres de son equipe, ou le sien s'il n'en a pas. */
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
    public AutoChallengeProgress progress(CardDuplicateCountParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(
                collectionRepository.findHighestCopiesForUsers(
                        scopedUsers(parameters, userId), parameters.collectionType()),
                parameters.copies());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            CardDuplicateCountParameters parameters, AutoChallengeCandidateQuery query
    ) {
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningEnoughCopiesOfOneCard(
                        parameters.copies(), query.afterUserId(), PageRequest.of(0, query.limit()))
                : List.of();
        if (!teamIds.isEmpty()) {
            List<UUID> members = teamIds.stream()
                    .flatMap(teamId -> teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream())
                    .map(member -> member.getUser().getId())
                    .toList();
            return new AutoChallengeCandidatePage(
                    members, teamIds.size() < query.limit() ? null : teamIds.getLast());
        }

        List<UUID> userIds = collectionRepository.findUsersOwningEnoughCopiesOfOneCard(
                parameters.copies(), parameters.collectionType(), query.challengeId(), query.afterUserId(),
                PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
