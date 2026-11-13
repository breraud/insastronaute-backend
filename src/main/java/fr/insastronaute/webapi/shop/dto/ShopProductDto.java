package fr.insastronaute.webapi.shop.dto;

import java.util.List;
import java.util.UUID;

/**
 * Un produit de la boutique.
 *
 * @param stock       le nombre d'exemplaires a vendre, ou null quand rien ne le borne
 * @param maxPerOrder le plafond par commande, ou null
 * @param remaining   ce qu'il reste vraiment : le stock moins les precommandes qui
 *                    tiennent. Null quand le stock ne l'est pas — il n'y a alors rien a
 *                    decompter, et renvoyer un nombre laisserait croire a une reserve.
 *                    Pour un lot, c'est le minimum de ce que ses composants permettent.
 * @param bundleItems ce que le lot contient, vide pour un produit ordinaire
 */
public record ShopProductDto(UUID id, String name, long priceCents, boolean outOfStock, Integer stock,
                             Integer maxPerOrder, Integer remaining, List<ShopBundleItemDto> bundleItems,
                             UUID articleId, String articleTitle,
                             List<ShopProductPhotoDto> photos) { }
