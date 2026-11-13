package fr.insastronaute.webapi.sts.multiplayer.dto;

import java.util.UUID;

/**
 * Un participant d'une bataille, tel qu'il est servi aux clients.
 *
 * <p>Tous ne sont pas des joueurs, depuis que le multijoueur a des formats en equipe : une place
 * restee vide d'un 2v2 peut etre tenue par un renfort que le serveur joue, et l'adversaire d'un
 * raid est un boss qui n'a pas de compte. Ceux-la n'ont pas d'{@code userId}.
 *
 * @param combatantId le nom que le moteur donne a ce combattant. Pour un joueur c'est son
 *                    identifiant d'utilisateur ; pour un combattant pilote par le serveur, un
 *                    identifiant a lui. C'est par ce nom que le client relie l'etat autoritatif
 *                    aux personnages qu'il affiche, donc il doit voyager meme quand il vaut
 *                    l'identifiant d'utilisateur.
 * @param ai          vrai quand ce siege est joue par le serveur plutot que par une personne
 * @param bossEnemyId l'ennemi des donnees de jeu dont ce boss est tire, ou null. Le client s'en
 *                    sert pour le portrait : un boss n'a pas de personnage choisi, et prendrait
 *                    sinon celui du personnage par defaut.
 */
public record StsPvpParticipantDto(
        UUID userId,
        String combatantId,
        String displayName,
        String department,
        String selectedCharacter,
        boolean ai,
        String bossEnemyId
) {
}
