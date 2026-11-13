package fr.insastronaute.webapi.shop.dto;

import java.util.List;
import java.util.UUID;

public record ShopOrderLineDto(
        UUID productId,
        String productName,
        Long unitPriceCents,
        boolean productAvailable,
        boolean outOfStock,
        List<ShopProductPhotoDto> photos,
        int quantity,
        Long lineTotalCents
) {
}
