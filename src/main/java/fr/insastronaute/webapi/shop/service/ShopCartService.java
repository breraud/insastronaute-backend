package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.shop.domain.*;
import fr.insastronaute.webapi.shop.repository.*;
import fr.insastronaute.webapi.shop.dto.ShopCartDto;
import fr.insastronaute.webapi.shop.dto.ShopCartLineDto;
import fr.insastronaute.webapi.shop.dto.ShopProductPhotoDto;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;

@Service
public class ShopCartService {
 private final UserRepository users; private final ShopCartRepository carts; private final ShopProductRepository products;
 private final ShopStockService stock;
 public ShopCartService(UserRepository users, ShopCartRepository carts, ShopProductRepository products, ShopStockService stock) { this.users=users; this.carts=carts; this.products=products; this.stock=stock; }
 @Transactional(readOnly = true) public ShopCartDto get(String sub) {
  AppUser user = currentUser(sub);
  return carts.findByUserId(user.getId()).map(this::toDto).orElseGet(() -> new ShopCartDto(null, List.of(), 0));
 }
 @Transactional public ShopCartDto setQuantity(String sub, UUID productId, int quantity) {
  if (quantity < 0) throw new BadRequestException("La quantité doit être positive.");
  if (quantity > 100) throw new BadRequestException("La quantité ne peut pas dépasser 100.");
  AppUser user=currentUser(sub);
  ShopProduct product=products.findById(productId).orElseThrow(()->new NotFoundException("Produit introuvable."));
  if(quantity > 0 && product.isOutOfStock()) throw new BadRequestException("Produit indisponible.");
  ShopCart cart=carts.findByUserId(user.getId()).orElseGet(()->{ ShopCart created=new ShopCart(); created.setUser(user); return created; });
  // Les limites se disent des le panier, pas seulement au moment de valider : laisser
  // quelqu'un composer sa commande pour la lui refuser a la fin lui fait tout refaire.
  // Le controle de la validation reste, lui, celui qui fait foi — voir ShopOrderService.
  if (quantity > 0) { ensureWithinProductLimits(product, quantity); ensureWithinCartLimit(cart, productId, quantity); }
  ShopCartLine line=cart.getLines().stream().filter(item->item.getProduct().getId().equals(productId)).findFirst().orElse(null);
  if(quantity==0 && line!=null) cart.getLines().remove(line); else if(quantity>0 && line==null) { line=new ShopCartLine(); line.setCart(cart); line.setProduct(product); line.setQuantity(quantity); cart.getLines().add(line); } else if(line!=null) line.setQuantity(quantity);
  return toDto(carts.save(cart));
 }

 /** Le plafond par commande du produit, et ce qu'il en reste a vendre. */
 private void ensureWithinProductLimits(ShopProduct product, int quantity) {
  Integer maxPerOrder = product.getMaxPerOrder();
  if (maxPerOrder != null && quantity > maxPerOrder) {
   throw new BadRequestException("« " + product.getName() + " » est limité à " + maxPerOrder
           + (maxPerOrder > 1 ? " exemplaires par commande." : " exemplaire par commande."));
  }
  Integer remaining = stock.remaining(product, stock.orderedByProduct());
  if (remaining != null && quantity > remaining) {
   throw new BadRequestException(remaining == 0
           ? "« " + product.getName() + " » n'est plus disponible."
           : "Il ne reste que " + remaining + " « " + product.getName() + " ».");
  }
 }

 /** Le nombre total d'articles que la boutique autorise dans une commande. */
 private void ensureWithinCartLimit(ShopCart cart, UUID productId, int quantity) {
  Integer maxItems = stock.settings().getMaxItemsPerOrder();
  if (maxItems == null) return;
  int total = quantity + cart.getLines().stream()
          .filter(line -> !line.getProduct().getId().equals(productId))
          .mapToInt(ShopCartLine::getQuantity).sum();
  if (total > maxItems) {
   throw new BadRequestException(maxItems + (maxItems > 1 ? " articles maximum par commande." : " article maximum par commande."));
  }
 }

 private AppUser currentUser(String sub) { return users.findByKeycloakSub(sub).orElseThrow(()->new NotFoundException("Utilisateur introuvable.")); }
 private ShopCartDto toDto(ShopCart cart) {
  Map<UUID, Long> ordered = stock.orderedByProduct();
  List<ShopCartLineDto> lines = cart.getLines().stream().map(line -> {
   ShopProduct product = line.getProduct();
   List<ShopProductPhotoDto> photos = product.getPhotos().stream().sorted(Comparator.comparingInt(ShopProductPhoto::getDisplayOrder)).map(photo -> new ShopProductPhotoDto(photo.getId(), photo.getUrl(), photo.getDisplayOrder())).toList();
   return new ShopCartLineDto(product.getId(), product.getName(), product.getPriceCents(), product.isOutOfStock(), photos, line.getQuantity(), Math.multiplyExact(product.getPriceCents(), line.getQuantity()), product.getMaxPerOrder(), stock.remaining(product, ordered));
  }).toList();
  return new ShopCartDto(cart.getId(), lines, lines.stream().mapToLong(ShopCartLineDto::lineTotalCents).sum());
 }
}
