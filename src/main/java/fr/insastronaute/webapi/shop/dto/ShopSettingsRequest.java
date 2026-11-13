package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ShopSettingsRequest(
        @NotNull @PositiveOrZero Integer lockDelayMinutes,
        // Null vaut « pas de limite ». Zero serait une boutique fermee sans le dire.
        @Positive Integer maxOrdersPerUser,
        @Positive Integer maxItemsPerOrder
) {}
