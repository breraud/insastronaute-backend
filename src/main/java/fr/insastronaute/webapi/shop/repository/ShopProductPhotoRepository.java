package fr.insastronaute.webapi.shop.repository;

import fr.insastronaute.webapi.shop.domain.ShopProductPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ShopProductPhotoRepository extends JpaRepository<ShopProductPhoto, UUID> {
    List<ShopProductPhoto> findByProductIdOrderByDisplayOrderAsc(UUID productId);
}
