package fr.insastronaute.webapi.shop.controller;

import fr.insastronaute.webapi.shop.domain.ShopOrderStatus;
import fr.insastronaute.webapi.shop.dto.*;
import fr.insastronaute.webapi.shop.service.ShopManualSaleService;
import fr.insastronaute.webapi.shop.service.ShopOrderService;
import fr.insastronaute.webapi.shop.service.ShopProductImageService;
import fr.insastronaute.webapi.shop.service.ShopProductService;
import fr.insastronaute.webapi.shop.service.ShopReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/shop")
@Tag(name = "Admin Shop")
public class AdminShopController {
    private final ShopProductService productService;
    private final ShopProductImageService imageService;
    private final ShopOrderService orderService;
    private final ShopReservationService reservationService;
    private final ShopManualSaleService manualSaleService;

    public AdminShopController(ShopProductService productService, ShopProductImageService imageService,
                               ShopOrderService orderService, ShopReservationService reservationService,
                               ShopManualSaleService manualSaleService) {
        this.productService = productService;
        this.imageService = imageService;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.manualSaleService = manualSaleService;
    }

    @GetMapping("/products") public List<ShopProductDto> listProducts() { return productService.list(); }
    @GetMapping("/products/{productId}") public ShopProductDto getProduct(@PathVariable UUID productId) { return productService.get(productId); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) public ShopProductDto createProduct(@Valid @RequestBody ShopProductRequest request) { return productService.create(request); }
    @PutMapping("/products/{productId}") public ShopProductDto updateProduct(@PathVariable UUID productId, @Valid @RequestBody ShopProductRequest request) { return productService.update(productId, request); }
    @DeleteMapping("/products/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProduct(@PathVariable UUID productId) { productService.delete(productId); }

    @PostMapping(path = "/products/{productId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a shop product photo")
    public ShopProductPhotoDto uploadPhoto(@PathVariable UUID productId, @RequestParam MultipartFile file) { return imageService.upload(productId, file); }

    @PutMapping("/products/{productId}/photos/order")
    public List<ShopProductPhotoDto> reorderPhotos(@PathVariable UUID productId, @Valid @RequestBody ShopPhotoOrderRequest request) { return imageService.reorder(productId, request.photoIds()); }

    @DeleteMapping("/products/{productId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable UUID productId, @PathVariable UUID photoId) { imageService.delete(productId, photoId); }

    @GetMapping("/products/sales")
    @Operation(summary = "Sales per product, broken down by pre-order status")
    public List<ShopProductSalesDto> productSales() { return productService.sales(); }

    @GetMapping("/manual-sales")
    @Operation(summary = "Counter sales, most recent first")
    public List<ShopManualSaleDto> listManualSales() { return manualSaleService.list(); }

    @PostMapping("/manual-sales")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a counter sale", description = "No buyer account: the unit leaves the stock all the same.")
    public ShopManualSaleDto recordManualSale(
            @Valid @RequestBody ShopManualSaleRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return manualSaleService.record(request, oidcUser == null ? null : oidcUser.getSubject());
    }

    @DeleteMapping("/manual-sales/{saleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Undo a counter sale recorded by mistake")
    public void deleteManualSale(@PathVariable UUID saleId) { manualSaleService.delete(saleId); }

    @GetMapping("/orders")
    public List<ShopOrderDto> listOrders(@RequestParam(required = false) ShopOrderStatus status) { return orderService.listAdmin(status); }

    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a cancelled pre-order for good",
            description = "Only a cancelled order can be deleted. The row disappears from every listing, "
                    + "recap and export; the admin registry keeps what it contained."
    )
    public void deleteOrder(@PathVariable UUID orderId, @AuthenticationPrincipal OidcUser oidcUser) {
        orderService.delete(orderId, oidcUser == null ? null : oidcUser.getSubject());
    }

    @PutMapping("/orders/{orderId}/status")
    public ShopOrderDto changeStatus(@PathVariable UUID orderId, @Valid @RequestBody ShopOrderStatusRequest request) { return orderService.changeStatus(orderId, request.status()); }

    @PutMapping("/orders/{orderId}/reservation-slot")
    public ShopOrderDto changeOrderReservationSlot(@PathVariable UUID orderId,
                                                    @Valid @RequestBody ShopOrderSlotRequest request) {
        return orderService.changeReservationSlotAdmin(orderId, request.slotId());
    }

    @GetMapping("/reservation-slots")
    public List<ShopReservationSlotDto> listReservationSlots() { return reservationService.listAdmin(); }

    @GetMapping("/reservation-events")
    @Operation(summary = "List events eligible for a shop pickup slot")
    public List<ShopReservationEventDto> listReservationEvents() { return reservationService.listEventOptions(); }

    @PostMapping("/reservation-slots/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ShopReservationSlotDto createEventReservation(@Valid @RequestBody ShopEventReservationRequest request) {
        return reservationService.createEvent(request);
    }

    @PostMapping("/reservation-slots/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public ShopReservationSlotDto createCustomReservation(@Valid @RequestBody ShopCustomReservationRequest request) {
        return reservationService.createCustom(request);
    }

    @PostMapping("/reservation-series")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ShopReservationSlotDto> createReservationSeries(@Valid @RequestBody ShopRecurringReservationRequest request) {
        return reservationService.createRecurring(request);
    }

    @PutMapping("/reservation-slots/{slotId}")
    public ShopReservationSlotDto updateReservationSlot(@PathVariable UUID slotId,
                                                         @Valid @RequestBody ShopReservationSlotUpdateRequest request) {
        return reservationService.update(slotId, request);
    }

    @DeleteMapping("/reservation-slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateReservationSlot(@PathVariable UUID slotId) { reservationService.deactivate(slotId); }

    @PutMapping("/reservation-slots/{slotId}/activate")
    public ShopReservationSlotDto activateReservationSlot(@PathVariable UUID slotId) {
        return reservationService.activate(slotId);
    }

    @DeleteMapping("/reservation-series/{seriesId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReservationSeries(@PathVariable UUID seriesId) { reservationService.deleteSeries(seriesId); }

    @GetMapping("/settings")
    public ShopSettingsDto getSettings() { return reservationService.getSettings(); }

    @PutMapping("/settings")
    public ShopSettingsDto updateSettings(@Valid @RequestBody ShopSettingsRequest request) {
        return reservationService.updateSettings(request);
    }
}
