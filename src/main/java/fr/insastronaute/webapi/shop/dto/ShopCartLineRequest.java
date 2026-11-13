package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShopCartLineRequest(
        @NotNull @Min(0) @Max(100) Integer quantity
) {
}
