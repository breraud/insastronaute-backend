package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Une vente au comptoir a enregistrer.
 *
 * <p>Pas de joueur : c'est ce qui la distingue d'une precommande. Le prix n'est pas
 * fourni non plus — il est repris du produit au moment de la vente, pour qu'une saisie
 * ne puisse pas inventer un tarif.</p>
 */
public record ShopManualSaleRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) @Max(1000) Integer quantity,
        @Size(max = 280) String note
) {
}
