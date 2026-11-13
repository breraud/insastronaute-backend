package fr.insastronaute.webapi.shop.dto;

import fr.insastronaute.webapi.shop.domain.ShopReservationSlotType;

import java.time.Instant;
import java.util.UUID;

public record ShopReservationSlotDto(
        UUID id,
        ShopReservationSlotType type,
        UUID eventId,
        String eventTitle,
        UUID seriesId,
        Instant startsAt,
        Instant endsAt,
        String location,
        String note,
        boolean active
) {}
