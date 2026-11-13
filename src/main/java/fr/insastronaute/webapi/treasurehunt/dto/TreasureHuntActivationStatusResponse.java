package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;

/**
 * @param active la chasse accepte les validations
 * @param visible la chasse apparaît dans le front joueur — indépendant de
 *                l'activation, qu'une simple retouche de contenu suffit à couper
 */
public record TreasureHuntActivationStatusResponse(boolean active, boolean visible, List<String> violations) { }
