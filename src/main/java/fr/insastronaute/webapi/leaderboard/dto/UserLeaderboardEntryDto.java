package fr.insastronaute.webapi.leaderboard.dto;

import java.util.List;

/**
 * Une ligne de classement.
 *
 * <p>{@code you} et {@code friend} sont calcules ici plutot que devines au nom :
 * une ligne d'equipe porte le nom de l'equipe, et le front ne peut plus s'y
 * reconnaitre en comparant des chaines. Deux joueurs homonymes s'y trompaient
 * deja tous les deux.</p>
 *
 * @param members les joueurs derriere la ligne quand c'est une equipe, vide sinon.
 *                Le nom d'une equipe ne designe personne : cliquer dessus cherchait
 *                un joueur de ce nom et n'en trouvait aucun. La liste dit qui elle
 *                represente, sans quoi la ligne reste une impasse.
 */
public record UserLeaderboardEntryDto(
        Integer rank,
        String name,
        String department,
        Long score,
        boolean you,
        boolean friend,
        List<String> members
) {
}
