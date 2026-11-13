package fr.insastronaute.webapi.leaderboard.domain;

import fr.insastronaute.webapi.user.domain.AppUser;

public enum LeaderboardMetric {
    TOURNAMENT_POINTS,
    PHYSICAL_COLLECTION_POINTS,
    VIRTUAL_COLLECTION_POINTS,
    CHALLENGE_POINTS,
    /**
     * Les points rapportés par le jeu.
     *
     * <p>Ils ne vivent pas sur le joueur mais sur ses parties : c'est la somme des
     * jetons versés en fin de partie, recomptée à chaque cliché. Le calcul passe
     * donc par {@code LeaderboardService}, comme pour les points de collection.</p>
     *
     * <p>Le solde de jetons du joueur ne conviendrait pas : il se dépense, et un
     * classement du jeu se viderait à la boutique.</p>
     */
    GAME_POINTS,
    /**
     * Le classement Elo du multijoueur.
     *
     * <p><b>Ce n'est pas un compte de points</b>, et c'est ce qui le met à part des cinq
     * autres : il ne s'additionne à rien. Il ne rejoint donc ni le classement total — un
     * classement de départ à 1000 y écraserait des points qui se comptent par dizaines — ni
     * les classements de département, où la somme des classements d'une promo mesurerait
     * surtout son effectif. Voir {@link #countsTowardTotal()} et {@link #supports}.</p>
     *
     * <p>Seuls comptent les joueurs qui ont disputé au moins un duel classé : un profil se crée
     * rien qu'en ouvrant le menu multijoueur, déjà à 1000, et un curieux passerait sinon devant
     * un vrai joueur descendu à 950.</p>
     */
    PVP_ELO;

    public long score(AppUser user) {
        return switch (this) {
            case TOURNAMENT_POINTS -> user.getTournamentPoints();
            case PHYSICAL_COLLECTION_POINTS -> user.getPhysicalCollectionPoints();
            case VIRTUAL_COLLECTION_POINTS -> user.getVirtualCollectionPoints();
            case CHALLENGE_POINTS -> user.getChallengePoints();
            // Rien à lire sur le joueur : ses parties portent le score, et
            // LeaderboardService les additionne avant de classer.
            case GAME_POINTS -> 0L;
            // Porté par le profil multijoueur, lu par LeaderboardService en une requête.
            case PVP_ELO -> 0L;
        };
    }

    /**
     * Vrai quand ce classement se calcule à cette échelle.
     *
     * <p>Tout ce qui parcourt « toutes les métriques » d'une échelle doit passer par ici : une
     * métrique sans configuration à une échelle y lèverait une introuvable, et le classement des
     * départements tomberait en entier pour une métrique qui ne le concerne pas.</p>
     */
    public boolean supports(LeaderboardScope scope) {
        return this != PVP_ELO || scope == LeaderboardScope.USER;
    }

    /**
     * Vrai quand ne figurent à ce classement que les joueurs qui ont un score à y porter.
     *
     * <p>Un classement de points montre tout le monde, zéro compris : zéro point est un score.
     * Un classement Elo ne peut pas faire de même — un joueur qui n'a jamais disputé de duel n'a
     * pas zéro, il n'a pas de classement, et l'afficher à zéro en bas de la liste publique serait
     * dire de lui quelque chose de faux. L'appartenance se décide sur la présence d'un score, et
     * non sur sa valeur : un vrai joueur peut descendre jusqu'à zéro et doit rester classé.</p>
     */
    public boolean ranksOnlyScoredPlayers() {
        return this == PVP_ELO;
    }

    /**
     * Vrai quand ce classement entre dans le classement total, pondéré.
     *
     * <p>Le total additionne des points ; une métrique qui n'en compte pas n'y a pas sa place, et
     * son poids n'est pas à demander.</p>
     */
    public boolean countsTowardTotal() {
        return this != PVP_ELO;
    }
}
