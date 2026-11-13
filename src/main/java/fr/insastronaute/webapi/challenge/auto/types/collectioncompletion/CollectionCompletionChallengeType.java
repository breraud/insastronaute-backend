package fr.insastronaute.webapi.challenge.auto.types.collectioncompletion;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
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
 * « Complete X % de la collection », toutes categories confondues.
 *
 * <p>Le seuil se recalcule a chaque evaluation depuis le catalogue actif : une
 * carte ajoutee en cours d'integration deplace la cible, ce qui est le sens meme
 * d'un pourcentage. Fixer le nombre a la creation figerait une cible que le
 * catalogue dementirait le lendemain.</p>
 */
@Component
public final class CollectionCompletionChallengeType implements AutoChallengeType<CollectionCompletionParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardRepository cardRepository;
    private final CardTeamMemberRepository teamMemberRepository;

    public CollectionCompletionChallengeType(
            CardCollectionRepository collectionRepository,
            CardRepository cardRepository,
            CardTeamMemberRepository teamMemberRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.cardRepository = cardRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Override
    public String key() {
        return "COLLECTION_COMPLETION";
    }

    @Override
    public String publicName() {
        return "Compléter la collection";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur atteint un pourcentage donné de la collection entière.";
    }

    @Override
    public Class<CollectionCompletionParameters> parametersType() {
        return CollectionCompletionParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                new AutoChallengeParameter(
                        "percentage", "Pourcentage de complétion",
                        AutoChallengeParameterType.INTEGER, true, 100, 1L, 100L, null
                ),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CollectionCompletionParameters parameters) {
        String phrase = parameters.percentage() == 100
                ? "Complète toute la collection"
                : "Atteins " + parameters.percentage() + " % de la collection";
        return phrase + CollectionScope.suffix(parameters.collectionType());
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CARD_OBTAINED);
    }

    @Override
    public boolean isTeamScoped() {
        return true;
    }

    @Override
    public boolean isTeamScoped(CollectionCompletionParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    @Override
    public AutoChallengeProgress progress(CollectionCompletionParameters parameters, UUID userId) {
        long owned = collectionRepository.countDistinctCardsForUsers(
                scopedUsers(parameters, userId), parameters.collectionType());
        return AutoChallengeProgress.of(owned, requiredCards(parameters));
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(
            CollectionCompletionParameters parameters, AutoChallengeCandidateQuery query
    ) {
        long threshold = requiredCards(parameters);
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningEnoughCards(
                        threshold, query.afterUserId(), PageRequest.of(0, query.limit()))
                : List.of();
        if (!teamIds.isEmpty()) {
            List<UUID> members = teamIds.stream()
                    .flatMap(teamId -> teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream())
                    .map(member -> member.getUser().getId())
                    .toList();
            return new AutoChallengeCandidatePage(
                    members, teamIds.size() < query.limit() ? null : teamIds.getLast());
        }

        // Sur un objectif d'equipe, personne ne valide a titre personnel : les cartes
        // physiques sont mises en commun, et l'objectif porte sur ce que l'equipe
        // possede. Sans ce filtre, la recherche retombait ici des qu'aucune equipe
        // n'atteignait le seuil et recompensait un joueur pour sa seule collection --
        // alors que sa barre d'avancement, elle, comptait bien celle de l'equipe.
        // Restent candidats seuls ceux que personne ne porte : les joueurs sans equipe.
        List<UUID> page = collectionRepository.findUsersOwningEnoughCards(
                parameters.collectionType(), threshold, query.challengeId(), query.afterUserId(),
                PageRequest.of(0, query.limit()));
        List<UUID> userIds = page.stream()
                .filter(userId -> !isTeamScoped(parameters) || teamMemberRepository.findByUserId(userId).isEmpty())
                .toList();
        // Le curseur se lit sur la page brute, jamais sur la liste filtree : une page
        // pleine dont le filtre ne laisse qu'un joueur reste une page pleine, et
        // conclure d'apres le filtre arreterait le balayage sur les suivants.
        return new AutoChallengeCandidatePage(
                userIds, page.size() < query.limit() ? null : page.getLast());
    }

    /**
     * Combien de cartes il faut posseder.
     *
     * <p>Au moins une des que le catalogue n'est pas vide : un pourcentage arrondi
     * a zero validerait la phrase pour qui n'a rien.</p>
     */
    private long requiredCards(CollectionCompletionParameters parameters) {
        // La collection physique se mesure sur l'equipe, dont la vue ecarte les
        // extensions : viser le catalogue entier laisserait l'objectif ouvert sur
        // des cartes que cet ecran ne montre pas.
        long total = isTeamScoped(parameters)
                ? cardRepository.countTeamCollectionCards()
                : cardRepository.countPersonalCollectionCards();
        if (total <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(1, Math.ceilDiv(total * parameters.percentage(), 100));
    }

    private List<UUID> scopedUsers(CollectionCompletionParameters parameters, UUID userId) {
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
