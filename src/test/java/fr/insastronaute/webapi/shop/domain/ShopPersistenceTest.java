package fr.insastronaute.webapi.shop.domain;

import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.shop.repository.ShopCartRepository;
import fr.insastronaute.webapi.shop.repository.ShopOrderRepository;
import fr.insastronaute.webapi.shop.repository.ShopReservationSeriesRepository;
import fr.insastronaute.webapi.shop.repository.ShopReservationSlotRepository;
import fr.insastronaute.webapi.shop.repository.ShopSettingsRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ShopPersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopCartRepository cartRepository;

    @Autowired
    private ShopOrderRepository orderRepository;

    @Autowired
    private ShopReservationSlotRepository slotRepository;

    @Autowired
    private ShopReservationSeriesRepository seriesRepository;

    @Autowired
    private ShopSettingsRepository settingsRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAnEventBackedReservationSlotWithoutCopyingEventDetails() {
        Event event = event("Soiree goodies", Instant.parse("2026-09-01T18:00:00Z"),
                Instant.parse("2026-09-01T22:00:00Z"), "Bar INSA");
        entityManager.persist(event);

        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.EVENT);
        slot.setEvent(event);
        slot.setEventStartsAtSnapshot(event.getStartsAt());
        slot.setEventEndsAtSnapshot(event.getEndsAt());
        slot.setEventLocationSnapshot(event.getLocation());
        slot.setNote("Retrait seulement en debut de soiree");
        slotRepository.saveAndFlush(slot);
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(loaded.getType()).isEqualTo(ShopReservationSlotType.EVENT);
        assertThat(loaded.getEvent().getId()).isEqualTo(event.getId());
        assertThat(loaded.getCustomStartsAt()).isNull();
        assertThat(loaded.getCustomEndsAt()).isNull();
        assertThat(loaded.getCustomLocation()).isNull();
        assertThat(loaded.getEventStartsAtSnapshot()).isEqualTo(event.getStartsAt());
        assertThat(loaded.getEventEndsAtSnapshot()).isEqualTo(event.getEndsAt());
        assertThat(loaded.getEventLocationSnapshot()).isEqualTo(event.getLocation());
        assertThat(loaded.getNote()).isEqualTo("Retrait seulement en debut de soiree");
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void persistsAnEventSlotWithoutOptionalEndOrLocationSnapshots() {
        Event event = event("Evenement ouvert", Instant.parse("2026-09-03T18:00:00Z"), null, null);
        entityManager.persist(event);

        ShopReservationSlot slot = eventSlot(event);
        slotRepository.saveAndFlush(slot);
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(loaded.getEventStartsAtSnapshot()).isEqualTo(Instant.parse("2026-09-03T18:00:00Z"));
        assertThat(loaded.getEventEndsAtSnapshot()).isNull();
        assertThat(loaded.getEventLocationSnapshot()).isNull();
    }

    @Test
    void eventSnapshotSynchronizationCanClearOptionalEndAndLocation() {
        Event event = event("Evenement modifie", Instant.parse("2026-09-04T18:00:00Z"),
                Instant.parse("2026-09-04T22:00:00Z"), "Bar initial");
        entityManager.persist(event);
        ShopReservationSlot slot = eventSlot(event);
        slotRepository.saveAndFlush(slot);

        assertThat(slotRepository.updateEventSnapshots(
                event.getId(), Instant.parse("2026-09-04T19:00:00Z"), null, null)).isEqualTo(1);
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(loaded.getEventStartsAtSnapshot()).isEqualTo(Instant.parse("2026-09-04T19:00:00Z"));
        assertThat(loaded.getEventEndsAtSnapshot()).isNull();
        assertThat(loaded.getEventLocationSnapshot()).isNull();
    }

    @Test
    void rejectsAnActiveEventSlotWithoutItsSourceEvent() {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.EVENT);
        slot.setEventStartsAtSnapshot(Instant.parse("2026-09-01T18:00:00Z"));
        slot.setEventEndsAtSnapshot(Instant.parse("2026-09-01T22:00:00Z"));
        slot.setEventLocationSnapshot("Bar INSA");

        assertThatThrownBy(() -> slotRepository.saveAndFlush(slot))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesEventSnapshotsWhenADeactivatedSlotsSourceIsDeleted() {
        Event event = event("Soiree supprimee", Instant.parse("2026-10-01T18:00:00Z"),
                Instant.parse("2026-10-01T22:00:00Z"), "Bar historique");
        entityManager.persist(event);
        ShopReservationSlot slot = eventSlot(event);
        slotRepository.saveAndFlush(slot);

        assertThat(slotRepository.deactivateByEventId(event.getId())).isEqualTo(1);
        slotRepository.flush();

        entityManager.clear();
        entityManager.remove(entityManager.find(Event.class, event.getId()));
        entityManager.flush();
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(loaded.getEvent()).isNull();
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.getEventStartsAtSnapshot()).isEqualTo(Instant.parse("2026-10-01T18:00:00Z"));
        assertThat(loaded.getEventEndsAtSnapshot()).isEqualTo(Instant.parse("2026-10-01T22:00:00Z"));
        assertThat(loaded.getEventLocationSnapshot()).isEqualTo("Bar historique");
    }

    @Test
    void preservesTheLatestEventSnapshotsAfterUpdateThenDelete() {
        Event event = event("Soiree initiale", Instant.parse("2026-10-15T18:00:00Z"),
                Instant.parse("2026-10-15T22:00:00Z"), "Ancien bar");
        entityManager.persist(event);
        ShopReservationSlot slot = eventSlot(event);
        slotRepository.saveAndFlush(slot);

        Instant newStartsAt = Instant.parse("2026-10-15T19:00:00Z");
        Instant newEndsAt = Instant.parse("2026-10-15T23:00:00Z");
        assertThat(slotRepository.updateEventSnapshots(event.getId(), newStartsAt, newEndsAt, "Nouveau bar"))
                .isEqualTo(1);
        assertThat(slotRepository.deactivateByEventId(event.getId())).isEqualTo(1);
        slotRepository.flush();
        entityManager.clear();
        entityManager.remove(entityManager.find(Event.class, event.getId()));
        entityManager.flush();
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(loaded.getEvent()).isNull();
        assertThat(loaded.getEventStartsAtSnapshot()).isEqualTo(newStartsAt);
        assertThat(loaded.getEventEndsAtSnapshot()).isEqualTo(newEndsAt);
        assertThat(loaded.getEventLocationSnapshot()).isEqualTo("Nouveau bar");
    }

    @Test
    void rejectsDeletingTheSourceOfAnActiveEventSlot() {
        Event event = event("Soiree active", Instant.parse("2026-10-08T18:00:00Z"),
                Instant.parse("2026-10-08T22:00:00Z"), "Bar actif");
        entityManager.persist(event);
        slotRepository.saveAndFlush(eventSlot(event));

        entityManager.clear();
        entityManager.remove(entityManager.find(Event.class, event.getId()));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }

    private ShopReservationSlot eventSlot(Event event) {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.EVENT);
        slot.setEvent(event);
        slot.setEventStartsAtSnapshot(event.getStartsAt());
        slot.setEventEndsAtSnapshot(event.getEndsAt());
        slot.setEventLocationSnapshot(event.getLocation());
        return slot;
    }

    @Test
    void persistsAnOccasionalCustomReservationSlot() {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.CUSTOM);
        slot.setCustomStartsAt(Instant.parse("2026-09-08T16:30:00Z"));
        slot.setCustomEndsAt(Instant.parse("2026-09-08T18:00:00Z"));
        slot.setCustomLocation("Local goodies");
        slot.setNote("Sonnez a l'accueil");
        slotRepository.saveAndFlush(slot);
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(slot.getId()).orElseThrow();
        assertThat(loaded.getEvent()).isNull();
        assertThat(loaded.getSeries()).isNull();
        assertThat(loaded.getCustomStartsAt()).isEqualTo(Instant.parse("2026-09-08T16:30:00Z"));
        assertThat(loaded.getCustomEndsAt()).isEqualTo(Instant.parse("2026-09-08T18:00:00Z"));
        assertThat(loaded.getCustomLocation()).isEqualTo("Local goodies");
    }

    @Test
    void persistsAMaterializedWeeklySeriesOccurrence() {
        ShopReservationSeries series = new ShopReservationSeries();
        series.setStartsOn(LocalDate.of(2026, 9, 1));
        series.setEndsOn(LocalDate.of(2026, 9, 29));
        series.setDayOfWeek(2);
        series.setStartsAt(LocalTime.of(18, 0));
        series.setEndsAt(LocalTime.of(20, 0));
        series.setLocation("Local BDE");
        series.setNote("Chaque mardi");
        seriesRepository.saveAndFlush(series);

        ShopReservationSlot occurrence = new ShopReservationSlot();
        occurrence.setType(ShopReservationSlotType.CUSTOM);
        occurrence.setSeries(series);
        occurrence.setCustomStartsAt(Instant.parse("2026-09-08T16:00:00Z"));
        occurrence.setCustomEndsAt(Instant.parse("2026-09-08T18:00:00Z"));
        occurrence.setCustomLocation("Local BDE");
        occurrence.setNote("Chaque mardi");
        slotRepository.saveAndFlush(occurrence);
        entityManager.clear();

        ShopReservationSlot loaded = slotRepository.findById(occurrence.getId()).orElseThrow();
        assertThat(loaded.getSeries().getId()).isEqualTo(series.getId());
        assertThat(loaded.getSeries().getDayOfWeek()).isEqualTo(2);
        assertThat(loaded.getSeries().getStartsOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(loaded.getSeries().getEndsOn()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void persistsTheGlobalLockDelayAndAnOrdersReservationSlot() {
        ShopSettings settings = new ShopSettings();
        settings.setId(ShopSettings.SINGLETON_ID);
        settings.setLockDelayMinutes(1_440);
        settingsRepository.saveAndFlush(settings);

        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.CUSTOM);
        slot.setCustomStartsAt(Instant.parse("2026-09-15T16:00:00Z"));
        slot.setCustomEndsAt(Instant.parse("2026-09-15T18:00:00Z"));
        slot.setCustomLocation("Local goodies");
        slotRepository.saveAndFlush(slot);

        AppUser user = user("shop-slot-order-user");
        userRepository.saveAndFlush(user);
        ShopOrder order = new ShopOrder();
        order.setUser(user);
        order.setReservationSlot(slot);
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        assertThat(settingsRepository.findById(ShopSettings.SINGLETON_ID).orElseThrow().getLockDelayMinutes())
                .isEqualTo(1_440);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getReservationSlot().getId())
                .isEqualTo(slot.getId());
    }

    @Test
    void pessimisticWriteQueriesLoadOrderAndSlotGraphs() {
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.CUSTOM);
        slot.setCustomStartsAt(Instant.parse("2026-11-15T16:00:00Z"));
        slot.setCustomEndsAt(Instant.parse("2026-11-15T18:00:00Z"));
        slot.setCustomLocation("Local goodies");
        slotRepository.saveAndFlush(slot);

        AppUser user = user("shop-lock-query-user");
        userRepository.saveAndFlush(user);
        ShopOrder order = new ShopOrder();
        order.setUser(user);
        order.setReservationSlot(slot);
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        ShopOrder lockedOrder = orderRepository.findWithGraphForUpdateById(order.getId()).orElseThrow();
        ShopReservationSlot lockedSlot = slotRepository
                .findWithEventAndSeriesForUpdateById(slot.getId()).orElseThrow();

        assertThat(lockedOrder.getUser().getId()).isEqualTo(user.getId());
        assertThat(lockedOrder.getReservationSlot().getId()).isEqualTo(slot.getId());
        assertThat(lockedSlot.getCustomLocation()).isEqualTo("Local goodies");
    }

    private Event event(String title, Instant startsAt, Instant endsAt, String location) {
        Event event = new Event();
        event.setTitle(title);
        event.setShortDescription(title);
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setLocation(location);
        return event;
    }

    private AppUser user(String keycloakSub) {
        AppUser user = new AppUser();
        user.setKeycloakSub(keycloakSub);
        user.setEmail(keycloakSub + "@example.com");
        return user;
    }

    @Test
    void preventsMoreThanOneCartForTheSameUser() {
        AppUser user = new AppUser();
        user.setKeycloakSub("shop-user");
        user.setEmail("shop-user@example.com");
        userRepository.saveAndFlush(user);

        ShopCart firstCart = new ShopCart();
        firstCart.setUser(user);
        cartRepository.saveAndFlush(firstCart);

        ShopCart duplicateCart = new ShopCart();
        duplicateCart.setUser(user);

        assertThatThrownBy(() -> cartRepository.saveAndFlush(duplicateCart))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void loadsFilteredOrdersWithTheirProductPhotos() {
        AppUser user = new AppUser();
        user.setKeycloakSub("shop-order-user");
        user.setEmail("shop-order-user@example.com");
        userRepository.saveAndFlush(user);

        ShopProduct product = new ShopProduct();
        product.setName("Produit");
        product.setPriceCents(1200);
        entityManager.persist(product);

        ShopProductPhoto photo = new ShopProductPhoto();
        photo.setProduct(product);
        photo.setObjectKey("shop/products/photo.png");
        photo.setUrl("https://s3.example.test/public/shop/products/photo.png");
        photo.setDisplayOrder(0);
        entityManager.persist(photo);

        ShopOrder order = new ShopOrder();
        order.setUser(user);
        order.setStatus(ShopOrderStatus.PENDING);
        ShopReservationSlot slot = new ShopReservationSlot();
        slot.setType(ShopReservationSlotType.CUSTOM);
        slot.setCustomStartsAt(Instant.parse("2026-12-01T18:00:00Z"));
        slot.setCustomEndsAt(Instant.parse("2026-12-01T20:00:00Z"));
        slot.setCustomLocation("Local goodies");
        entityManager.persist(slot);
        order.setReservationSlot(slot);
        entityManager.persist(order);

        ShopOrderLine line = new ShopOrderLine();
        line.setOrder(order);
        line.setProduct(product);
        line.setQuantity(1);
        entityManager.persist(line);
        entityManager.flush();
        entityManager.clear();

        ShopOrder loaded = orderRepository.findByStatusOrderByCreatedAtDesc(ShopOrderStatus.PENDING).getFirst();

        assertThat(loaded.getLines()).hasSize(1);
        assertThat(loaded.getLines().getFirst().getProduct().getPhotos()).hasSize(1);
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(loaded, "reservationSlot")).isTrue();
        assertThat(loaded.getReservationSlot().getId()).isEqualTo(slot.getId());
    }

    @Test
    void loadsCartLinesWithoutAConcurrentMultipleBagFetch() {
        AppUser user = new AppUser();
        user.setKeycloakSub("shop-cart-user");
        user.setEmail("shop-cart-user@example.com");
        userRepository.saveAndFlush(user);

        ShopProduct product = new ShopProduct();
        product.setName("Produit");
        product.setPriceCents(1200);
        entityManager.persist(product);

        ShopProductPhoto photo = new ShopProductPhoto();
        photo.setProduct(product);
        photo.setObjectKey("shop/products/photo.png");
        photo.setUrl("https://s3.example.test/public/shop/products/photo.png");
        photo.setDisplayOrder(0);
        entityManager.persist(photo);

        ShopCart cart = new ShopCart();
        cart.setUser(user);
        entityManager.persist(cart);

        ShopCartLine line = new ShopCartLine();
        line.setCart(cart);
        line.setProduct(product);
        line.setQuantity(1);
        entityManager.persist(line);
        entityManager.flush();
        entityManager.clear();

        ShopCart loaded = cartRepository.findByUserId(user.getId()).orElseThrow();

        assertThat(loaded.getLines()).hasSize(1);
        assertThat(loaded.getLines().getFirst().getProduct().getPhotos()).hasSize(1);
    }
}
