package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ShopReservationSlotUpdateRequest(
        Instant startsAt,
        Instant endsAt,
        @Size(max = 255) String location,
        @Size(max = 1000) String note
) {}
