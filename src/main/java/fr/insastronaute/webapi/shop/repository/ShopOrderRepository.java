package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, UUID> {
    boolean existsByReservationSlotId(UUID reservationSlotId);
    @EntityGraph(attributePaths = {"user", "reservationSlot", "reservationSlot.event", "reservationSlot.series", "lines", "lines.product"})
    List<ShopOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
    @EntityGraph(attributePaths = {"user", "reservationSlot", "reservationSlot.event", "reservationSlot.series", "lines", "lines.product"})
    List<ShopOrder> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"user", "reservationSlot", "reservationSlot.event", "reservationSlot.series", "lines", "lines.product"})
    List<ShopOrder> findByStatusOrderByCreatedAtDesc(fr.insastronaute.webapi.shop.domain.ShopOrderStatus status);

    @EntityGraph(attributePaths = {"user", "lines", "lines.product"})
    java.util.Optional<ShopOrder> findWithGraphById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "reservationSlot", "reservationSlot.event", "reservationSlot.series", "lines", "lines.product"})
    @Query("select order from ShopOrder order where order.id = :id")
    java.util.Optional<ShopOrder> findWithGraphForUpdateById(@Param("id") UUID id);

    /**
     * Ce qui est deja pris sur chaque produit, par les precommandes qui tiennent encore.
     *
     * <p>Une commande annulee ne prend la place de personne : son stock est rendu du seul
     * fait de l'annulation, sans compteur a remettre d'aplomb. C'est la raison pour
     * laquelle le restant se compte plutot que de se stocker.</p>
     *
     * @return des paires (identifiant de produit, quantite commandee)
     */
    @Query("""
            select line.product.id, sum(line.quantity)
            from ShopOrderLine line
            where line.product is not null
              and line.order.status <> fr.insastronaute.webapi.shop.domain.ShopOrderStatus.CANCELLED
            group by line.product.id
            """)
    List<Object[]> sumOrderedQuantityByProduct();

    /** Le nombre de precommandes qu'un joueur tient encore, annulees exclues. */
    @Query("""
            select count(order)
            from ShopOrder order
            where order.user.id = :userId
              and order.status <> fr.insastronaute.webapi.shop.domain.ShopOrderStatus.CANCELLED
            """)
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Le detail par produit et par statut, pour le recapitulatif des ventes.
     *
     * <p>Les annulees y figurent : savoir combien de commandes se sont defaites en dit
     * autant que le total, et les taire rendrait le chiffre indiscutable la ou il
     * merite de l'etre.</p>
     *
     * @return des triplets (identifiant de produit, statut, quantite)
     */
    @Query("""
            select line.product.id, line.order.status, sum(line.quantity)
            from ShopOrderLine line
            where line.product is not null
            group by line.product.id, line.order.status
            """)
    List<Object[]> sumQuantityByProductAndStatus();
}
