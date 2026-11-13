package fr.insastronaute.webapi.shop.domain;

import fr.insastronaute.webapi.event.domain.Event;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_reservation_slots", check = @CheckConstraint(
        name = "ck_shop_reservation_slot_event_source",
        constraint = "type <> 'EVENT' OR ((event_id IS NOT NULL OR active = false) AND event_starts_at_snapshot IS NOT NULL)"
))
public class ShopReservationSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopReservationSlotType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Event event;

    @Column(name = "event_starts_at_snapshot")
    private Instant eventStartsAtSnapshot;

    @Column(name = "event_ends_at_snapshot")
    private Instant eventEndsAtSnapshot;

    @Column(name = "event_location_snapshot", length = 255)
    private String eventLocationSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private ShopReservationSeries series;

    @Column(name = "custom_starts_at")
    private Instant customStartsAt;

    @Column(name = "custom_ends_at")
    private Instant customEndsAt;

    @Column(name = "custom_location", length = 255)
    private String customLocation;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ShopReservationSlotType getType() { return type; }
    public void setType(ShopReservationSlotType type) { this.type = type; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Instant getEventStartsAtSnapshot() { return eventStartsAtSnapshot; }
    public void setEventStartsAtSnapshot(Instant eventStartsAtSnapshot) { this.eventStartsAtSnapshot = eventStartsAtSnapshot; }
    public Instant getEventEndsAtSnapshot() { return eventEndsAtSnapshot; }
    public void setEventEndsAtSnapshot(Instant eventEndsAtSnapshot) { this.eventEndsAtSnapshot = eventEndsAtSnapshot; }
    public String getEventLocationSnapshot() { return eventLocationSnapshot; }
    public void setEventLocationSnapshot(String eventLocationSnapshot) { this.eventLocationSnapshot = eventLocationSnapshot; }
    public ShopReservationSeries getSeries() { return series; }
    public void setSeries(ShopReservationSeries series) { this.series = series; }
    public Instant getCustomStartsAt() { return customStartsAt; }
    public void setCustomStartsAt(Instant customStartsAt) { this.customStartsAt = customStartsAt; }
    public Instant getCustomEndsAt() { return customEndsAt; }
    public void setCustomEndsAt(Instant customEndsAt) { this.customEndsAt = customEndsAt; }
    public String getCustomLocation() { return customLocation; }
    public void setCustomLocation(String customLocation) { this.customLocation = customLocation; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
