package fr.insastronaute.webapi.shop.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ShopOrderSlotRequest(@NotNull UUID slotId) {}
