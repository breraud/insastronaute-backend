package fr.insastronaute.webapi.shop.controller;

import fr.insastronaute.webapi.shop.dto.*;
import fr.insastronaute.webapi.shop.service.ShopCartService;
import fr.insastronaute.webapi.shop.service.ShopOrderService;
import fr.insastronaute.webapi.shop.service.ShopProductService;
import fr.insastronaute.webapi.shop.service.ShopReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shop")
@Tag(name = "Shop")
public class ShopController {
    private final ShopProductService productService;
    private final ShopCartService cartService;
    private final ShopOrderService orderService;
    private final ShopReservationService reservationService;

    public ShopController(ShopProductService productService, ShopCartService cartService, ShopOrderService orderService,
                          ShopReservationService reservationService) {
        this.productService = productService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.reservationService = reservationService;
    }

    @GetMapping("/products")
    @Operation(summary = "List shop products")
    public List<ShopProductDto> listProducts() { return productService.list(); }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get a shop product")
    public ShopProductDto getProduct(@PathVariable UUID productId) { return productService.get(productId); }

    @GetMapping("/cart")
    @Operation(summary = "Get my cart")
    public ShopCartDto getCart(@AuthenticationPrincipal OidcUser user) { return cartService.get(user.getSubject()); }

    @PutMapping("/cart/items/{productId}")
    @Operation(summary = "Set a product quantity in my cart")
    public ShopCartDto setQuantity(@PathVariable UUID productId, @Valid @RequestBody ShopCartLineRequest request, @AuthenticationPrincipal OidcUser user) {
        return cartService.setQuantity(user.getSubject(), productId, request.quantity());
    }

    @DeleteMapping("/cart/items/{productId}")
    @Operation(summary = "Remove a product from my cart")
    public ShopCartDto removeItem(@PathVariable UUID productId, @AuthenticationPrincipal OidcUser user) {
        return cartService.setQuantity(user.getSubject(), productId, 0);
    }

    @GetMapping("/orders")
    @Operation(summary = "List my preorders")
    public List<ShopOrderDto> listOrders(@AuthenticationPrincipal OidcUser user) { return orderService.listMine(user.getSubject()); }

    @GetMapping("/reservation-slots")
    @Operation(summary = "List future reservation slots")
    public List<ShopReservationSlotDto> listReservationSlots() { return reservationService.listAvailable(); }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit my cart as a preorder")
    public ShopOrderDto submit(@Valid @RequestBody ShopOrderRequest request, @AuthenticationPrincipal OidcUser user) {
        return orderService.submit(user.getSubject(), request.slotId());
    }

    @PutMapping("/orders/{orderId}/reservation-slot")
    @Operation(summary = "Change my preorder reservation slot")
    public ShopOrderDto changeReservationSlot(@PathVariable UUID orderId,
                                               @Valid @RequestBody ShopOrderSlotRequest request,
                                               @AuthenticationPrincipal OidcUser user) {
        return orderService.changeReservationSlot(orderId, request.slotId(), user.getSubject());
    }

    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel my pending preorder")
    public void cancel(@PathVariable UUID orderId, @AuthenticationPrincipal OidcUser user) { orderService.cancel(orderId, user.getSubject()); }
}
