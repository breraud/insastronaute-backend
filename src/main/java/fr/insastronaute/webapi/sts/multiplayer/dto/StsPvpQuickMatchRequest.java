package fr.insastronaute.webapi.sts.multiplayer.dto;

/**
 * @param mode        le format cherche : {@code ONE_V_ONE}, {@code TWO_V_TWO} ou {@code RAID}.
 *                    Absent, c'est un duel — ce que demandait tout client ecrit avant les modes.
 * @param fillWithAi  autorise l'ouverture du combat avec des renforts que le serveur joue sur les
 *                    sieges restes vides. C'est ce qui rend un 2v2 essayable sans reunir quatre
 *                    joueurs, et c'est demande explicitement plutot que fait d'office : arriver
 *                    dans une equipe de robots sans l'avoir voulu serait une surprise.
 */
public record StsPvpQuickMatchRequest(
        Boolean friendly,
        Boolean skipMatchmaking,
        String mode,
        Boolean fillWithAi
) {
}
