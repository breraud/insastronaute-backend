package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.repository.ShopCartRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopCartServiceTest {
 @Mock UserRepository userRepository; @Mock ShopCartRepository cartRepository; @Mock ShopProductRepository productRepository;
 @Mock ShopStockService stockService;

 @Test void setQuantityRejectsMoreThanOneHundredItems() {
  ShopCartService service = new ShopCartService(userRepository, cartRepository, productRepository, stockService);

  assertThatThrownBy(() -> service.setQuantity("sub", UUID.randomUUID(), 101))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("La quantité ne peut pas dépasser 100.");
 }

 @Test void addRejectsOutOfStockProduct() {
  AppUser user = new AppUser(); user.setKeycloakSub("sub"); ShopProduct product = new ShopProduct(); product.setOutOfStock(true);
  when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user)); when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
  ShopCartService service = new ShopCartService(userRepository, cartRepository, productRepository, stockService);
  assertThatThrownBy(() -> service.setQuantity("sub", product.getId(), 1)).isInstanceOf(BadRequestException.class).hasMessage("Produit indisponible.");
 }
}
