package fr.insastronaute.webapi.shop.dto;

public record ShopSettingsDto(int lockDelayMinutes, Integer maxOrdersPerUser, Integer maxItemsPerOrder) {}
