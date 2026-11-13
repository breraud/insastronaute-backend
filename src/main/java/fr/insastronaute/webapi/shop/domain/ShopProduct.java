package fr.insastronaute.webapi.shop.domain;

import fr.insastronaute.webapi.article.domain.Article;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shop_products")
public class ShopProduct {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 180) private String name;
    @Column(name = "price_cents", nullable = false) private long priceCents;
    @Column(name = "out_of_stock", nullable = false) private boolean outOfStock;
    /**
     * Le nombre d'exemplaires a vendre, ou null quand rien ne le borne.
     *
     * <p>Ce qui reste ne se lit pas ici : il se compte a partir des precommandes non
     * annulees. Un compteur decremente aurait a etre remis d'aplomb a chaque annulation,
     * chaque suppression de commande, chaque changement de quantite — et il derive au
     * premier oubli.</p>
     */
    @Column(name = "stock") private Integer stock;
    /** Le plus grand nombre d'exemplaires qu'une seule commande peut porter, ou null. */
    @Column(name = "max_per_order") private Integer maxPerOrder;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "article_id") private Article article;
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true) private List<ShopProductPhoto> photos = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow(); }
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public long getPriceCents() { return priceCents; } public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public boolean isOutOfStock() { return outOfStock; } public void setOutOfStock(boolean outOfStock) { this.outOfStock = outOfStock; }
    public Integer getStock() { return stock; } public void setStock(Integer stock) { this.stock = stock; }
    public Integer getMaxPerOrder() { return maxPerOrder; } public void setMaxPerOrder(Integer maxPerOrder) { this.maxPerOrder = maxPerOrder; }
    public Article getArticle() { return article; } public void setArticle(Article article) { this.article = article; }
    public List<ShopProductPhoto> getPhotos() { return photos; } public void setPhotos(List<ShopProductPhoto> photos) { this.photos = photos; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
