package fr.insastronaute.webapi.shop.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "shop_product_photos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shop_product_photos_order",
                columnNames = {"product_id", "display_order"}
        )
)
public class ShopProductPhoto {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private ShopProduct product;
    @Column(nullable = false, length = 1024) private String url;
    @Column(name = "object_key", nullable = false, length = 512) private String objectKey;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public ShopProduct getProduct() { return product; } public void setProduct(ShopProduct product) { this.product = product; }
    public String getUrl() { return url; } public void setUrl(String url) { this.url = url; }
    public String getObjectKey() { return objectKey; } public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
