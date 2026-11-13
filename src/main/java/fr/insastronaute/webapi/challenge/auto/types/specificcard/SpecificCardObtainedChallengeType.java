package fr.insastronaute.webapi.challenge.auto.types.specificcard;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
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
 * « Obtiens cette carte-là ».
 *
 * <p>Les autres phrases de collection comptent des cartes — une catégorie, un
 * total, des exemplaires — sans jamais pouvoir en désigner une. Celle-ci nomme
 * sa cible : la carte cachée derrière un code, celle d'un événement, celle qu'on
 * ne trouve qu'à un endroit.</p>
 */
@Component
public final class SpecificCardObtainedChallengeType implements AutoChallengeType<SpecificCardObtainedParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardTeamMemberRepository teamMemberRepository;
    private final CardRepository cardRepository;

    public SpecificCardObtainedChallengeType(
            CardCollectionRepository collectionRepository,
            CardTeamMemberRepository teamMemberRepository,
            CardRepository cardRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.cardRepository = cardRepository;
    }

    @Override
    public String key() {
        return "SPECIFIC_CARD_OBTAINED";
    }

    @Override
    public String publicName() {
        return "Obtenir une carte précise";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède une carte donnée du catalogue.";
    }

    @Override
    public Class<SpecificCardObtainedParameters> parametersType() {
        return SpecificCardObtainedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.card("cardId", "Carte"),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    /**
     * La carte est nommée, pas numérotée : c'est un joueur qui lit la phrase.
     *
     * <p>Une carte supprimée depuis laisse « inconnue », comme pour les catégories :
     * la phrase reste lisible, et le défi reste modifiable dans le panneau.</p>
     */
    @Override
    public String phrase(SpecificCardObtainedParameters parameters) {
        String cardName = cardRepository.findById(parameters.cardId())
                .map(card -> card.getName())
                .orElse("inconnue");
        return "Obtiens la carte " + cardName + CollectionScope.suffix(parameters.collectionType());
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CARD_OBTAINED);
    }

    /** La collection physique est mutualisee : la carte peut venir d'un coequipier. */
    @Override
    public boolean isTeamScoped() {
        return true;
    }

    /** La collection virtuelle reste personnelle : ni mesure ni recompense d'equipe. */
    @Override
    public boolean isTeamScoped(SpecificCardObtainedParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    @Override
    public AutoChallengeProgress progress(SpecificCardObtainedParameters parameters, UUID userId) {
        long holders = collectionRepository.countHoldersOfCardAmongUsers(
                scopedUsers(parameters, userId), parameters.cardId(), parameters.collectionType());
        return AutoChallengeProgress.of(holders, 1);
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            SpecificCardObtainedParameters parameters, AutoChallengeCandidateQuery query
    ) {
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningCard(
                        parameters.cardId(), query.afterUserId(), PageRequest.of(0, query.limit()))
                : List.of();
        if (!teamIds.isEmpty()) {
            List<UUID> members = teamIds.stream()
                    .flatMap(teamId -> teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream())
                    .map(member -> member.getUser().getId())
                    .toList();
            return new AutoChallengeCandidatePage(
                    members, teamIds.size() < query.limit() ? null : teamIds.getLast());
        }

        List<UUID> userIds = collectionRepository.findUsersOwningCard(
                parameters.cardId(), parameters.collectionType(), query.challengeId(),
                query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }

    /** Les identifiants a mesurer : l'equipe pour le physique, soi seul sinon. */
    private List<UUID> scopedUsers(SpecificCardObtainedParameters parameters, UUID userId) {
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
}
