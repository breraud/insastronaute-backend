package fr.insastronaute.webapi.sts.multiplayer.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * Un siège d'une bataille multijoueur : qui l'occupe, dans quelle équipe, et qui le joue.
 *
 * <p>Ce type remplace la convention qui rendait le 1v1 indépassable. La couche PVP lisait les
 * équipes en ne prenant que {@code team.get(0)}, et se servait de l'indice d'équipe comme d'un
 * indice de joueur : « l'équipe 0 » et « le joueur 0 » étaient le même entier. Tout en dépendait
 * — le règlement du combat, le classement, la diffusion — et chacun de ces endroits aurait eu à
 * réapprendre séparément qu'une équipe peut compter deux joueurs. Ils lisent maintenant une liste
 * de sièges où l'équipe est un champ parmi d'autres.
 *
 * <p>Un siège n'est pas toujours un joueur. Une place vide d'un 2v2 peut être tenue par un renfort
 * que le serveur joue, et l'adversaire d'un raid est un boss qui n'a pas de compte : ces deux-là
 * n'ont ni {@code userId} ni {@code keycloakSub}, ce qui est exactement la question que se pose
 * tout ce qui envoie quelque chose à un joueur — un siège sans destinataire n'en reçoit pas.
 */
public record StsPvpSeat(
        String combatantId,
        UUID userId,
        String keycloakSub,
        String displayName,
        String selectedCharacter,
        int teamIndex,
        boolean ai,
        String bossEnemyId,
        UUID deckOwnerUserId,
        double bossHpMultiplier
) {

    public StsPvpSeat {
        Objects.requireNonNull(combatantId, "combatantId");
        if (combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId must not be blank");
        }
        if (teamIndex < 0) {
            throw new IllegalArgumentException("teamIndex must not be negative");
        }
        if (!ai && userId == null) {
            throw new IllegalArgumentException("a human seat must name its player");
        }
        // Un multiplicateur nul ou négatif donnerait un boss à 1 PV : c'est une faute de saisie,
        // pas un réglage, et elle retombe sur l'échelle ordinaire.
        if (!(bossHpMultiplier > 0d) || Double.isInfinite(bossHpMultiplier)) {
            bossHpMultiplier = 1d;
        }
    }

    /** Un siège tenu par un joueur, dont le combattant porte l'identifiant d'utilisateur. */
    public static StsPvpSeat human(
            UUID userId, String keycloakSub, String displayName, String selectedCharacter, int teamIndex) {
        Objects.requireNonNull(userId, "userId");
        return new StsPvpSeat(
                userId.toString(), userId, keycloakSub, displayName, selectedCharacter,
                teamIndex, false, null, null, 1d);
    }

    /**
     * Un renfort que le serveur joue, avec le deck de {@code deckOwnerUserId}.
     *
     * <p>Le renfort emprunte le deck d'un joueur de son équipe plutôt que d'en avoir un à lui :
     * cela lui garantit un deck légal, complet et fait de cartes que le catalogue connaît, sans
     * qu'il faille écrire du contenu avant de pouvoir essayer le mode. C'est un choix de
     * remplissage, pas une règle de jeu — le jour où les renforts auront leurs propres decks, il
     * n'y a que cette ligne et {@code activeDeckCardIds} à changer.
     */
    public static StsPvpSeat aiAlly(
            String combatantId, String displayName, String selectedCharacter,
            int teamIndex, UUID deckOwnerUserId) {
        return new StsPvpSeat(
                combatantId, null, null, displayName, selectedCharacter,
                teamIndex, true, null, Objects.requireNonNull(deckOwnerUserId, "deckOwnerUserId"), 1d);
    }

    /**
     * Le boss d'un raid, tiré des données de jeu comme n'importe quel ennemi de run, avec l'échelle
     * de points de vie propre à sa rencontre.
     *
     * @param hpMultiplier ce par quoi la rencontre multiplie les PV déjà mis à l'échelle du nombre
     *                     de joueurs ; {@code 1} garde l'échelle ordinaire
     */
    public static StsPvpSeat boss(
            String combatantId, String displayName, String bossEnemyId, int teamIndex, double hpMultiplier) {
        return new StsPvpSeat(
                combatantId, null, null, displayName, null,
                teamIndex, true, Objects.requireNonNull(bossEnemyId, "bossEnemyId"), null, hpMultiplier);
    }

    /** Vrai pour un siège auquel le serveur a quelque chose à envoyer, c'est-à-dire un joueur. */
    public boolean isReachablePlayer() {
        return !ai && keycloakSub != null && !keycloakSub.isBlank();
    }

    public boolean isBoss() {
        return bossEnemyId != null;
    }

    /** Le personnage du siège, {@code EP} par défaut, comme partout ailleurs en PVP. */
    public String characterOrDefault() {
        return selectedCharacter == null || selectedCharacter.isBlank() ? "EP" : selectedCharacter;
    }
}
