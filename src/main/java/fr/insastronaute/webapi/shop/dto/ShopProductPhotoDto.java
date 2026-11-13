package fr.insastronaute.webapi.shop.dto;

import java.util.UUID;

public record ShopProductPhotoDto(UUID id, String url, int displayOrder) { }
