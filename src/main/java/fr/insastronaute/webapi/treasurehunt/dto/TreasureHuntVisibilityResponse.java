package fr.insastronaute.webapi.treasurehunt.dto;

/**
 * Ce que le front joueur a besoin de savoir avant même d'afficher une tuile :
 * la chasse existe-t-elle pour lui ? Rien d'autre n'est exposé — un joueur n'a
 * pas à apprendre qu'une chasse cachée est par ailleurs prête à démarrer.
 */
public record TreasureHuntVisibilityResponse(boolean visible) { }
