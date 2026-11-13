package fr.insastronaute.webapi.shop.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "shop_settings")
public class ShopSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "lock_delay_minutes", nullable = false)
    private Integer lockDelayMinutes = 1_440;

    /**
     * Le nombre de precommandes en cours qu'un joueur peut avoir, ou null sans limite.
     *
     * <p>« En cours » exclut les annulees : une commande annulee ne prend la place de
     * personne, et compter les siennes empecherait un joueur de se raviser.</p>
     */
    @Column(name = "max_orders_per_user")
    private Integer maxOrdersPerUser;

    /** Le nombre total d'articles qu'une commande peut porter, ou null sans limite. */
    @Column(name = "max_items_per_order")
    private Integer maxItemsPerOrder;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public Integer getLockDelayMinutes() { return lockDelayMinutes; }
    public void setLockDelayMinutes(Integer lockDelayMinutes) { this.lockDelayMinutes = lockDelayMinutes; }
    public Integer getMaxOrdersPerUser() { return maxOrdersPerUser; }
    public void setMaxOrdersPerUser(Integer maxOrdersPerUser) { this.maxOrdersPerUser = maxOrdersPerUser; }
    public Integer getMaxItemsPerOrder() { return maxItemsPerOrder; }
    public void setMaxItemsPerOrder(Integer maxItemsPerOrder) { this.maxItemsPerOrder = maxItemsPerOrder; }
    public Instant getUpdatedAt() { return updatedAt; }
}
