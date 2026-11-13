package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopManualSale;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShopManualSaleRepository extends JpaRepository<ShopManualSale, UUID> {

    @EntityGraph(attributePaths = {"product", "recordedBy"})
    List<ShopManualSale> findAllByOrderBySoldAtDesc();

    /**
     * Ce que le comptoir a sorti du carton, par produit.
     *
     * <p>Le stock restant et le recapitulatif additionnent cette source a celle des
     * precommandes : une vente au comptoir retire un exemplaire aussi surement qu'une
     * commande retiree.</p>
     *
     * @return des paires (identifiant de produit, quantite vendue)
     */
    @Query("""
            select sale.product.id, sum(sale.quantity)
            from ShopManualSale sale
            group by sale.product.id
            """)
    List<Object[]> sumQuantityByProduct();

    /** Le chiffre encaisse par produit, au prix fige a chaque vente. */
    @Query("""
            select sale.product.id, sum(sale.quantity * sale.unitPriceCents)
            from ShopManualSale sale
            group by sale.product.id
            """)
    List<Object[]> sumRevenueByProduct();

    boolean existsByProductId(UUID productId);
}
