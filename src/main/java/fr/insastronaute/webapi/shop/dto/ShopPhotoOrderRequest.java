package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ShopPhotoOrderRequest(@NotNull List<UUID> photoIds) {
}
