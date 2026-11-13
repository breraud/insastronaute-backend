package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ShopOrderRequest(@NotNull UUID slotId) {}
