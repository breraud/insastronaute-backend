package fr.insastronaute.webapi.shop.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Un produit et sa quantite dans un lot.
 *
 * <p>Le lot est un produit comme un autre ; cette table dit seulement ce qu'il contient.
 * C'est ce qui permet au reste de la boutique — panier, commande, recapitulatif — de
 * continuer sans savoir que les lots existent.</p>
 */
@Entity
@Table(name = "shop_product_bundle_items")
@IdClass(ShopProductBundleItem.Key.class)
public class ShopProductBundleItem {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bundle_product_id", nullable = false)
    private ShopProduct bundle;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_product_id", nullable = false)
    private ShopProduct component;

    @Column(nullable = false)
    private int quantity;

    public ShopProduct getBundle() { return bundle; }
    public void setBundle(ShopProduct bundle) { this.bundle = bundle; }
    public ShopProduct getComponent() { return component; }
    public void setComponent(ShopProduct component) { this.component = component; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** La cle composee, telle que la table la porte. */
    public static class Key implements Serializable {
        private UUID bundle;
        private UUID component;

        public Key() {
        }

        public Key(UUID bundle, UUID component) {
            this.bundle = bundle;
            this.component = component;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(bundle, key.bundle) && Objects.equals(component, key.component);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bundle, component);
        }
    }
}
