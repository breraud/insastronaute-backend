package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.UUID;

/**
 * Une tentative de saisie.
 *
 * Aucune contrainte de presence : un corps vide ou incomplet doit se faire refuser comme
 * un secret faux. Un @NotBlank rendrait un 400 d'une autre forme, qui designerait l'outil.
 * pathId n'est plus envoye par le front ; present, il reste applique comme filtre.
 *
 * code est le nom que l'ancien front envoie encore a la place de secret. Le backend se
 * deploie a la main et le front par Cloudflare Pages : les deux ne partent jamais
 * ensemble, donc un ancien client doit continuer de fonctionner le temps du rattrapage.
 * Ce champ se retire une fois les deux fronts a jour.
 */
public record CheckpointValidationRequest(String tool, String secret, String code, UUID pathId) {
    /** secret est prioritaire ; code n'est lu que si secret est absent. */
    public String effectiveSecret() { return secret != null ? secret : code; }
}
