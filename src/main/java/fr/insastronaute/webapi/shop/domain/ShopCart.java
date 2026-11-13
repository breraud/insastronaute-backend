package fr.insastronaute.webapi.shop.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shop_carts", uniqueConstraints = @UniqueConstraint(name = "uk_shop_carts_user", columnNames = "user_id"))
public class ShopCart {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true) private List<ShopCartLine> lines = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public AppUser getUser() { return user; } public void setUser(AppUser user) { this.user = user; }
    public List<ShopCartLine> getLines() { return lines; } public void setLines(List<ShopCartLine> lines) { this.lines = lines; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
