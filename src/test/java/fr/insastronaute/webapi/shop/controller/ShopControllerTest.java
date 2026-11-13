package fr.insastronaute.webapi.shop.controller;

import fr.insastronaute.webapi.shop.dto.ShopCartDto;
import fr.insastronaute.webapi.shop.dto.ShopOrderDto;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.shop.service.ShopCartService;
import fr.insastronaute.webapi.shop.service.ShopOrderService;
import fr.insastronaute.webapi.shop.service.ShopProductService;
import fr.insastronaute.webapi.shop.service.ShopReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ShopProductService productService;
    @MockitoBean ShopCartService cartService;
    @MockitoBean ShopOrderService orderService;
    @MockitoBean ShopReservationService reservationService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void authenticatedUserCanReadCart() throws Exception {
        when(cartService.get("sub")).thenReturn(new ShopCartDto(null, List.of(), 0));

        mockMvc.perform(get("/api/shop/cart").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPriceCents").value(0));
    }

    @Test
    void authenticatedUserCanSetProductQuantity() throws Exception {
        UUID productId = UUID.randomUUID();
        when(cartService.setQuantity("sub", productId, 2)).thenReturn(new ShopCartDto(null, List.of(), 0));

        mockMvc.perform(put("/api/shop/cart/items/{productId}", productId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanSubmitAndCancelOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        when(orderService.submit("sub", slotId)).thenReturn(ShopOrderDto.empty(orderId));

        mockMvc.perform(post("/api/shop/orders")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slotId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()));

        mockMvc.perform(delete("/api/shop/orders/{orderId}", orderId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        verify(orderService).cancel(orderId, "sub");
    }

    @Test
    void authenticatedUserCanListAndChangeReservationSlots() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        when(reservationService.listAvailable()).thenReturn(List.of());
        when(orderService.changeReservationSlot(orderId, slotId, "sub")).thenReturn(ShopOrderDto.empty(orderId));

        mockMvc.perform(get("/api/shop/reservation-slots")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/shop/orders/{orderId}/reservation-slot", orderId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slotId + "\"}"))
                .andExpect(status().isOk());
        verify(orderService).changeReservationSlot(orderId, slotId, "sub");
    }

    @Test
    void submitRequiresReservationSlot() throws Exception {
        mockMvc.perform(post("/api/shop/orders")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHOP_SLOT_REQUIRED"));
    }

    @Test
    void reservationOrderErrorsExposeStableCodes() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        when(orderService.changeReservationSlot(orderId, slotId, "sub"))
                .thenThrow(new CodedBadRequestException("SHOP_ORDER_LOCKED", "Cette précommande est verrouillée."));

        mockMvc.perform(put("/api/shop/orders/{orderId}/reservation-slot", orderId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slotId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHOP_ORDER_LOCKED"));

        when(orderService.submit("sub", slotId))
                .thenThrow(new CodedBadRequestException("SHOP_SLOT_UNAVAILABLE", "Le créneau de retrait n'est plus disponible."));
        mockMvc.perform(post("/api/shop/orders")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("sub")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":\"" + slotId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHOP_SLOT_UNAVAILABLE"));
    }
}
