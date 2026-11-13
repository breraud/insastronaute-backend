package fr.insastronaute.webapi.shop.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shop_order_lines")
public class ShopOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") private ShopProduct product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private ShopOrder order;
    @Column(nullable = false) private int quantity;
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public ShopProduct getProduct() { return product; } public void setProduct(ShopProduct product) { this.product = product; }
    public ShopOrder getOrder() { return order; } public void setOrder(ShopOrder order) { this.order = order; }
    public int getQuantity() { return quantity; } public void setQuantity(int quantity) { this.quantity = quantity; }
}
