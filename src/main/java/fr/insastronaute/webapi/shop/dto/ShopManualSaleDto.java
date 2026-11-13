package fr.insastronaute.webapi.shop.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une vente au comptoir, telle qu'elle se relit.
 *
 * @param recordedBy qui a encaisse, ou null si son compte a depuis disparu — la vente,
 *                   elle, a bien eu lieu
 */
public record ShopManualSaleDto(
        UUID id,
        UUID productId,
        String productName,
        int quantity,
        long unitPriceCents,
        long totalCents,
        String note,
        String recordedBy,
        Instant soldAt
) {
}
