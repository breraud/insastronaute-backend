package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.shop.domain.*;
import fr.insastronaute.webapi.shop.repository.*;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopOrderServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");
    @Mock private UserRepository userRepository;
    @Mock private ShopCartRepository cartRepository;
    @Mock private ShopOrderRepository orderRepository;
    @Mock private ShopReservationSlotRepository slotRepository;
    @Mock private ShopSettingsRepository settingsRepository;
    @Mock private ShopManualSaleRepository manualSaleRepository;
    @Mock private ShopProductBundleItemRepository bundleItemRepository;
    @Mock private fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal journal;
    private ShopOrderService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new ShopOrderService(userRepository, cartRepository, orderRepository, slotRepository,
                settingsRepository, new ShopReservationService(slotRepository, null, settingsRepository,
                orderRepository, null, Clock.fixed(NOW, ZoneOffset.UTC)),
                new ShopStockService(orderRepository, manualSaleRepository, bundleItemRepository, settingsRepository),
                journal, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setKeycloakSub("sub");
        lenient().when(userRepository.findByKeycloakSub("sub")).thenReturn(Optional.of(user));
    }

    @Test
    void submitBindsAnActiveFutureSlot() {
        ShopCart cart = cartWithAvailableProduct();
        ShopReservationSlot slot = customSlot(NOW.plusSeconds(172800), true);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(slotRepository.findWithEventAndSeriesForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        var result = service.submit("sub", slot.getId());

        assertThat(result.slot().id()).isEqualTo(slot.getId());
        assertThat(result.lockAt()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(result.locked()).isFalse();
        verify(slotRepository).findWithEventAndSeriesForUpdateById(slot.getId());
    }

    @Test
    void submitRejectsInactiveOrPastSlotWithoutEmptyingCart() {
        ShopCart cart = cartWithAvailableProduct();
        ShopReservationSlot slot = customSlot(NOW.minusSeconds(1), true);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(slotRepository.findWithEventAndSeriesForUpdateById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.submit("sub", slot.getId()))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_SLOT_UNAVAILABLE"))
                .hasMessage("Le créneau de retrait n'est plus disponible.");
        assertThat(cart.getLines()).hasSize(1);
        verify(orderRepository, never()).save(any());
    }

    // ── Stock et plafonds ─────────────────────────────────────────────────────
    // Le panier previent tot, mais c'est ici que ca se decide : entre le moment ou l'on
    // compose sa commande et celui ou on la valide, quelqu'un d'autre a pu prendre le
    // dernier exemplaire. Aucune de ces regles ne parle tant que l'admin ne l'a pas posee.

    @Test
    void submitRefusesMoreThanTheStockLeft() {
        ShopCart cart = cartWithAvailableProduct();
        ShopProduct product = cart.getLines().getFirst().getProduct();
        product.setName("Sweat INSA");
        product.setStock(10);
        cart.getLines().getFirst().setQuantity(3);
        ShopReservationSlot slot = customSlot(NOW.plusSeconds(172800), true);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));
        // Huit sont deja pris : il en reste deux, pas trois.
        when(orderRepository.sumOrderedQuantityByProduct())
                .thenReturn(java.util.List.<Object[]>of(new Object[]{product.getId(), 8L}));

        assertThatThrownBy(() -> service.submit("sub", slot.getId()))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_OUT_OF_STOCK"))
                .hasMessage("Il ne reste que 2 « Sweat INSA ».");
        assertThat(cart.getLines()).hasSize(1);
        verify(orderRepository, never()).save(any());
    }

    /** Une commande annulee ne prend la place de personne : elle rend son stock. */
    @Test
    void aCancelledOrderGivesItsStockBack() {
        ShopCart cart = cartWithAvailableProduct();
        ShopProduct product = cart.getLines().getFirst().getProduct();
        product.setStock(1);
        ShopReservationSlot slot = customSlot(NOW.plusSeconds(172800), true);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(slotRepository.findWithEventAndSeriesForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // La requete de comptage exclut deja les annulees : elle ne renvoie rien.
        when(orderRepository.sumOrderedQuantityByProduct()).thenReturn(java.util.List.of());

        assertThat(service.submit("sub", slot.getId())).isNotNull();
    }

    @Test
    void submitRefusesASecondOrderWhenOneIsAllowed() {
        ShopCart cart = cartWithAvailableProduct();
        ShopSettings settings = new ShopSettings();
        settings.setMaxOrdersPerUser(1);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(orderRepository.countActiveByUserId(user.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.submit("sub", UUID.randomUUID()))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_ORDER_LIMIT_REACHED"))
                .hasMessage("Tu as déjà une précommande en cours.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void submitRefusesMoreItemsThanTheShopAllows() {
        ShopCart cart = cartWithAvailableProduct();
        cart.getLines().getFirst().setQuantity(6);
        ShopSettings settings = new ShopSettings();
        settings.setMaxItemsPerOrder(5);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.submit("sub", UUID.randomUUID()))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_ITEM_LIMIT_REACHED"))
                .hasMessage("5 articles maximum par commande.");
    }

    @Test
    void submitRefusesMoreCopiesThanTheProductAllows() {
        ShopCart cart = cartWithAvailableProduct();
        cart.getLines().getFirst().getProduct().setName("Sweat INSA");
        cart.getLines().getFirst().getProduct().setMaxPerOrder(1);
        cart.getLines().getFirst().setQuantity(2);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        assertThatThrownBy(() -> service.submit("sub", UUID.randomUUID()))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_PRODUCT_LIMIT_REACHED"))
                .hasMessage("« Sweat INSA » est limité à 1 exemplaire par commande.");
    }

    /** Sans reglage, la boutique se comporte exactement comme avant. */
    @Test
    void submitStaysUnboundedWhenNoLimitIsSet() {
        ShopCart cart = cartWithAvailableProduct();
        cart.getLines().getFirst().setQuantity(42);
        ShopReservationSlot slot = customSlot(NOW.plusSeconds(172800), true);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(slotRepository.findWithEventAndSeriesForUpdateById(slot.getId())).thenReturn(Optional.of(slot));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.submit("sub", slot.getId())).isNotNull();
        verify(orderRepository, never()).countActiveByUserId(any());
    }

    // ── Suppression definitive ────────────────────────────────────────────────
    // Une commande effacee doit disparaitre partout : listes, recapitulatif, exports.
    // C'est le but — une comptabilite propre. Le registre global garde la trace.

    /**
     * Une commande vivante ne se supprime pas : elle s'annule d'abord.
     *
     * <p>Le detour n'est pas une formalite. L'annulation previent le joueur, rend le
     * creneau et libere le stock ; supprimer directement sauterait tout cela sans que
     * rien ne le dise.</p>
     */
    @Test
    void refusesToDeleteAnOrderThatIsStillAlive() {
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(172800), true));
        when(orderRepository.findWithGraphForUpdateById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.delete(order.getId(), "respo-sub"))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_ORDER_NOT_CANCELLED"));
        verify(orderRepository, never()).delete(any());
        verifyNoInteractions(journal);
    }

    /** Ce que la commande contenait part au registre : apres, il n'y aura plus rien a lire. */
    @Test
    void deletesACancelledOrderAndKeepsWhatItHeldInTheRegistry() {
        ShopOrder order = order(ShopOrderStatus.CANCELLED, customSlot(NOW.plusSeconds(172800), true));
        ShopProduct product = new ShopProduct();
        product.setName("Sweat INSA");
        ShopOrderLine line = new ShopOrderLine();
        line.setProduct(product);
        line.setQuantity(2);
        order.getLines().add(line);
        when(orderRepository.findWithGraphForUpdateById(order.getId())).thenReturn(Optional.of(order));

        service.delete(order.getId(), "respo-sub");

        InOrder ordering = inOrder(journal, orderRepository);
        ordering.verify(journal).record(
                org.mockito.ArgumentMatchers.eq("respo-sub"),
                org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.eq(
                        fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType.SHOP_ORDER_DELETED),
                org.mockito.ArgumentMatchers.<java.util.Map<String, ?>>argThat(
                        details -> "Sweat INSA ×2".equals(details.get("content"))));
        ordering.verify(orderRepository).delete(order);
    }

    @Test
    void ownerCanChangeSlotBeforeLockDeadline() {
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(172800), true));
        ShopReservationSlot replacement = customSlot(NOW.plusSeconds(259200), true);
        stubLockedOrder(order);
        when(slotRepository.findWithEventAndSeriesForUpdateById(replacement.getId())).thenReturn(Optional.of(replacement));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        var result = service.changeReservationSlot(order.getId(), replacement.getId(), "sub");

        assertThat(result.slot().id()).isEqualTo(replacement.getId());
        assertThat(order.getReservationSlot()).isSameAs(replacement);
    }

    @Test
    void validatedOrderIsImmediatelyLockedForPlayer() {
        ShopOrder order = order(ShopOrderStatus.VALIDATED, customSlot(NOW.plusSeconds(172800), true));
        stubLockedOrder(order);
        assertThatThrownBy(() -> service.changeReservationSlot(order.getId(), UUID.randomUUID(), "sub"))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_ORDER_LOCKED"))
                .hasMessage("Cette précommande est verrouillée.");
        verify(slotRepository, never()).findWithEventAndSeriesForUpdateById(any());
    }

    @Test
    void temporalDeadlineLocksCancellation() {
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(3600), true));
        stubLockedOrder(order);
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        assertThatThrownBy(() -> service.cancel(order.getId(), "sub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cette précommande est verrouillée.");
        assertThat(order.getStatus()).isEqualTo(ShopOrderStatus.PENDING);
    }

    @Test
    void nonOwnerCannotChangeReservationSlot() {
        AppUser other = new AppUser();
        ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
        other.setKeycloakSub("other");
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(172800), true));
        stubLockedOrder(order);
        when(userRepository.findByKeycloakSub("other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.changeReservationSlot(order.getId(), UUID.randomUUID(), "other"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Cette précommande ne vous appartient pas.");
        verify(slotRepository, never()).findWithEventAndSeriesForUpdateById(any());
    }

    @Test
    void ownerCanCancelPendingOrderBeforeLockDeadline() {
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(172800), true));
        stubLockedOrder(order);
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        service.cancel(order.getId(), "sub");

        assertThat(order.getStatus()).isEqualTo(ShopOrderStatus.CANCELLED);
    }

    @Test
    void completedOrderIsImmediatelyLockedForPlayer() {
        ShopOrder order = order(ShopOrderStatus.COMPLETED, customSlot(NOW.plusSeconds(172800), true));
        stubLockedOrder(order);

        assertThatThrownBy(() -> service.cancel(order.getId(), "sub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cette précommande est verrouillée.");
        assertThat(order.getStatus()).isEqualTo(ShopOrderStatus.COMPLETED);
    }

    @Test
    void exactLockDeadlineIsLocked() {
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(86400), true));
        stubLockedOrder(order);
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        assertThatThrownBy(() -> service.cancel(order.getId(), "sub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cette précommande est verrouillée.");
    }

    @Test
    void playerCannotChangeToInactiveSlot() {
        ShopOrder order = order(ShopOrderStatus.PENDING, customSlot(NOW.plusSeconds(172800), true));
        ShopReservationSlot inactive = customSlot(NOW.plusSeconds(259200), false);
        stubLockedOrder(order);
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));
        when(slotRepository.findWithEventAndSeriesForUpdateById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.changeReservationSlot(order.getId(), inactive.getId(), "sub"))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_SLOT_UNAVAILABLE"))
                .hasMessage("Le créneau de retrait n'est plus disponible.");
        assertThat(order.getReservationSlot()).isNotSameAs(inactive);
    }

    @Test
    void submitNullSlotUsesStableRequiredCode() {
        ShopCart cart = cartWithAvailableProduct();
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.submit("sub", null))
                .isInstanceOfSatisfying(CodedBadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SHOP_SLOT_REQUIRED"))
                .hasMessage("Le créneau de retrait est obligatoire.");
    }

    @Test
    void adminCanOverrideLockedOrderSlot() {
        ShopOrder order = order(ShopOrderStatus.VALIDATED, customSlot(NOW.plusSeconds(3600), true));
        ShopReservationSlot replacement = customSlot(NOW.plusSeconds(7200), true);
        when(orderRepository.findWithGraphForUpdateById(order.getId())).thenReturn(Optional.of(order));
        when(slotRepository.findWithEventAndSeriesForUpdateById(replacement.getId())).thenReturn(Optional.of(replacement));
        when(settingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(new ShopSettings()));

        var result = service.changeReservationSlotAdmin(order.getId(), replacement.getId());

        assertThat(result.slot().id()).isEqualTo(replacement.getId());
    }

    @Test
    void submitRejectsCartContainingOutOfStockProduct() {
        ShopProduct product = new ShopProduct(); product.setOutOfStock(true);
        ShopCartLine line = new ShopCartLine(); line.setProduct(product); line.setQuantity(1);
        ShopCart cart = new ShopCart(); cart.getLines().add(line);
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> service.submit("sub", UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le panier contient un produit indisponible.");
        verify(slotRepository, never()).findWithEventAndSeriesForUpdateById(any());
    }

    private void stubLockedOrder(ShopOrder order) {
        when(orderRepository.findWithGraphForUpdateById(order.getId())).thenReturn(Optional.of(order));
    }

    private ShopCart cartWithAvailableProduct() {
        ShopProduct product = new ShopProduct(); product.setPriceCents(500L);
        ShopCartLine line = new ShopCartLine(); line.setProduct(product); line.setQuantity(1);
        ShopCart cart = new ShopCart(); cart.getLines().add(line);
        return cart;
    }

    private ShopReservationSlot customSlot(Instant startsAt, boolean active) {
        ShopReservationSlot slot = new ShopReservationSlot();
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setType(ShopReservationSlotType.CUSTOM);
        slot.setCustomStartsAt(startsAt);
        slot.setCustomEndsAt(startsAt.plusSeconds(3600));
        slot.setCustomLocation("INSA");
        slot.setActive(active);
        return slot;
    }

    private ShopOrder order(ShopOrderStatus status, ShopReservationSlot slot) {
        ShopOrder order = new ShopOrder();
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        // Une commande persistee porte toujours sa date : @PrePersist la pose.
        ReflectionTestUtils.setField(order, "createdAt", NOW);
        order.setUser(user); order.setStatus(status); order.setReservationSlot(slot);
        return order;
    }
}
