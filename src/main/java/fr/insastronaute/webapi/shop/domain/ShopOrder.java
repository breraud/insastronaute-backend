package fr.insastronaute.webapi.shop.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shop_orders")
public class ShopOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ShopOrderStatus status = ShopOrderStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservation_slot_id") private ShopReservationSlot reservationSlot;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) private List<ShopOrderLine> lines = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public AppUser getUser() { return user; } public void setUser(AppUser user) { this.user = user; }
    public ShopOrderStatus getStatus() { return status; } public void setStatus(ShopOrderStatus status) { this.status = status; }
    public ShopReservationSlot getReservationSlot() { return reservationSlot; } public void setReservationSlot(ShopReservationSlot reservationSlot) { this.reservationSlot = reservationSlot; }
    public List<ShopOrderLine> getLines() { return lines; } public void setLines(List<ShopOrderLine> lines) { this.lines = lines; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
