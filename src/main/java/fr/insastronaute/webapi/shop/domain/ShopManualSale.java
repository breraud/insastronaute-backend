package fr.insastronaute.webapi.shop.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Une vente au comptoir : quelqu'un passe, achete, repart avec l'article.
 *
 * <p>Aucun compte derriere, aucun creneau de retrait — rien de ce que la precommande
 * organise ne s'applique. D'ou une table a part plutot qu'une commande deguisee :
 * {@code shop_orders.user_id} est obligatoire, et le rendre nullable pour ce seul cas
 * obligerait chaque lecture de commande a se demander si elle en regarde une sans
 * proprietaire.</p>
 *
 * <p>La ligne est sa propre trace : elle porte qui a encaisse et quand. Elle n'entre pas
 * au registre d'administration, qui consigne ce qui change ce qu'un joueur possede — une
 * vente au comptoir ne change les possessions de personne qui soit inscrit.</p>
 */
@Entity
@Table(name = "shop_manual_sales")
public class ShopManualSale {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ShopProduct product;

    @Column(nullable = false) private int quantity;

    /**
     * Le prix du jour, fige a la vente.
     *
     * <p>Le tarif du produit peut bouger ensuite : recalculer sur le tarif courant
     * changerait retroactivement ce qui est entre en caisse.</p>
     */
    @Column(name = "unit_price_cents", nullable = false) private long unitPriceCents;

    @Column(length = 280) private String note;

    /** Qui a encaisse. Null si son compte a depuis disparu : la vente, elle, a eu lieu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private AppUser recordedBy;

    @Column(name = "sold_at", nullable = false) private Instant soldAt;

    @PrePersist
    void onCreate() {
        if (soldAt == null) {
            soldAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ShopProduct getProduct() { return product; }
    public void setProduct(ShopProduct product) { this.product = product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(long unitPriceCents) { this.unitPriceCents = unitPriceCents; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public AppUser getRecordedBy() { return recordedBy; }
    public void setRecordedBy(AppUser recordedBy) { this.recordedBy = recordedBy; }
    public Instant getSoldAt() { return soldAt; }
    public void setSoldAt(Instant soldAt) { this.soldAt = soldAt; }
}
