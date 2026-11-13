package fr.insastronaute.webapi.shop.controller;

import fr.insastronaute.webapi.shop.domain.ShopOrderStatus;
import fr.insastronaute.webapi.shop.dto.ShopOrderDto;
import fr.insastronaute.webapi.shop.dto.ShopProductPhotoDto;
import fr.insastronaute.webapi.shop.dto.ShopReservationSlotDto;
import fr.insastronaute.webapi.shop.dto.ShopReservationEventDto;
import fr.insastronaute.webapi.shop.dto.ShopSettingsDto;
import fr.insastronaute.webapi.shop.domain.ShopReservationSlotType;
import fr.insastronaute.webapi.shop.service.ShopOrderService;
import fr.insastronaute.webapi.shop.service.ShopProductImageService;
import fr.insastronaute.webapi.shop.service.ShopProductService;
import fr.insastronaute.webapi.shop.service.ShopReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminShopControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ShopProductService productService;
    @MockitoBean ShopProductImageService imageService;
    @MockitoBean ShopOrderService orderService;
    @MockitoBean ShopReservationService reservationService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanListAndValidateOrders() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.listAdmin(null)).thenReturn(List.of(ShopOrderDto.empty(orderId)));

        mockMvc.perform(get("/api/admin/shop/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/shop/orders/{orderId}/status", orderId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALIDATED\"}"))
                .andExpect(status().isOk());
        verify(orderService).changeStatus(orderId, ShopOrderStatus.VALIDATED);
    }

    @Test
    void adminCanOverrideOrderReservationSlot() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        when(orderService.changeReservationSlotAdmin(orderId, slotId)).thenReturn(ShopOrderDto.empty(orderId));

        mockMvc.perform(put("/api/admin/shop/orders/{orderId}/reservation-slot", orderId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slotId + "\"}"))
                .andExpect(status().isOk());
        verify(orderService).changeReservationSlotAdmin(orderId, slotId);
    }

    @Test
    void adminCanFilterOrdersByStatus() throws Exception {
        when(orderService.listAdmin(ShopOrderStatus.PENDING)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/shop/orders")
                        .param("status", "PENDING")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(orderService).listAdmin(ShopOrderStatus.PENDING);
    }

    @Test
    void adminCanDeleteProductPhoto() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/shop/products/{productId}/photos/{photoId}", productId, photoId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(imageService).delete(productId, photoId);
    }

    @Test
    void adminCanUploadProductPhoto() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3}
        );
        when(imageService.upload(org.mockito.ArgumentMatchers.eq(productId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ShopProductPhotoDto(photoId, "https://s3.example.test/public/shop/photo.png", 0));

        mockMvc.perform(multipart("/api/admin/shop/products/{productId}/photos", productId)
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(photoId.toString()))
                .andExpect(jsonPath("$.url").value("https://s3.example.test/public/shop/photo.png"));
    }

    @Test
    void nonAdminCannotReadOrders() throws Exception {
        mockMvc.perform(get("/api/admin/shop/orders").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void goodiesRolesCanAccessShopButNotOtherAdminApis() throws Exception {
        when(orderService.listAdmin(null)).thenReturn(List.of());
        UUID eventId = UUID.randomUUID();
        when(reservationService.listEventOptions()).thenReturn(List.of(new ShopReservationEventDto(
                eventId, "Gala", Instant.parse("2027-02-02T17:00:00Z"),
                Instant.parse("2027-02-02T19:00:00Z"), "Hall A")));

        mockMvc.perform(get("/api/admin/shop/orders")
                        .with(user("respo-goodies").roles("RESPO_GOODIES")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/shop/orders")
                        .with(user("sous-respo-goodies").roles("SOUS_RESPO_GOODIES")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/shop/reservation-events")
                        .with(user("respo-goodies").roles("RESPO_GOODIES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].title").value("Gala"));
        mockMvc.perform(get("/api/admin/shop/reservation-events")
                        .with(user("sous-respo-goodies").roles("SOUS_RESPO_GOODIES")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/test")
                        .with(user("respo-goodies").roles("RESPO_GOODIES")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/test")
                        .with(user("sous-respo-goodies").roles("SOUS_RESPO_GOODIES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreateInvalidProduct() throws Exception {
        mockMvc.perform(post("/api/admin/shop/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"priceCents\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    @Test
    void adminCanManageReservationSlotsAndSettings() throws Exception {
        UUID slotId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        ShopReservationSlotDto slot = new ShopReservationSlotDto(
                slotId, ShopReservationSlotType.CUSTOM, null, null, null,
                Instant.parse("2027-02-02T17:00:00Z"), Instant.parse("2027-02-02T19:00:00Z"),
                "Bar INSA", "Début de soirée", true
        );
        when(reservationService.listAdmin()).thenReturn(List.of(slot));
        when(reservationService.createEvent(org.mockito.ArgumentMatchers.any())).thenReturn(slot);
        when(reservationService.createCustom(org.mockito.ArgumentMatchers.any())).thenReturn(slot);
        when(reservationService.createRecurring(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(slot));
        when(reservationService.update(org.mockito.ArgumentMatchers.eq(slotId), org.mockito.ArgumentMatchers.any())).thenReturn(slot);
        when(reservationService.activate(slotId)).thenReturn(slot);
        when(reservationService.getSettings()).thenReturn(new ShopSettingsDto(1440, null, null));
        when(reservationService.updateSettings(org.mockito.ArgumentMatchers.any())).thenReturn(new ShopSettingsDto(720, null, null));

        mockMvc.perform(get("/api/admin/shop/reservation-slots").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(slotId.toString()));

        mockMvc.perform(post("/api/admin/shop/reservation-slots/events")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\",\"note\":\"Début de soirée\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/shop/reservation-slots/custom")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"2027-02-02T17:00:00Z\",\"endsAt\":\"2027-02-02T19:00:00Z\",\"location\":\"Bar INSA\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/shop/reservation-series")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsOn\":\"2027-02-02\",\"endsOn\":\"2027-02-16\",\"dayOfWeek\":\"TUESDAY\",\"startsAt\":\"18:00:00\",\"endsAt\":\"20:00:00\",\"location\":\"Bar INSA\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/admin/shop/reservation-slots/{id}", slotId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Nouvelle note\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/shop/reservation-slots/{id}", slotId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/admin/shop/reservation-slots/{id}/activate", slotId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/shop/reservation-series/{id}", seriesId)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/shop/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockDelayMinutes").value(1440));

        mockMvc.perform(put("/api/admin/shop/settings")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockDelayMinutes\":720}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockDelayMinutes").value(720));

        verify(reservationService).deactivate(slotId);
        verify(reservationService).activate(slotId);
        verify(reservationService).deleteSeries(seriesId);
    }

    @Test
    void adminCannotCreateInvalidReservationOrSettings() throws Exception {
        mockMvc.perform(post("/api/admin/shop/reservation-slots/custom")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/shop/settings")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockDelayMinutes\":-1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reservationService);
    }
}
