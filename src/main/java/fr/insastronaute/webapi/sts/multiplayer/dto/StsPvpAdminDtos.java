package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ce que l'administration lit du multijoueur : l'usage des personnages, les joueurs classés, et
 * le détail d'un joueur.
 *
 * <p>Regroupés dans un seul fichier parce qu'ils ne servent qu'ensemble, à la même page, et
 * qu'aucun n'a de vie en dehors d'elle.
 */
public final class StsPvpAdminDtos {

    private StsPvpAdminDtos() {
    }

    /**
     * L'usage des personnages sur une période.
     *
     * @param since           depuis quand, ou null pour « depuis toujours »
     * @param modes           les formats comptés — les raids en sont exclus par défaut, parce
     *                        qu'on y gagne contre le serveur et non contre un autre personnage
     * @param includeFriendly si les duels amicaux entrent dans le compte
     * @param battles         combien de batailles terminées ont été lues
     * @param picks           combien de sélections de personnage en tout
     */
    public record CharacterUsage(
            Instant since,
            List<String> modes,
            boolean includeFriendly,
            int battles,
            int picks,
            List<CharacterUsageRow> characters
    ) {
    }

    /**
     * @param share   la part des sélections, entre 0 et 1
     * @param winRate les victoires sur les parties décidées, ou null s'il n'y en a aucune
     */
    public record CharacterUsageRow(
            String character,
            int picks,
            double share,
            int wins,
            int losses,
            int draws,
            Double winRate
    ) {
    }

    /**
     * Un joueur classé, tel qu'une liste le montre.
     *
     * @param rank            sa place parmi tous ceux qui ont disputé un duel classé
     * @param leaderboardRank sa place au classement Elo public, ou null quand il n'y figure pas —
     *                        un compte de staff, masqué ou non étudiant joue, mais ne se classe pas.
     *                        C'est cette colonne-ci qui répond à « le premier du classement ».
     */
    public record PlayerSummary(
            int rank,
            Integer leaderboardRank,
            UUID userId,
            String displayName,
            String department,
            int elo,
            int winsRanked,
            int lossesRanked,
            int winsFriendly,
            int lossesFriendly,
            String selectedCharacter
    ) {
    }

    /**
     * Un joueur, avec tous ses decks.
     *
     * @param rank sa place parmi les joueurs classés, ou null s'il n'a jamais disputé de duel classé
     */
    public record PlayerDetail(
            Integer rank,
            Integer leaderboardRank,
            UUID userId,
            String displayName,
            String email,
            String department,
            int elo,
            int winsRanked,
            int lossesRanked,
            int winsFriendly,
            int lossesFriendly,
            String selectedCharacter,
            /** La relique de base que ce personnage apporte en duel. */
            String characterRelic,
            List<Deck> decks
    ) {
    }

    /**
     * Un deck, ses cartes regroupées.
     *
     * @param active le deck qu'un duel utilise pour ce personnage. À défaut de deck marqué actif,
     *               le combat prend le plus récent ; {@code usedInBattle} dit lequel c'est vraiment.
     */
    public record Deck(
            UUID id,
            String name,
            String selectedCharacter,
            boolean active,
            boolean usedInBattle,
            int cardCount,
            Instant updatedAt,
            List<DeckCard> cards
    ) {
    }

    /** Une carte et combien d'exemplaires le deck en porte. */
    public record DeckCard(String cardId, int count) {
    }
}
