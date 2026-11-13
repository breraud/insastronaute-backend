package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ShopProductRequest(
        @NotBlank @Size(max = 180) String name,
        @NotNull @PositiveOrZero Long priceCents,
        Boolean outOfStock,
        // Null vaut « pas de limite » des deux cotes : un produit sans stock renseigne se
        // commande sans plafond, comme avant que ces champs existent.
        @PositiveOrZero Integer stock,
        @Positive Integer maxPerOrder,
        // Le contenu du lot. Vide ou absent : ce produit n'est pas un lot.
        @Valid List<ShopBundleItemRequest> bundleItems,
        UUID articleId
) {
}
