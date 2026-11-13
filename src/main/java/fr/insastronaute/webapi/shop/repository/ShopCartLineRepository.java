package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopCartLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface ShopCartLineRepository extends JpaRepository<ShopCartLine, UUID> {
    @Modifying
    @Query("delete from ShopCartLine line where line.product.id = :productId")
    void deleteByProductId(UUID productId);
}
