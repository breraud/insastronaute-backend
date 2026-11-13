package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopCart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ShopCartRepository extends JpaRepository<ShopCart, UUID> {
    @EntityGraph(attributePaths = {"lines", "lines.product"})
    Optional<ShopCart> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"lines", "lines.product"})
    @Query("select cart from ShopCart cart where cart.user.id = :userId")
    Optional<ShopCart> findByUserIdForUpdate(@Param("userId") UUID userId);
}
