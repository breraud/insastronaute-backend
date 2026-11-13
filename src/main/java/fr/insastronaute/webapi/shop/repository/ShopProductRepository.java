package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopProduct;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ShopProductRepository extends JpaRepository<ShopProduct, UUID> {
    @EntityGraph(attributePaths = {"article", "photos"})
    List<ShopProduct> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"article", "photos"})
    java.util.Optional<ShopProduct> findWithGraphById(UUID id);
}
