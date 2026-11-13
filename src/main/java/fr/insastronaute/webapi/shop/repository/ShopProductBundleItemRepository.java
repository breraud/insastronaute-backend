package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopProductBundleItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShopProductBundleItemRepository
        extends JpaRepository<ShopProductBundleItem, ShopProductBundleItem.Key> {

    @EntityGraph(attributePaths = {"component"})
    List<ShopProductBundleItem> findByBundleId(UUID bundleId);

    /** Tout le contenu de tous les lots, pour calculer les stocks en une passe. */
    @EntityGraph(attributePaths = {"bundle", "component"})
    @Query("select item from ShopProductBundleItem item")
    List<ShopProductBundleItem> findAllWithProducts();

    void deleteByBundleId(UUID bundleId);

    /** Les lots qui retiennent ce produit : le supprimer les laisserait incomplets. */
    boolean existsByComponentId(UUID componentId);
}
