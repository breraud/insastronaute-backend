package fr.insastronaute.webapi.sts.dto;

/**
 * Ce qu'on demande au feu de camp.
 *
 * @param action        HEAL ou ENCHANT
 * @param cardInstanceId la carte à enchanter ; ignoré par HEAL
 * @param charges       les charges à dépenser ; ignoré par HEAL, qui soigne tant
 *                      qu'il en reste et que le joueur n'est pas au maximum
 */
public record StsRestActionRequest(
        String action,
        String cardInstanceId,
        Integer charges
) {
}
