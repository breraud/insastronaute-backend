package fr.insastronaute.webapi.shop.dto;

import java.util.List;
import java.util.UUID;

public record ShopCartDto(
        UUID id,
        List<ShopCartLineDto> lines,
        long totalPriceCents
) {
}
