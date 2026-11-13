package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record ShopRecurringReservationRequest(
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startsAt,
        @NotNull LocalTime endsAt,
        @NotBlank @Size(max = 255) String location,
        @Size(max = 1000) String note
) {}
