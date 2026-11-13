package fr.insastronaute.webapi.challenge.auto.types.cardtypecompletion;

import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
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
 * « Avoir toutes les cartes bar » -- ou la moitie, ou les trois quarts.
 *
 * <p>Le pendant, sur l'autre axe du catalogue, de « completer une categorie de
 * cartes ». La categorie porte la rarete ; le type porte ce que la carte
 * represente. Viser un type entier ne s'ecrivait avec aucun des types existants :
 * ni « completer une categorie », qui vise la rarete, ni « completer la
 * collection », qui vise tout.</p>
 *
 * <p>Le denominateur suit le catalogue : ajouter une carte de ce type releve la
 * barre pour qui ne l'a pas encore atteinte. Qui veut un objectif fige pendant
 * l'integration prendra plutot « obtenir des cartes d'un type » avec un nombre.</p>
 */
@Component
public final class CardTypeCompletionChallengeType implements AutoChallengeType<CardTypeCompletionParameters> {

    private final CardCollectionRepository collectionRepository;
    private final CardRepository cardRepository;
    private final CardTeamMemberRepository teamMemberRepository;
    private final CardTypeRepository typeRepository;

    public CardTypeCompletionChallengeType(
            CardCollectionRepository collectionRepository,
            CardRepository cardRepository,
            CardTeamMemberRepository teamMemberRepository,
            CardTypeRepository typeRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.cardRepository = cardRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.typeRepository = typeRepository;
    }

    @Override
    public String key() {
        return "CARD_TYPE_COMPLETION";
    }

    @Override
    public String publicName() {
        return "Compléter un type de cartes";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur possède un pourcentage donné des cartes actives d'un même type.";
    }

    @Override
    public Class<CardTypeCompletionParameters> parametersType() {
        return CardTypeCompletionParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.cardType("typeId", "Type de carte"),
                new AutoChallengeParameter(
                        "percentage", "Pourcentage de complétion",
                        AutoChallengeParameterType.INTEGER, true, 100, 1L, 100L, null
                ),
                AutoChallengeParameter.collectionType("collectionType", "Collection")
        );
    }

    @Override
    public String phrase(CardTypeCompletionParameters parameters) {
        String typeName = typeRepository.findById(parameters.typeId())
                .map(type -> type.getName())
                .orElse("inconnu");
        String phrase = parameters.percentage() >= 100
                ? "Obtiens toutes les cartes " + typeName
                : "Obtiens " + parameters.percentage() + " % des cartes " + typeName;
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
    public boolean isTeamScoped(CardTypeCompletionParameters parameters) {
        return CollectionScope.isTeamScoped(parameters.collectionType());
    }

    private List<UUID> scopedUsers(CardTypeCompletionParameters parameters, UUID userId) {
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
    public AutoChallengeProgress progress(CardTypeCompletionParameters parameters, UUID userId) {
        long owned = collectionRepository.countDistinctCardsOfTypeForUsers(
                scopedUsers(parameters, userId), parameters.typeId(), parameters.collectionType());
        return AutoChallengeProgress.of(owned, requiredCards(parameters));
    }

    /**
     * Deux recherches, parce qu'il y a deux facons d'atteindre l'objectif.
     *
     * <p>Les equipes d'abord : un joueur peut valider grace a des cartes qu'il n'a pas
     * lui-meme. Puis les joueurs sans equipe, qui n'ont qu'eux-memes.</p>
     */
    @Override
    public AutoChallengeCandidatePage findCandidates(
            CardTypeCompletionParameters parameters, AutoChallengeCandidateQuery query
    ) {
        long threshold = requiredCards(parameters);
        List<UUID> teamIds = isTeamScoped(parameters)
                ? collectionRepository.findTeamsOwningCardsOfType(
                        parameters.typeId(), threshold, query.afterUserId(), PageRequest.of(0, query.limit()))
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
        List<UUID> soloIds = collectionRepository.findUsersOwningCardsOfType(
                parameters.typeId(), parameters.collectionType(), threshold, query.challengeId(), null,
                PageRequest.of(0, query.limit())
        ).stream()
                .filter(userId -> !isTeamScoped(parameters) || teamMemberRepository.findByUserId(userId).isEmpty())
                .toList();
        return new AutoChallengeCandidatePage(soloIds, null);
    }

    /**
     * Arrondi au superieur : « 50 % de cinq cartes » en demande trois, pas deux. On
     * ne valide pas une completion en restant sous la moitie honnete.
     */
    private long requiredCards(CardTypeCompletionParameters parameters) {
        long total = cardRepository.countCollectionCardsOfType(parameters.typeId());
        if (total <= 0) {
            // Un type vide serait sinon complete par tout le monde, sans rien faire.
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (total * parameters.percentage() + 99) / 100);
    }
}
