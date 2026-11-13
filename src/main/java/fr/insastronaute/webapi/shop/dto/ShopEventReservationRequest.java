package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ShopEventReservationRequest(
        @NotNull UUID eventId,
        @Size(max = 1000) String note
) {}
