package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShopBundleItemRequest(@NotNull UUID productId, @NotNull @Min(1) @Max(100) Integer quantity) {
}
