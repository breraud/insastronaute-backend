package fr.insastronaute.webapi.challenge.auto.types.friendscount;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public final class FriendsCountChallengeType implements AutoChallengeType<FriendsCountParameters> {

    private final FriendshipRepository friendshipRepository;

    public FriendsCountChallengeType(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    @Override
    public String key() {
        return "FRIENDS_COUNT";
    }

    @Override
    public String publicName() {
        return "Avoir des amis";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur compte un nombre donné d'amitiés acceptées.";
    }

    @Override
    public Class<FriendsCountParameters> parametersType() {
        return FriendsCountParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre d'amis", 1, 1_000_000));
    }

    @Override
    public String phrase(FriendsCountParameters parameters) {
        return parameters.count() == 1
                ? "Ajoute un ami"
                : "Ajoute {n} amis".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.FRIEND_ACCEPTED);
    }

    @Override
    public AutoChallengeProgress progress(FriendsCountParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(friendshipRepository.countAcceptedFriends(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(FriendsCountParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = friendshipRepository.findUsersWithEnoughFriends(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
