package fr.insastronaute.webapi.shop.dto;

import fr.insastronaute.webapi.shop.domain.ShopOrderStatus;
import jakarta.validation.constraints.NotNull;

public record ShopOrderStatusRequest(@NotNull ShopOrderStatus status) {
}
