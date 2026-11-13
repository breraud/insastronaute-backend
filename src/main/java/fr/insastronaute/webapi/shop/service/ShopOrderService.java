package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.shop.domain.*;
import fr.insastronaute.webapi.shop.dto.*;
import fr.insastronaute.webapi.shop.repository.*;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShopOrderService {
    private static final int DEFAULT_LOCK_DELAY_MINUTES = 1_440;
    private final UserRepository userRepository;
    private final ShopCartRepository cartRepository;
    private final ShopOrderRepository orderRepository;
    private final ShopReservationSlotRepository slotRepository;
    private final ShopSettingsRepository settingsRepository;
    private final ShopReservationService reservationService;
    private final ShopStockService stockService;
    private final AdminActionJournal journal;
    private final Clock clock;

    public ShopOrderService(UserRepository userRepository, ShopCartRepository cartRepository,
                            ShopOrderRepository orderRepository, ShopReservationSlotRepository slotRepository,
                            ShopSettingsRepository settingsRepository, ShopReservationService reservationService,
                            ShopStockService stockService, AdminActionJournal journal, Clock clock) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.slotRepository = slotRepository;
        this.settingsRepository = settingsRepository;
        this.reservationService = reservationService;
        this.stockService = stockService;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional
    public ShopOrderDto submit(String keycloakSub, UUID slotId) {
        AppUser user = user(keycloakSub);
        ShopCart cart = cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new BadRequestException("Le panier est vide."));
        if (cart.getLines().isEmpty()) throw new BadRequestException("Le panier est vide.");
        if (cart.getLines().stream().anyMatch(line -> line.getProduct() == null || line.getProduct().isOutOfStock())) {
            throw new BadRequestException("Le panier contient un produit indisponible.");
        }
        // Le controle qui fait foi. Celui du panier previent tot, mais entre le moment ou
        // l'on compose sa commande et celui ou on la valide, quelqu'un d'autre a pu
        // prendre le dernier exemplaire.
        ensureOrderIsAllowed(user, cart);
        ShopReservationSlot slot = slotForUpdate(slotId, true);
        ShopOrder order = new ShopOrder();
        order.setUser(user);
        order.setReservationSlot(slot);
        for (ShopCartLine cartLine : cart.getLines()) {
            ShopOrderLine line = new ShopOrderLine();
            line.setOrder(order);
            line.setProduct(cartLine.getProduct());
            line.setQuantity(cartLine.getQuantity());
            order.getLines().add(line);
        }
        cart.getLines().clear();
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public void cancel(UUID orderId, String keycloakSub) {
        ShopOrder order = findForUpdate(orderId);
        if (!order.getUser().getId().equals(user(keycloakSub).getId())) {
            throw new ForbiddenException("Cette précommande ne vous appartient pas.");
        }
        ensurePlayerCanEdit(order);
        order.setStatus(ShopOrderStatus.CANCELLED);
    }

    @Transactional
    public ShopOrderDto changeReservationSlot(UUID orderId, UUID slotId, String keycloakSub) {
        ShopOrder order = findForUpdate(orderId);
        if (!order.getUser().getId().equals(user(keycloakSub).getId())) {
            throw new ForbiddenException("Cette précommande ne vous appartient pas.");
        }
        ensurePlayerCanEdit(order);
        order.setReservationSlot(slotForUpdate(slotId, true));
        return toDto(order);
    }

    @Transactional
    public ShopOrderDto changeReservationSlotAdmin(UUID orderId, UUID slotId) {
        ShopOrder order = findForUpdate(orderId);
        order.setReservationSlot(slotForUpdate(slotId, false));
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<ShopOrderDto> listMine(String keycloakSub) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user(keycloakSub).getId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ShopOrderDto> listAdmin(ShopOrderStatus status) {
        List<ShopOrder> orders = status == null ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.findByStatusOrderByCreatedAtDesc(status);
        return orders.stream().map(this::toDto).toList();
    }

    @Transactional
    public ShopOrderDto changeStatus(UUID orderId, ShopOrderStatus status) {
        ShopOrder order = findForUpdate(orderId);
        boolean allowed = (order.getStatus() == ShopOrderStatus.PENDING
                && (status == ShopOrderStatus.VALIDATED || status == ShopOrderStatus.CANCELLED))
                || (order.getStatus() == ShopOrderStatus.VALIDATED
                && (status == ShopOrderStatus.COMPLETED || status == ShopOrderStatus.CANCELLED));
        if (!allowed) throw new BadRequestException("Transition de statut invalide.");
        order.setStatus(status);
        return toDto(order);
    }

    /**
     * Ce que la boutique autorise, verifie au dernier moment.
     *
     * <p>Trois regles, dans l'ordre ou elles se comprennent : combien de precommandes un
     * joueur peut tenir, combien d'articles une commande peut porter, et ce qu'il reste
     * de chaque produit. Chacune est muette tant que l'admin ne l'a pas posee : sans
     * reglage, la boutique se comporte exactement comme avant.</p>
     */
    private void ensureOrderIsAllowed(AppUser user, ShopCart cart) {
        ShopSettings settings = stockService.settings();

        Integer maxOrders = settings.getMaxOrdersPerUser();
        if (maxOrders != null && orderRepository.countActiveByUserId(user.getId()) >= maxOrders) {
            throw new CodedBadRequestException("SHOP_ORDER_LIMIT_REACHED", maxOrders == 1
                    ? "Tu as déjà une précommande en cours."
                    : "Tu as déjà " + maxOrders + " précommandes en cours.");
        }

        Integer maxItems = settings.getMaxItemsPerOrder();
        int items = cart.getLines().stream().mapToInt(ShopCartLine::getQuantity).sum();
        if (maxItems != null && items > maxItems) {
            throw new CodedBadRequestException("SHOP_ITEM_LIMIT_REACHED",
                    maxItems + (maxItems > 1 ? " articles maximum par commande." : " article maximum par commande."));
        }

        Map<UUID, Long> ordered = stockService.orderedByProduct();
        for (ShopCartLine line : cart.getLines()) {
            ShopProduct product = line.getProduct();
            Integer maxPerOrder = product.getMaxPerOrder();
            if (maxPerOrder != null && line.getQuantity() > maxPerOrder) {
                throw new CodedBadRequestException("SHOP_PRODUCT_LIMIT_REACHED", "« " + product.getName()
                        + " » est limité à " + maxPerOrder
                        + (maxPerOrder > 1 ? " exemplaires par commande." : " exemplaire par commande."));
            }
            Integer remaining = stockService.remaining(product, ordered);
            if (remaining != null && line.getQuantity() > remaining) {
                throw new CodedBadRequestException("SHOP_OUT_OF_STOCK", remaining == 0
                        ? "« " + product.getName() + " » n'est plus disponible."
                        : "Il ne reste que " + remaining + " « " + product.getName() + " ».");
            }
        }
    }

    /**
     * Efface definitivement une precommande annulee.
     *
     * <p>Seulement une annulee : une commande vivante s'annule d'abord. Le detour n'est pas
     * une formalite — l'annulation previent le joueur, rend le creneau et libere le stock,
     * et supprimer directement sauterait tout cela sans que rien ne le dise.</p>
     *
     * <p>La ligne du registre porte ce que la commande contenait. Une commande effacee ne
     * laisse sinon aucune trace de son existence, et « qu'est-il arrive a ma commande ? »
     * doit rester une question a laquelle on sait repondre.</p>
     */
    @Transactional
    public void delete(UUID orderId, String actorKeycloakSub) {
        ShopOrder order = findForUpdate(orderId);
        if (order.getStatus() != ShopOrderStatus.CANCELLED) {
            throw new CodedBadRequestException("SHOP_ORDER_NOT_CANCELLED",
                    "Seule une précommande annulée peut être supprimée. Annule-la d'abord.");
        }
        AppUser buyer = order.getUser();
        String content = order.getLines().stream()
                .map(line -> (line.getProduct() == null ? "Produit supprimé" : line.getProduct().getName())
                        + " ×" + line.getQuantity())
                .collect(java.util.stream.Collectors.joining(", "));
        // La trace s'ecrit avant l'effacement : apres, il n'y aurait plus rien a lire pour
        // la composer.
        journal.record(actorKeycloakSub, buyer.getId(), AdminActionType.SHOP_ORDER_DELETED, Map.of(
                "orderId", order.getId().toString(),
                "content", content.isEmpty() ? "Commande vide" : content,
                "createdAt", order.getCreatedAt().toString()
        ));
        orderRepository.delete(order);
    }

    private void ensurePlayerCanEdit(ShopOrder order) {
        if (order.getStatus() == ShopOrderStatus.CANCELLED || isLocked(order)) {
            throw new CodedBadRequestException("SHOP_ORDER_LOCKED", "Cette précommande est verrouillée.");
        }
    }

    private boolean isLocked(ShopOrder order) {
        if (order.getStatus() == ShopOrderStatus.VALIDATED || order.getStatus() == ShopOrderStatus.COMPLETED) return true;
        Instant lockAt = lockAt(order);
        return lockAt != null && !clock.instant().isBefore(lockAt);
    }

    private boolean isLocked(ShopOrder order, Instant lockAt) {
        return order.getStatus() == ShopOrderStatus.VALIDATED
                || order.getStatus() == ShopOrderStatus.COMPLETED
                || (lockAt != null && !clock.instant().isBefore(lockAt));
    }

    private Instant lockAt(ShopOrder order) {
        if (order.getReservationSlot() == null) return null;
        int delay = settingsRepository.findById(ShopSettings.SINGLETON_ID)
                .map(ShopSettings::getLockDelayMinutes).orElse(DEFAULT_LOCK_DELAY_MINUTES);
        return effectiveStart(order.getReservationSlot()).minus(delay, ChronoUnit.MINUTES);
    }

    private ShopReservationSlot slotForUpdate(UUID id, boolean requireAvailable) {
        if (id == null) {
            throw new CodedBadRequestException("SHOP_SLOT_REQUIRED", "Le créneau de retrait est obligatoire.");
        }
        ShopReservationSlot slot = slotRepository.findWithEventAndSeriesForUpdateById(id)
                .orElseThrow(() -> new NotFoundException("Créneau introuvable."));
        if (requireAvailable && (!slot.isActive() || !effectiveStart(slot).isAfter(clock.instant()))) {
            throw new CodedBadRequestException("SHOP_SLOT_UNAVAILABLE", "Le créneau de retrait n'est plus disponible.");
        }
        return slot;
    }

    private Instant effectiveStart(ShopReservationSlot slot) {
        if (slot.getType() == ShopReservationSlotType.CUSTOM) return slot.getCustomStartsAt();
        return slot.getEvent() == null ? slot.getEventStartsAtSnapshot() : slot.getEvent().getStartsAt();
    }

    private AppUser user(String sub) {
        return userRepository.findByKeycloakSub(sub).orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }

    private ShopOrder findForUpdate(UUID id) {
        return orderRepository.findWithGraphForUpdateById(id)
                .orElseThrow(() -> new NotFoundException("Précommande introuvable."));
    }

    private ShopOrderDto toDto(ShopOrder order) {
        List<ShopOrderLineDto> lines = order.getLines().stream().map(line -> {
            ShopProduct product = line.getProduct();
            if (product == null) return new ShopOrderLineDto(null, null, null, false, true, List.of(), line.getQuantity(), null);
            List<ShopProductPhotoDto> photos = product.getPhotos().stream()
                    .sorted(Comparator.comparingInt(ShopProductPhoto::getDisplayOrder))
                    .map(photo -> new ShopProductPhotoDto(photo.getId(), photo.getUrl(), photo.getDisplayOrder())).toList();
            long total = Math.multiplyExact(product.getPriceCents(), line.getQuantity());
            return new ShopOrderLineDto(product.getId(), product.getName(), product.getPriceCents(), true,
                    product.isOutOfStock(), photos, line.getQuantity(), total);
        }).toList();
        boolean unavailable = lines.stream().anyMatch(line -> !line.productAvailable());
        Long total = unavailable ? null : lines.stream().mapToLong(ShopOrderLineDto::lineTotalCents).sum();
        AppUser owner = order.getUser();
        ShopReservationSlotDto slot = order.getReservationSlot() == null ? null : reservationService.toDto(order.getReservationSlot());
        Instant lockAt = lockAt(order);
        return new ShopOrderDto(order.getId(), new ShopOrderUserDto(owner.getId(), owner.getDisplayName(), owner.getEmail()),
                order.getStatus(), lines, total, unavailable, slot, lockAt, isLocked(order, lockAt),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
