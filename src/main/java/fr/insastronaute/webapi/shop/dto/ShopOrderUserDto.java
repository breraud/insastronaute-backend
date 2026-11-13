package fr.insastronaute.webapi.shop.dto;

import java.util.UUID;

public record ShopOrderUserDto(UUID id, String displayName, String email) {
}
