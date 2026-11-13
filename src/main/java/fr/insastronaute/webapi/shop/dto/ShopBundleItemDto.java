package fr.insastronaute.webapi.shop.dto;

import java.util.UUID;

/** Un produit et sa quantite dans un lot. */
public record ShopBundleItemDto(UUID productId, String productName, int quantity) {
}
