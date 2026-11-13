package fr.insastronaute.webapi.shop.dto;

import fr.insastronaute.webapi.shop.domain.ShopOrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShopOrderDto(
        UUID id,
        ShopOrderUserDto user,
        ShopOrderStatus status,
        List<ShopOrderLineDto> lines,
        Long totalPriceCents,
        boolean hasUnavailableProducts,
        ShopReservationSlotDto slot,
        Instant lockAt,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShopOrderDto empty(UUID id) {
        return new ShopOrderDto(id, null, ShopOrderStatus.PENDING, List.of(), 0L, false, null, null, false, null, null);
    }
}
