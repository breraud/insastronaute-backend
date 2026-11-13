package fr.insastronaute.webapi.shop.dto;

import java.time.Instant;
import java.util.UUID;

/** The event fields the shop needs to create a pickup slot, and nothing else. */
public record ShopReservationEventDto(
        UUID id,
        String title,
        Instant startsAt,
        Instant endsAt,
        String location
) {
}
