package fr.insastronaute.webapi.challenge.auto.types.cardcategory;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Obtain N cards of category X". Card categories are the ranked rarity axis
 * here, so this single phrase covers "obtain a common card", "obtain a legendary
 * card", and "obtain a card of category X" with different parameters.
 */
@Component
public final class CardCategoryObtainedChallengeType implements AutoChallengeType<CardCategoryObtainedParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardTeamMemberRepository teamMemberRepository;
    private final CardCategoryRepository categoryRepository;

    public CardCategoryObtainedChallengeType(
            CardCollectionRepository collectionRepository,
            CardTeamMemberRepository teamMemberRepository,
            CardCategoryRepository categoryRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public String key() {
        return "CARD_CATEGORY_OBTAINED";
    }

    @Override
    public String publicName() {
        return "Obtenir des cartes d'une catégorie";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède un nombre donné de cartes distinctes dans une catégorie.";
    }

    @Override
    public Class<CardCategoryObtainedParameters> parametersType() {
        return CardCategoryObtainedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.cardCategory("categoryId", "Catégorie de carte"),
                AutoChallengeParameter.integer("count", "Nombre de cartes distinctes", 1, 10_000),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CardCategoryObtainedParameters parameters) {
        String categoryName = categoryRepository.findById(parameters.categoryId())
                .map(category -> category.getName())
                .orElse("inconnue");
        String phrase = parameters.count() == 1
                ? "Obtiens une carte de catégorie " + categoryName
                : "Obtiens " + parameters.count() + " cartes de catégorie " + categoryName;
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
    public boolean isTeamScoped(CardCategoryObtainedParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    /** Les identifiants a mesurer : l'equipe pour le physique, soi seul sinon. */
    private List<UUID> scopedUsers(CardCategoryObtainedParameters parameters, UUID userId) {
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
    public AutoChallengeProgress progress(CardCategoryObtainedParameters parameters, UUID userId) {
        long owned = collectionRepository.countDistinctCardsInCategoryForUsers(
                scopedUsers(parameters, userId), parameters.categoryId(), parameters.collectionType());
        return AutoChallengeProgress.of(owned, parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            CardCategoryObtainedParameters parameters, AutoChallengeCandidateQuery query
    ) {
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningCardsInCategory(
                        parameters.categoryId(), parameters.count(), query.afterUserId(),
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

        List<UUID> userIds = collectionRepository.findUsersOwningCardsInCategory(
                parameters.categoryId(), parameters.collectionType(), parameters.count(),
                query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
