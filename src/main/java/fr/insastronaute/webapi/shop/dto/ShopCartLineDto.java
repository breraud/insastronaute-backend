package fr.insastronaute.webapi.shop.dto;

import java.util.List;
import java.util.UUID;

public record ShopCartLineDto(
        UUID productId,
        String productName,
        long unitPriceCents,
        boolean outOfStock,
        List<ShopProductPhotoDto> photos,
        int quantity,
        long lineTotalCents,
        /** Le plafond par commande du produit, ou null quand il n'en a pas. */
        Integer maxPerOrder,
        /** Ce qu'il reste a vendre, ou null quand aucun stock n'est pose. */
        Integer remaining
) {
}
