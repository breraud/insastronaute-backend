package fr.insastronaute.webapi.challenge.auto.types.cardcategorycompletion;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.types.CollectionScope;
import fr.insastronaute.webapi.card.repository.CardRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Reach X% of category Y". The target is resolved against the category's live
 * card count, so adding a card to a category moves everyone's bar back — which
 * is the honest behaviour for a completion goal.
 */
@Component
public final class CardCategoryCompletionChallengeType implements AutoChallengeType<CardCategoryCompletionParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardTeamMemberRepository teamMemberRepository;
    private final CardRepository cardRepository;
    private final CardCategoryRepository categoryRepository;

    public CardCategoryCompletionChallengeType(
            CardCollectionRepository collectionRepository,
            CardTeamMemberRepository teamMemberRepository,
            CardRepository cardRepository,
            CardCategoryRepository categoryRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.cardRepository = cardRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public String key() {
        return "CARD_CATEGORY_COMPLETION";
    }

    @Override
    public String publicName() {
        return "Compléter une catégorie de cartes";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède un pourcentage donné des cartes actives d'une catégorie.";
    }

    @Override
    public Class<CardCategoryCompletionParameters> parametersType() {
        return CardCategoryCompletionParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.cardCategory("categoryId", "Catégorie de carte"),
                new AutoChallengeParameter(
                        "percentage", "Pourcentage de complétion",
                        AutoChallengeParameterType.INTEGER, true, 100, 1L, 100L, null
                ),
                // Le champ etait lu depuis toujours -- les parametres le parsent, la
                // mesure s'en sert -- mais il n'etait pas declare, donc le panneau ne
                // l'offrait pas : ce type seul restait coince sur le physique, quand
                // les trois autres laissaient choisir. Absent, il vaut PHYSICAL, donc
                // les phrases deja publiees ne changent pas de sens.
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CardCategoryCompletionParameters parameters) {
        String categoryName = categoryRepository.findById(parameters.categoryId())
                .map(category -> category.getName())
                .orElse("inconnue");
        String phrase = parameters.percentage() == 100
                ? "Complète toute la catégorie " + categoryName
                : "Atteins " + parameters.percentage() + " % de la catégorie " + categoryName;
        return phrase + CollectionScope.suffix(parameters.collectionType());
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CARD_OBTAINED);
    }

    /**
     * L'objectif se mesure sur la collection de l'equipe, jamais sur celle du joueur seul.
     *
     * <p>Les cartes physiques sont mutualisees : compter chacun de son cote ferait rater
     * l'objectif a une equipe qui l'a pourtant atteint, et donnerait une progression qui
     * ne correspond a rien de ce que le joueur voit dans sa collection d'equipe.</p>
     *
     * <p>Sans equipe, on compte ce qu'il possede -- il n'y a personne d'autre.</p>
     */
    @Override
    public boolean isTeamScoped() {
        return true;
    }

    /** La collection virtuelle reste personnelle : ni mesure ni recompense d'equipe. */
    @Override
    public boolean isTeamScoped(CardCategoryCompletionParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    /** Les identifiants a mesurer : l'equipe pour le physique, soi seul sinon. */
    private List<UUID> scopedUsers(CardCategoryCompletionParameters parameters, UUID userId) {
        return isTeamScoped(parameters) ? teamOrSelf(userId) : List.of(userId);
    }

    @Override
    public AutoChallengeProgress progress(CardCategoryCompletionParameters parameters, UUID userId) {
        long target = requiredCards(parameters);
        long owned = collectionRepository.countDistinctCardsInCategoryForUsers(
                scopedUsers(parameters, userId), parameters.categoryId(), parameters.collectionType());
        return AutoChallengeProgress.of(owned, target);
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

    /**
     * Deux recherches, parce qu'il y a deux facons d'atteindre l'objectif.
     *
     * <p>Les equipes d'abord : un joueur peut valider grace a des cartes qu'il n'a pas
     * lui-meme, donc partir des joueurs qui atteignent le seuil seuls raterait ceux que
     * leur equipe porte. Puis les joueurs sans equipe, qui n'ont qu'eux-memes.</p>
     *
     * <p>La pagination avance sur les equipes ; les joueurs seuls sont rendus au dernier
     * passage, quand il n'y a plus d'equipe a parcourir. Les melanger ferait avancer deux
     * curseurs sur un seul champ, et le balayage en oublierait sans rien signaler.</p>
     */
    @Override
    public AutoChallengeCandidatePage findCandidates(
            CardCategoryCompletionParameters parameters, AutoChallengeCandidateQuery query
    ) {
        long threshold = requiredCards(parameters);
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningCardsInCategory(
                        parameters.categoryId(), threshold, query.afterUserId(), PageRequest.of(0, query.limit()))
                : List.of();

        if (!teamIds.isEmpty()) {
            List<UUID> members = teamIds.stream()
                    .flatMap(teamId -> teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream())
                    .map(member -> member.getUser().getId())
                    .toList();
            return new AutoChallengeCandidatePage(
                    members, teamIds.size() < query.limit() ? null : teamIds.getLast());
        }

        // Sur la collection virtuelle, personne n'est porte par son equipe : tout le
        // monde est candidat a titre personnel, membre d'une equipe ou non.
        List<UUID> soloIds = collectionRepository.findUsersOwningCardsInCategory(
                parameters.categoryId(), parameters.collectionType(), threshold, query.challengeId(), null,
                PageRequest.of(0, query.limit())
        ).stream()
                .filter(userId -> !isTeamScoped(parameters) || teamMemberRepository.findByUserId(userId).isEmpty())
                .toList();
        return new AutoChallengeCandidatePage(soloIds, null);
    }

    /**
     * Rounds up, so "50 % of 5 cards" needs 3 rather than 2: a player should
     * never clear a completion goal while short of the honest half.
     */
    private long requiredCards(CardCategoryCompletionParameters parameters) {
        long total = cardRepository.countCollectionCardsInCategory(parameters.categoryId());
        if (total <= 0) {
            // An empty category would otherwise be trivially complete for everyone.
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (total * parameters.percentage() + 99) / 100);
    }
}
