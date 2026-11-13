package fr.insastronaute.webapi.shop.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shop_cart_lines", uniqueConstraints = @UniqueConstraint(name = "uk_shop_cart_lines_cart_product", columnNames = {"cart_id", "product_id"}))
public class ShopCartLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cart_id", nullable = false) private ShopCart cart;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private ShopProduct product;
    @Column(nullable = false) private int quantity;
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public ShopCart getCart() { return cart; } public void setCart(ShopCart cart) { this.cart = cart; }
    public ShopProduct getProduct() { return product; } public void setProduct(ShopProduct product) { this.product = product; }
    public int getQuantity() { return quantity; } public void setQuantity(int quantity) { this.quantity = quantity; }
}
